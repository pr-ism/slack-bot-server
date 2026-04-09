package com.slack.bot.application.review.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.review.box.out.ReviewNotificationOutboxEnqueuer;
import com.slack.bot.application.review.dto.ReviewNotificationPayload;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxStatus;
import com.slack.bot.infrastructure.review.box.in.repository.ReviewRequestInboxRepository;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import com.slack.bot.infrastructure.review.persistence.box.in.JpaReviewRequestInboxRepository;
import com.slack.bot.infrastructure.review.persistence.box.out.JpaReviewNotificationOutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewRequestInboxLeaseIntegrationTest {

    @Autowired
    ReviewRequestInboxProcessor reviewRequestInboxProcessor;

    @Autowired
    ReviewRequestInboxRepository reviewRequestInboxRepository;

    @Autowired
    ReviewNotificationOutboxEnqueuer reviewNotificationOutboxEnqueuer;

    @Autowired
    JpaReviewRequestInboxRepository jpaReviewRequestInboxRepository;

    @Autowired
    JpaReviewNotificationOutboxRepository jpaReviewNotificationOutboxRepository;

    @Autowired
    Clock clock;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        reset(clock, reviewRequestInboxRepository, reviewNotificationOutboxEnqueuer);
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/review/project_t1.sql",
            "classpath:sql/fixtures/review/workspace_t1.sql",
            "classpath:sql/fixtures/review/channel_t1.sql",
            "classpath:sql/fixtures/review/project_member_t1_mapped.sql"
    })
    void lease를_연장한_review_inbox는_처리_중_timeout_recovery가_실행되어도_복구되지_않는다() throws Exception {
        // given
        Instant base = Instant.parse("2026-03-24T00:00:00Z");
        doReturn(
                base,
                base.plusSeconds(70),
                base.plusSeconds(70),
                base.plusSeconds(71)
        ).when(clock).instant();

        ReviewRequestInbox inbox = savePendingInbox("review-inbox-lease-success", requestJson(request(1101L)));

        doAnswer(invocation -> {
            int recoveredCount = reviewRequestInboxProcessor.recoverTimeoutProcessing(60_000L);
            assertThat(recoveredCount).isZero();
            return invocation.callRealMethod();
        }).when(reviewNotificationOutboxEnqueuer).enqueueReviewNotification(
                any(),
                any(),
                any(),
                any(),
                any()
        );

        // when
        reviewRequestInboxProcessor.processPending(1);

        // then
        ReviewRequestInbox actualInbox = jpaReviewRequestInboxRepository.findDomainById(inbox.getId()).orElseThrow();
        List<ReviewNotificationOutbox> actualOutboxes = jpaReviewNotificationOutboxRepository.findAllDomains();

        assertAll(
                () -> assertThat(actualInbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSED),
                () -> assertThat(actualInbox.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actualOutboxes).hasSize(1)
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/review/project_t1.sql",
            "classpath:sql/fixtures/review/workspace_t1.sql",
            "classpath:sql/fixtures/review/channel_t1.sql",
            "classpath:sql/fixtures/review/project_member_t1_mapped.sql"
    })
    void lease_연장에_실패한_review_inbox는_timeout_recovery로_복구된다() throws Exception {
        // given
        Instant base = Instant.parse("2026-03-24T00:00:00Z");
        doReturn(
                base,
                base.plusSeconds(10),
                base.plusSeconds(70)
        ).when(clock).instant();
        doReturn(false).when(reviewRequestInboxRepository).renewProcessingLease(any(), any(), any());

        ReviewRequestInbox inbox = savePendingInbox("review-inbox-lease-failure", requestJson(request(1102L)));

        // when
        reviewRequestInboxProcessor.processPending(1);
        int recoveredCount = reviewRequestInboxProcessor.recoverTimeoutProcessing(60_000L);

        // then
        ReviewRequestInbox actualInbox = jpaReviewRequestInboxRepository.findDomainById(inbox.getId()).orElseThrow();
        List<ReviewNotificationOutbox> actualOutboxes = jpaReviewNotificationOutboxRepository.findAllDomains();

        assertAll(
                () -> assertThat(recoveredCount).isEqualTo(1),
                () -> assertThat(actualInbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.RETRY_PENDING),
                () -> assertThat(actualInbox.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actualOutboxes).isEmpty()
        );
        verify(reviewNotificationOutboxEnqueuer, never()).enqueueReviewNotification(
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    private ReviewRequestInbox savePendingInbox(String idempotencyKey, String requestJson) {
        ReviewRequestInbox inbox = ReviewRequestInbox.pending(
                idempotencyKey,
                "test-api-key",
                1101L,
                requestJson,
                Instant.parse("2026-03-23T23:59:00Z")
        );
        return reviewRequestInboxRepository.save(inbox);
    }

    private ReviewNotificationPayload request(Long githubPullRequestId) {
        return new ReviewNotificationPayload(
                "my-repo",
                githubPullRequestId,
                Math.toIntExact(githubPullRequestId),
                "lease-check",
                "https://github.com/pr/" + githubPullRequestId,
                "author-gh",
                List.of("reviewer-gh-1"),
                List.of("reviewer-gh-1")
        );
    }

    private String requestJson(ReviewNotificationPayload request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }
}
