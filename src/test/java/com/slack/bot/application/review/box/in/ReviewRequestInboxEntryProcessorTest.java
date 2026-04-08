package com.slack.bot.application.review.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.review.box.in.exception.ReviewRequestInboxProcessingLeaseLostException;
import com.slack.bot.application.review.dto.ReviewNotificationPayload;
import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxStatus;
import com.slack.bot.infrastructure.review.box.in.repository.ReviewRequestInboxRepository;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import com.slack.bot.infrastructure.review.persistence.box.in.JpaReviewRequestInboxRepository;
import com.slack.bot.infrastructure.review.persistence.box.out.JpaReviewNotificationOutboxRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewRequestInboxEntryProcessorTest {

    private static final Instant CLAIMED_PROCESSING_STARTED_AT = Instant.parse("2026-02-15T00:00:00Z");

    @Autowired
    ReviewRequestInboxEntryProcessor reviewRequestInboxEntryProcessor;

    @Autowired
    ReviewRequestInboxRepository reviewRequestInboxRepository;

    @Autowired
    JpaReviewRequestInboxRepository jpaReviewRequestInboxRepository;

    @Autowired
    JpaReviewNotificationOutboxRepository jpaReviewNotificationOutboxRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/review/project_t1.sql",
            "classpath:sql/fixtures/review/workspace_t1.sql",
            "classpath:sql/fixtures/review/channel_t1.sql",
            "classpath:sql/fixtures/review/project_member_t1_mapped.sql"
    })
    void outbox_enqueue_성공후_inbox_상태_저장에_실패하면_전체_트랜잭션이_롤백된다() throws Exception {
        // given
        ReviewRequestInbox inbox = saveProcessingInbox(
                "review-entry-rollback",
                requestJson(request(1101L, "rollback-check"))
        );

        doReturn(true)
                .when(reviewRequestInboxRepository)
                .renewProcessingLease(inbox.getId(), CLAIMED_PROCESSING_STARTED_AT, CLAIMED_PROCESSING_STARTED_AT);
        doReturn(false)
                .when(reviewRequestInboxRepository)
                .saveIfProcessingLeaseMatched(
                        argThat(savedInbox -> savedInbox != null && inbox.getId().equals(savedInbox.getId())),
                        any(),
                        any()
                );

        // when & then
        assertThatThrownBy(() -> reviewRequestInboxEntryProcessor.processClaimedInbox(
                inbox.getId(),
                CLAIMED_PROCESSING_STARTED_AT
        ))
                .isInstanceOf(ReviewRequestInboxProcessingLeaseLostException.class)
                .hasMessageContaining("processing lease");

        ReviewRequestInbox actualInbox = jpaReviewRequestInboxRepository.findDomainById(inbox.getId()).orElseThrow();
        List<ReviewNotificationOutbox> actualOutboxes = jpaReviewNotificationOutboxRepository.findAll();

        assertAll(
                () -> assertThat(actualInbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSING),
                () -> assertThat(actualInbox.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actualOutboxes).isEmpty()
        );
    }

    private ReviewRequestInbox saveProcessingInbox(String idempotencyKey, String requestJson) {
        ReviewRequestInbox inbox = ReviewRequestInbox.pending(
                idempotencyKey,
                "test-api-key",
                1101L,
                requestJson,
                Instant.parse("2026-02-15T00:00:00Z")
        );
        ReflectionTestUtils.setField(inbox, "status", ReviewRequestInboxStatus.PROCESSING);
        ReflectionTestUtils.setField(inbox, "processingStartedAt", CLAIMED_PROCESSING_STARTED_AT);
        ReflectionTestUtils.setField(inbox, "processedAt", FailureSnapshotDefaults.NO_PROCESSED_AT);
        ReflectionTestUtils.setField(inbox, "processingAttempt", 1);
        return reviewRequestInboxRepository.save(inbox);
    }

    private ReviewNotificationPayload request(Long githubPullRequestId, String pullRequestTitle) {
        return new ReviewNotificationPayload(
                "my-repo",
                githubPullRequestId,
                Math.toIntExact(githubPullRequestId),
                pullRequestTitle,
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
