package com.slack.bot.infrastructure.review.persistence.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxFailureType;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxHistory;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxStatus;
import com.slack.bot.infrastructure.review.box.in.repository.ReviewRequestInboxRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

    @Autowired
    JpaReviewRequestInboxHistoryRepository jpaReviewRequestInboxHistoryRepository;

    @Test
    void 신규_upsertPending은_failure_snapshot을_sentinel값으로_저장한다() {
        // given
        Instant availableAt = Instant.parse("2026-02-24T00:00:00Z");

        // when
        reviewRequestInboxRepository.upsertPending(
                "api-key:41",
                "api-key",
                41L,
                "{\"pullRequestTitle\":\"new\"}",
                availableAt
        );

        // then
        ReviewRequestInbox actual = findByIdempotencyKey("api-key:41");
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewRequestInboxStatus.PENDING),
                () -> assertThat(actual.getProcessingAttempt()).isZero(),
                () -> assertThat(actual.getProcessingStartedAt()).isEqualTo(
                        FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT
                ),
                () -> assertThat(actual.getProcessedAt()).isEqualTo(FailureSnapshotDefaults.NO_PROCESSED_AT),
                () -> assertThat(actual.getFailedAt()).isEqualTo(FailureSnapshotDefaults.NO_FAILURE_AT),
                () -> assertThat(actual.getFailureReason()).isEqualTo(FailureSnapshotDefaults.NO_FAILURE_REASON),
                () -> assertThat(actual.getFailureType()).isEqualTo(ReviewRequestInboxFailureType.NONE),
                () -> assertThat(actual.getAvailableAt()).isEqualTo(availableAt)
        );
    }

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
                () -> assertThat(actual.getProcessingStartedAt()).isEqualTo(
                        FailureSnapshotDefaults.NO_PROCESSING_STARTED_AT
                ),
                () -> assertThat(actual.getProcessedAt()).isEqualTo(FailureSnapshotDefaults.NO_PROCESSED_AT),
                () -> assertThat(actual.getFailedAt()).isEqualTo(FailureSnapshotDefaults.NO_FAILURE_AT),
                () -> assertThat(actual.getFailureReason()).isEqualTo(FailureSnapshotDefaults.NO_FAILURE_REASON),
                () -> assertThat(actual.getFailureType()).isEqualTo(ReviewRequestInboxFailureType.NONE),
                () -> assertThat(actual.getRequestJson()).isEqualTo("{\"pullRequestTitle\":\"new\"}"),
                () -> assertThat(actual.getAvailableAt()).isEqualTo(newAvailableAt)
        );
    }

    @Test
    void PROCESSING_타임아웃을_RETRY_PENDING으로_복구하면_history에는_PROCESSING_TIMEOUT을_남긴다() {
        // given
        ReviewRequestInbox inbox = ReviewRequestInbox.pending(
                "api-key:44",
                "api-key",
                44L,
                "{\"pullRequestTitle\":\"old\"}",
                Instant.parse("2026-02-24T00:00:00Z")
        );
        setProcessingState(inbox, Instant.parse("2026-02-24T00:01:00Z"), 1);
        ReviewRequestInbox saved = jpaReviewRequestInboxRepository.save(inbox);
        Instant failedAt = Instant.parse("2026-02-24T00:05:00Z");
        LocalDateTime expectedUpdatedAt = LocalDateTime.ofInstant(failedAt, ZoneOffset.UTC);

        // when
        int recoveredCount = reviewRequestInboxRepository.recoverTimeoutProcessing(
                Instant.parse("2026-02-24T00:02:00Z"),
                failedAt,
                "timeout",
                3
        );

        // then
        ReviewRequestInbox actual = jpaReviewRequestInboxRepository.findById(saved.getId()).orElseThrow();
        ReviewRequestInboxHistory history = findLatestHistory(saved.getId());
        assertAll(
                () -> assertThat(recoveredCount).isEqualTo(1),
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewRequestInboxStatus.RETRY_PENDING),
                () -> assertThat(actual.getFailureType()).isEqualTo(ReviewRequestInboxFailureType.NONE),
                () -> assertThat(actual.getUpdatedAt()).isEqualTo(expectedUpdatedAt),
                () -> assertThat(history.getStatus()).isEqualTo(ReviewRequestInboxStatus.RETRY_PENDING),
                () -> assertThat(history.getFailureType()).isEqualTo(ReviewRequestInboxFailureType.PROCESSING_TIMEOUT),
                () -> assertThat(history.getFailureReason()).isEqualTo("timeout"),
                () -> assertThat(history.getCompletedAt()).isEqualTo(failedAt)
        );
    }

    private void setProcessingState(ReviewRequestInbox inbox, Instant processingStartedAt, int processingAttempt) {
        ReflectionTestUtils.setField(inbox, "status", ReviewRequestInboxStatus.PROCESSING);
        ReflectionTestUtils.setField(inbox, "processingStartedAt", processingStartedAt);
        ReflectionTestUtils.setField(inbox, "processedAt", FailureSnapshotDefaults.NO_PROCESSED_AT);
        ReflectionTestUtils.setField(inbox, "processingAttempt", processingAttempt);
        ReflectionTestUtils.setField(inbox, "failedAt", FailureSnapshotDefaults.NO_FAILURE_AT);
        ReflectionTestUtils.setField(inbox, "failureReason", FailureSnapshotDefaults.NO_FAILURE_REASON);
        ReflectionTestUtils.setField(inbox, "failureType", ReviewRequestInboxFailureType.NONE);
    }

    private ReviewRequestInbox findByIdempotencyKey(String idempotencyKey) {
        for (ReviewRequestInbox inbox : jpaReviewRequestInboxRepository.findAll()) {
            if (idempotencyKey.equals(inbox.getIdempotencyKey())) {
                return inbox;
            }
        }

        throw new IllegalArgumentException("해당 idempotencyKey의 inbox가 없습니다.");
    }

    private ReviewRequestInboxHistory findLatestHistory(Long inboxId) {
        ReviewRequestInboxHistory latestHistory = null;

        for (ReviewRequestInboxHistory history : jpaReviewRequestInboxHistoryRepository.findAll()) {
            if (!inboxId.equals(history.getInboxId())) {
                continue;
            }
            if (latestHistory == null || history.getCreatedAt().isAfter(latestHistory.getCreatedAt())) {
                latestHistory = history;
            }
        }

        if (latestHistory == null) {
            throw new IllegalArgumentException("해당 inboxId의 history가 없습니다.");
        }

        return latestHistory;
    }
}
