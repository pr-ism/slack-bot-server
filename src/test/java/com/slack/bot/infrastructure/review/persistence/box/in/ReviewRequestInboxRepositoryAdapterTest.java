package com.slack.bot.infrastructure.review.persistence.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxStatus;
import com.slack.bot.infrastructure.review.box.in.repository.ReviewRequestInboxRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewRequestInboxRepositoryAdapterTest {

    @Autowired
    ReviewRequestInboxRepository reviewRequestInboxRepository;

    @Autowired
    JpaReviewRequestInboxRepository jpaReviewRequestInboxRepository;

    @Test
    void PROCESSING_상태면_upsertPending이_행을_덮어쓰지_않는다() {
        // given
        Instant oldAvailableAt = Instant.parse("2026-02-24T00:00:00Z");
        Instant processingStartedAt = Instant.parse("2026-02-24T00:01:00Z");
        ReviewRequestInbox inbox = ReviewRequestInbox.pending(
                "api-key:42",
                "api-key",
                42L,
                "{\"pullRequestTitle\":\"old\"}",
                oldAvailableAt
        );
        setProcessingState(inbox, processingStartedAt, 1);
        ReviewRequestInbox saved = jpaReviewRequestInboxRepository.save(inbox);

        // when
        reviewRequestInboxRepository.upsertPending(
                "api-key:42",
                "api-key-updated",
                42L,
                "{\"pullRequestTitle\":\"new\"}",
                Instant.parse("2026-02-24T00:02:00Z")
        );

        // then
        ReviewRequestInbox actual = jpaReviewRequestInboxRepository.findById(saved.getId()).orElseThrow();
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSING),
                () -> assertThat(actual.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actual.getProcessingStartedAt()).isEqualTo(processingStartedAt),
                () -> assertThat(actual.getApiKey()).isEqualTo("api-key"),
                () -> assertThat(actual.getRequestJson()).isEqualTo("{\"pullRequestTitle\":\"old\"}"),
                () -> assertThat(actual.getAvailableAt()).isEqualTo(oldAvailableAt)
        );
    }

    @Test
    void RETRY_PENDING_상태면_upsertPending이_최신_요청으로_갱신하고_PENDING으로_돌린다() {
        // given
        ReviewRequestInbox inbox = ReviewRequestInbox.pending(
                "api-key:43",
                "api-key",
                43L,
                "{\"pullRequestTitle\":\"old\"}",
                Instant.parse("2026-02-24T00:00:00Z")
        );
        setProcessingState(inbox, Instant.parse("2026-02-24T00:01:00Z"), 1);
        inbox.markRetryPending(Instant.parse("2026-02-24T00:02:00Z"), "temporary failure");
        ReviewRequestInbox saved = jpaReviewRequestInboxRepository.save(inbox);

        // when
        Instant newAvailableAt = Instant.parse("2026-02-24T00:03:00Z");
        reviewRequestInboxRepository.upsertPending(
                "api-key:43",
                "api-key",
                43L,
                "{\"pullRequestTitle\":\"new\"}",
                newAvailableAt
        );

        // then
        ReviewRequestInbox actual = jpaReviewRequestInboxRepository.findById(saved.getId()).orElseThrow();
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewRequestInboxStatus.PENDING),
                () -> assertThat(actual.getProcessingAttempt()).isZero(),
                () -> assertThat(actual.getProcessingStartedAt()).isNull(),
                () -> assertThat(actual.getFailedAt()).isNull(),
                () -> assertThat(actual.getFailureReason()).isNull(),
                () -> assertThat(actual.getFailureType()).isNull(),
                () -> assertThat(actual.getRequestJson()).isEqualTo("{\"pullRequestTitle\":\"new\"}"),
                () -> assertThat(actual.getAvailableAt()).isEqualTo(newAvailableAt)
        );
    }

    private void setProcessingState(ReviewRequestInbox inbox, Instant processingStartedAt, int processingAttempt) {
        ReflectionTestUtils.setField(inbox, "status", ReviewRequestInboxStatus.PROCESSING);
        ReflectionTestUtils.setField(inbox, "processingStartedAt", processingStartedAt);
        ReflectionTestUtils.setField(inbox, "processingAttempt", processingAttempt);
        ReflectionTestUtils.setField(inbox, "failedAt", null);
        ReflectionTestUtils.setField(inbox, "failureReason", null);
        ReflectionTestUtils.setField(inbox, "failureType", null);
    }
}
