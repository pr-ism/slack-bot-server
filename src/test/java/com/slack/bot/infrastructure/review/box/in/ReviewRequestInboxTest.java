package com.slack.bot.infrastructure.review.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.infrastructure.interaction.box.SlackInteractivityFailureType;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewRequestInboxTest {

    @Test
    void pending으로_생성하면_기본값은_PENDING이고_시도횟수는_0이다() {
        // when
        ReviewRequestInbox inbox = pendingInbox();

        // then
        assertAll(
                () -> assertThat(inbox.getCoalescingKey()).isEqualTo("api-key:42"),
                () -> assertThat(inbox.getApiKey()).isEqualTo("api-key"),
                () -> assertThat(inbox.getGithubPullRequestId()).isEqualTo(42L),
                () -> assertThat(inbox.getRequestJson()).isEqualTo("{\"githubPullRequestId\":42}"),
                () -> assertThat(inbox.getAvailableAt()).isEqualTo(Instant.parse("2026-02-24T00:00:00Z")),
                () -> assertThat(inbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.PENDING),
                () -> assertThat(inbox.getProcessingAttempt()).isZero()
        );
    }

    @Test
    void pending은_coalescingKey가_null이면_예외를_던진다() {
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
                .hasMessage("coalescingKey는 비어 있을 수 없습니다.");
    }

    @Test
    void pending은_coalescingKey가_공백이면_예외를_던진다() {
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
                .hasMessage("coalescingKey는 비어 있을 수 없습니다.");
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
    void markProcessing을_호출하면_PROCESSING_상태로_변경되고_시도횟수가_증가한다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();

        // when
        Instant processingStartedAt = Instant.parse("2026-02-24T00:10:00Z");
        inbox.markProcessing(processingStartedAt);

        // then
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSING),
                () -> assertThat(inbox.getProcessingStartedAt()).isEqualTo(processingStartedAt),
                () -> assertThat(inbox.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(inbox.getFailedAt()).isNull(),
                () -> assertThat(inbox.getFailureReason()).isNull(),
                () -> assertThat(inbox.getFailureType()).isNull()
        );
    }

    @Test
    void markProcessing은_RETRY_PENDING에서_재진입하면_실패정보를_초기화한다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        inbox.markProcessing(Instant.parse("2026-02-24T00:10:00Z"));
        inbox.markRetryPending(Instant.parse("2026-02-24T00:11:00Z"), "retry");

        // when
        inbox.markProcessing(Instant.parse("2026-02-24T00:12:00Z"));

        // then
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSING),
                () -> assertThat(inbox.getProcessingAttempt()).isEqualTo(2),
                () -> assertThat(inbox.getFailedAt()).isNull(),
                () -> assertThat(inbox.getFailureReason()).isNull(),
                () -> assertThat(inbox.getFailureType()).isNull()
        );
    }

    @Test
    void markProcessing은_processingStartedAt이_null이면_예외를_던진다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();

        // when & then
        assertThatThrownBy(() -> inbox.markProcessing(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("processingStartedAt은 비어 있을 수 없습니다.");
    }

    @Test
    void markProcessing은_PENDING이나_RETRY_PENDING이_아니면_예외를_던진다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        inbox.markProcessing(Instant.parse("2026-02-24T00:10:00Z"));
        inbox.markProcessed(Instant.parse("2026-02-24T00:11:00Z"));

        // when & then
        assertThatThrownBy(() -> inbox.markProcessing(Instant.parse("2026-02-24T00:12:00Z")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PROCESSING 전이는 PENDING 또는 RETRY_PENDING 상태에서만 가능합니다.");
    }

    @Test
    void markProcessed를_호출하면_PROCESSED_상태와_처리시각이_저장된다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        inbox.markProcessing(Instant.parse("2026-02-24T00:10:00Z"));

        // when
        Instant processedAt = Instant.parse("2026-02-24T00:13:00Z");
        inbox.markProcessed(processedAt);

        // then
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSED),
                () -> assertThat(inbox.getProcessedAt()).isEqualTo(processedAt),
                () -> assertThat(inbox.getProcessingStartedAt()).isNull(),
                () -> assertThat(inbox.getFailedAt()).isNull(),
                () -> assertThat(inbox.getFailureReason()).isNull(),
                () -> assertThat(inbox.getFailureType()).isNull()
        );
    }

    @Test
    void markProcessed는_processedAt이_null이면_예외를_던진다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        inbox.markProcessing(Instant.parse("2026-02-24T00:10:00Z"));

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
        inbox.markProcessing(Instant.parse("2026-02-24T00:10:00Z"));

        // when
        Instant failedAt = Instant.parse("2026-02-24T00:14:00Z");
        inbox.markRetryPending(failedAt, "retry");

        // then
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.RETRY_PENDING),
                () -> assertThat(inbox.getProcessingStartedAt()).isNull(),
                () -> assertThat(inbox.getFailedAt()).isEqualTo(failedAt),
                () -> assertThat(inbox.getFailureReason()).isEqualTo("retry"),
                () -> assertThat(inbox.getFailureType()).isNull()
        );
    }

    @Test
    void markRetryPending은_failedAt이_null이면_예외를_던진다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        inbox.markProcessing(Instant.parse("2026-02-24T00:10:00Z"));

        // when & then
        assertThatThrownBy(() -> inbox.markRetryPending(null, "retry"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failedAt은 비어 있을 수 없습니다.");
    }

    @Test
    void markRetryPending은_failureReason이_null이면_예외를_던진다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        inbox.markProcessing(Instant.parse("2026-02-24T00:10:00Z"));

        // when & then
        assertThatThrownBy(() -> inbox.markRetryPending(Instant.parse("2026-02-24T00:14:00Z"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureReason은 비어 있을 수 없습니다.");
    }

    @Test
    void markRetryPending은_failureReason이_공백이면_예외를_던진다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        inbox.markProcessing(Instant.parse("2026-02-24T00:10:00Z"));

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
        inbox.markProcessing(Instant.parse("2026-02-24T00:10:00Z"));

        // when
        Instant failedAt = Instant.parse("2026-02-24T00:15:00Z");
        inbox.markFailed(failedAt, "failure", SlackInteractivityFailureType.BUSINESS_INVARIANT);

        // then
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.FAILED),
                () -> assertThat(inbox.getProcessingStartedAt()).isNull(),
                () -> assertThat(inbox.getFailedAt()).isEqualTo(failedAt),
                () -> assertThat(inbox.getFailureReason()).isEqualTo("failure"),
                () -> assertThat(inbox.getFailureType()).isEqualTo(SlackInteractivityFailureType.BUSINESS_INVARIANT)
        );
    }

    @Test
    void markFailed는_failedAt이_null이면_예외를_던진다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        inbox.markProcessing(Instant.parse("2026-02-24T00:10:00Z"));

        // when & then
        assertThatThrownBy(() -> inbox.markFailed(null, "failure", SlackInteractivityFailureType.BUSINESS_INVARIANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failedAt은 비어 있을 수 없습니다.");
    }

    @Test
    void markFailed는_failureReason이_null이면_예외를_던진다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        inbox.markProcessing(Instant.parse("2026-02-24T00:10:00Z"));

        // when & then
        assertThatThrownBy(
                () -> inbox.markFailed(
                        Instant.parse("2026-02-24T00:15:00Z"),
                        null,
                        SlackInteractivityFailureType.BUSINESS_INVARIANT
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureReason은 비어 있을 수 없습니다.");
    }

    @Test
    void markFailed는_failureReason이_공백이면_예외를_던진다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        inbox.markProcessing(Instant.parse("2026-02-24T00:10:00Z"));

        // when & then
        assertThatThrownBy(
                () -> inbox.markFailed(
                        Instant.parse("2026-02-24T00:15:00Z"),
                        " ",
                        SlackInteractivityFailureType.BUSINESS_INVARIANT
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureReason은 비어 있을 수 없습니다.");
    }

    @Test
    void markFailed는_failureType이_null이면_예외를_던진다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        inbox.markProcessing(Instant.parse("2026-02-24T00:10:00Z"));

        // when & then
        assertThatThrownBy(() -> inbox.markFailed(Instant.parse("2026-02-24T00:15:00Z"), "failure", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("failureType은 비어 있을 수 없습니다.");
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
                        SlackInteractivityFailureType.BUSINESS_INVARIANT
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
}

