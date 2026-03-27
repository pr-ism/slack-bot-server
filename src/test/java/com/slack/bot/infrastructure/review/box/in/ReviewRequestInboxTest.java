package com.slack.bot.infrastructure.review.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewRequestInboxTest {

    @Test
    void pending으로_생성하면_기본값은_PENDING이고_시도횟수는_0이다() {
        // when
        ReviewRequestInbox inbox = pendingInbox();

        // then
        assertAll(
                () -> assertThat(inbox.getIdempotencyKey()).isEqualTo("api-key:42"),
                () -> assertThat(inbox.getApiKey()).isEqualTo("api-key"),
                () -> assertThat(inbox.getGithubPullRequestId()).isEqualTo(42L),
                () -> assertThat(inbox.getRequestJson()).isEqualTo("{\"githubPullRequestId\":42}"),
                () -> assertThat(inbox.getAvailableAt()).isEqualTo(Instant.parse("2026-02-24T00:00:00Z")),
                () -> assertThat(inbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.PENDING),
                () -> assertThat(inbox.getProcessingAttempt()).isZero(),
                () -> assertThat(inbox.getFailureType()).isEqualTo(ReviewRequestInboxFailureType.NONE)
        );
    }

    @Test
    void pending은_idempotencyKey가_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(
                () -> ReviewRequestInbox.pending(
                        null,
                        "api-key",
                        42L,
                        "{\"githubPullRequestId\":42}",
                        Instant.parse("2026-02-24T00:00:00Z")
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("idempotencyKey는 비어 있을 수 없습니다.");
    }

    @Test
    void pending은_idempotencyKey가_공백이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(
                () -> ReviewRequestInbox.pending(
                        " ",
                        "api-key",
                        42L,
                        "{\"githubPullRequestId\":42}",
                        Instant.parse("2026-02-24T00:00:00Z")
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("idempotencyKey는 비어 있을 수 없습니다.");
    }

    @Test
    void pending은_apiKey가_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(
                () -> ReviewRequestInbox.pending(
                        "api-key:42",
                        null,
                        42L,
                        "{\"githubPullRequestId\":42}",
                        Instant.parse("2026-02-24T00:00:00Z")
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("apiKey는 비어 있을 수 없습니다.");
    }

    @Test
    void pending은_apiKey가_공백이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(
                () -> ReviewRequestInbox.pending(
                        "api-key:42",
                        " ",
                        42L,
                        "{\"githubPullRequestId\":42}",
                        Instant.parse("2026-02-24T00:00:00Z")
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("apiKey는 비어 있을 수 없습니다.");
    }

    @Test
    void pending은_githubPullRequestId가_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(
                () -> ReviewRequestInbox.pending(
                        "api-key:42",
                        "api-key",
                        null,
                        "{\"githubPullRequestId\":42}",
                        Instant.parse("2026-02-24T00:00:00Z")
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("githubPullRequestId는 비어 있을 수 없습니다.");
    }

    @Test
    void pending은_githubPullRequestId가_0이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(
                () -> ReviewRequestInbox.pending(
                        "api-key:42",
                        "api-key",
                        0L,
                        "{\"githubPullRequestId\":42}",
                        Instant.parse("2026-02-24T00:00:00Z")
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("githubPullRequestId는 비어 있을 수 없습니다.");
    }

    @Test
    void pending은_requestJson이_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(
                () -> ReviewRequestInbox.pending(
                        "api-key:42",
                        "api-key",
                        42L,
                        null,
                        Instant.parse("2026-02-24T00:00:00Z")
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("requestJson은 비어 있을 수 없습니다.");
    }

    @Test
    void pending은_requestJson이_공백이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(
                () -> ReviewRequestInbox.pending(
                        "api-key:42",
                        "api-key",
                        42L,
                        " ",
                        Instant.parse("2026-02-24T00:00:00Z")
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("requestJson은 비어 있을 수 없습니다.");
    }

    @Test
    void pending은_availableAt이_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(
                () -> ReviewRequestInbox.pending(
                        "api-key:42",
                        "api-key",
                        42L,
                        "{\"githubPullRequestId\":42}",
                        null
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("availableAt은 비어 있을 수 없습니다.");
    }

    @Test
    void markProcessed를_호출하면_PROCESSED_상태와_처리시각이_저장된다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        setProcessingState(inbox, Instant.parse("2026-02-24T00:10:00Z"), 1);

        // when
        Instant processedAt = Instant.parse("2026-02-24T00:13:00Z");
        ReviewRequestInboxHistory history = inbox.markProcessed(processedAt);

        // then
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSED),
                () -> assertThat(inbox.getProcessedAt()).isEqualTo(processedAt),
                () -> assertThat(inbox.getProcessingStartedAt()).isNull(),
                () -> assertThat(inbox.getFailedAt()).isEqualTo(FailureSnapshotDefaults.NO_FAILURE_AT),
                () -> assertThat(inbox.getFailureReason()).isEqualTo(FailureSnapshotDefaults.NO_FAILURE_REASON),
                () -> assertThat(inbox.getFailureType()).isEqualTo(ReviewRequestInboxFailureType.NONE),
                () -> assertThat(history).isNotNull(),
                () -> assertThat(history.getInboxId()).isNull(),
                () -> assertThat(history.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSED),
                () -> assertThat(history.getFailureType()).isEqualTo(ReviewRequestInboxFailureType.NONE)
        );
    }

    @Test
    void markProcessed는_processedAt이_null이면_예외를_던진다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        setProcessingState(inbox, Instant.parse("2026-02-24T00:10:00Z"), 1);

        // when & then
        assertThatThrownBy(() -> inbox.markProcessed(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("processedAt은 비어 있을 수 없습니다.");
    }

    @Test
    void markProcessed는_PROCESSING_상태가_아니면_예외를_던진다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();

        // when & then
        assertThatThrownBy(() -> inbox.markProcessed(Instant.parse("2026-02-24T00:13:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROCESSED 전이는 PROCESSING 상태에서만 가능합니다.");
    }

    @Test
    void markRetryPending을_호출하면_RETRY_PENDING_상태와_실패정보가_저장된다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        setProcessingState(inbox, Instant.parse("2026-02-24T00:10:00Z"), 1);

        // when
        Instant failedAt = Instant.parse("2026-02-24T00:14:00Z");
        ReviewRequestInboxHistory history = inbox.markRetryPending(failedAt, "retry");

        // then
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.RETRY_PENDING),
                () -> assertThat(inbox.getProcessingStartedAt()).isNull(),
                () -> assertThat(inbox.getFailedAt()).isEqualTo(failedAt),
                () -> assertThat(inbox.getFailureReason()).isEqualTo("retry"),
                () -> assertThat(inbox.getFailureType()).isEqualTo(ReviewRequestInboxFailureType.NONE),
                () -> assertThat(history).isNotNull(),
                () -> assertThat(history.getInboxId()).isNull(),
                () -> assertThat(history.getStatus()).isEqualTo(ReviewRequestInboxStatus.RETRY_PENDING),
                () -> assertThat(history.getFailureType()).isEqualTo(ReviewRequestInboxFailureType.NONE)
        );
    }

    @Test
    void markRetryPending은_failedAt이_null이면_예외를_던진다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        setProcessingState(inbox, Instant.parse("2026-02-24T00:10:00Z"), 1);

        // when & then
        assertThatThrownBy(() -> inbox.markRetryPending(null, "retry"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failedAt은 비어 있을 수 없습니다.");
    }

    @Test
    void markRetryPending은_failureReason이_null이면_예외를_던진다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        setProcessingState(inbox, Instant.parse("2026-02-24T00:10:00Z"), 1);

        // when & then
        assertThatThrownBy(() -> inbox.markRetryPending(Instant.parse("2026-02-24T00:14:00Z"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureReason은 비어 있을 수 없습니다.");
    }

    @Test
    void markRetryPending은_failureReason이_공백이면_예외를_던진다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        setProcessingState(inbox, Instant.parse("2026-02-24T00:10:00Z"), 1);

        // when & then
        assertThatThrownBy(() -> inbox.markRetryPending(Instant.parse("2026-02-24T00:14:00Z"), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureReason은 비어 있을 수 없습니다.");
    }

    @Test
    void markRetryPending은_PROCESSING_상태가_아니면_예외를_던진다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();

        // when & then
        assertThatThrownBy(() -> inbox.markRetryPending(Instant.parse("2026-02-24T00:14:00Z"), "retry"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RETRY_PENDING 전이는 PROCESSING 상태에서만 가능합니다.");
    }

    @Test
    void markFailed를_호출하면_FAILED_상태와_실패정보가_저장된다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        setProcessingState(inbox, Instant.parse("2026-02-24T00:10:00Z"), 1);

        // when
        Instant failedAt = Instant.parse("2026-02-24T00:15:00Z");
        ReviewRequestInboxHistory history = inbox.markFailed(
                failedAt,
                "failure",
                ReviewRequestInboxFailureType.NON_RETRYABLE
        );

        // then
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.FAILED),
                () -> assertThat(inbox.getProcessingStartedAt()).isNull(),
                () -> assertThat(inbox.getFailedAt()).isEqualTo(failedAt),
                () -> assertThat(inbox.getFailureReason()).isEqualTo("failure"),
                () -> assertThat(inbox.getFailureType()).isEqualTo(ReviewRequestInboxFailureType.NON_RETRYABLE),
                () -> assertThat(history).isNotNull(),
                () -> assertThat(history.getInboxId()).isNull(),
                () -> assertThat(history.getStatus()).isEqualTo(ReviewRequestInboxStatus.FAILED)
        );
    }

    @Test
    void markFailed는_failedAt이_null이면_예외를_던진다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        setProcessingState(inbox, Instant.parse("2026-02-24T00:10:00Z"), 1);

        // when & then
        assertThatThrownBy(() -> inbox.markFailed(null, "failure", ReviewRequestInboxFailureType.NON_RETRYABLE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failedAt은 비어 있을 수 없습니다.");
    }

    @Test
    void markFailed는_failureReason이_null이면_예외를_던진다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        setProcessingState(inbox, Instant.parse("2026-02-24T00:10:00Z"), 1);

        // when & then
        assertThatThrownBy(
                () -> inbox.markFailed(
                        Instant.parse("2026-02-24T00:15:00Z"),
                        null,
                        ReviewRequestInboxFailureType.NON_RETRYABLE
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureReason은 비어 있을 수 없습니다.");
    }

    @Test
    void markFailed는_failureReason이_공백이면_예외를_던진다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        setProcessingState(inbox, Instant.parse("2026-02-24T00:10:00Z"), 1);

        // when & then
        assertThatThrownBy(
                () -> inbox.markFailed(
                        Instant.parse("2026-02-24T00:15:00Z"),
                        " ",
                        ReviewRequestInboxFailureType.NON_RETRYABLE
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureReason은 비어 있을 수 없습니다.");
    }

    @Test
    void markFailed는_failureType이_NONE이면_예외를_던진다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        setProcessingState(inbox, Instant.parse("2026-02-24T00:10:00Z"), 1);

        // when & then
        assertThatThrownBy(
                () -> inbox.markFailed(
                        Instant.parse("2026-02-24T00:15:00Z"),
                        "failure",
                        ReviewRequestInboxFailureType.NONE
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureType은 NONE일 수 없습니다.");
    }

    @Test
    void markFailed는_PROCESSING_상태가_아니면_예외를_던진다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();

        // when & then
        assertThatThrownBy(
                () -> inbox.markFailed(
                        Instant.parse("2026-02-24T00:15:00Z"),
                        "failure",
                        ReviewRequestInboxFailureType.NON_RETRYABLE
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FAILED 전이는 PROCESSING 상태에서만 가능합니다.");
    }

    private ReviewRequestInbox pendingInbox() {
        return ReviewRequestInbox.pending(
                "api-key:42",
                "api-key",
                42L,
                "{\"githubPullRequestId\":42}",
                Instant.parse("2026-02-24T00:00:00Z")
        );
    }

    private void setProcessingState(ReviewRequestInbox inbox, Instant processingStartedAt, int processingAttempt) {
        ReflectionTestUtils.setField(inbox, "status", ReviewRequestInboxStatus.PROCESSING);
        ReflectionTestUtils.setField(inbox, "processingStartedAt", processingStartedAt);
        ReflectionTestUtils.setField(inbox, "processingAttempt", processingAttempt);
        ReflectionTestUtils.setField(inbox, "failedAt", FailureSnapshotDefaults.NO_FAILURE_AT);
        ReflectionTestUtils.setField(inbox, "failureReason", FailureSnapshotDefaults.NO_FAILURE_REASON);
        ReflectionTestUtils.setField(inbox, "failureType", ReviewRequestInboxFailureType.NONE);
    }
}
