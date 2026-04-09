package com.slack.bot.infrastructure.review.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewRequestInboxTest {

    @Test
    void pending으로_생성하면_기본값은_PENDING이고_상태상세값은_비어있다() {
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
                () -> assertThat(inbox.hasClaimedProcessingLease()).isFalse(),
                () -> assertThat(inbox.getProcessedTime().isPresent()).isFalse(),
                () -> assertThat(inbox.getFailedTime().isPresent()).isFalse(),
                () -> assertThat(inbox.getFailure().isPresent()).isFalse()
        );
    }

    @Test
    void rehydrate는_PROCESSING과_idle_processingLease_조합을_허용하지_않는다() {
        // when & then
        assertThatThrownBy(() -> ReviewRequestInbox.rehydrate(
                1L,
                "api-key:42",
                "api-key",
                42L,
                "{}",
                Instant.parse("2026-02-24T00:00:00Z"),
                ReviewRequestInboxStatus.PROCESSING,
                1,
                BoxProcessingLease.idle(),
                BoxEventTime.absent(),
                BoxEventTime.absent(),
                BoxFailureSnapshot.absent()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PROCESSING 상태는 processingLease를 보유해야 합니다.");
    }

    @Test
    void rehydrate는_RETRY_PENDING과_absent_failure_조합을_허용하지_않는다() {
        // when & then
        assertThatThrownBy(() -> ReviewRequestInbox.rehydrate(
                1L,
                "api-key:42",
                "api-key",
                42L,
                "{}",
                Instant.parse("2026-02-24T00:00:00Z"),
                ReviewRequestInboxStatus.RETRY_PENDING,
                1,
                BoxProcessingLease.idle(),
                BoxEventTime.absent(),
                BoxEventTime.present(Instant.parse("2026-02-24T00:01:00Z")),
                BoxFailureSnapshot.absent()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("RETRY_PENDING 상태는 failure가 있어야 합니다.");
    }

    @Test
    void markProcessed를_호출하면_처리완료_상태와_처리시각이_저장된다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        setProcessingState(inbox, Instant.parse("2026-02-24T00:10:00Z"), 1);

        // when
        Instant processedAt = Instant.parse("2026-02-24T00:13:00Z");
        ReviewRequestInboxHistory history = inbox.markProcessed(processedAt);

        // then
        assertAll(
                () -> assertThat(inbox.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSED),
                () -> assertThat(inbox.hasClaimedProcessingLease()).isFalse(),
                () -> assertThat(inbox.getProcessedTime().occurredAt()).isEqualTo(processedAt),
                () -> assertThat(inbox.getFailedTime().isPresent()).isFalse(),
                () -> assertThat(inbox.getFailure().isPresent()).isFalse(),
                () -> assertThat(history.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSED),
                () -> assertThat(history.getFailure().isPresent()).isFalse()
        );
    }

    @Test
    void hasClaimedProcessingLease는_현재_lease_보유_여부와_시작시각_일치_여부를_제공한다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        Instant processingStartedAt = Instant.parse("2026-02-24T00:10:00Z");
        setProcessingState(inbox, processingStartedAt, 1);

        // when & then
        assertAll(
                () -> assertThat(inbox.hasClaimedProcessingLease()).isTrue(),
                () -> assertThat(inbox.hasClaimedProcessingLease(processingStartedAt)).isTrue(),
                () -> assertThat(inbox.hasClaimedProcessingLease(processingStartedAt.plusSeconds(1))).isFalse(),
                () -> assertThat(inbox.currentProcessingLeaseStartedAt()).isEqualTo(processingStartedAt)
        );
    }

    @Test
    void currentProcessingLeaseStartedAt는_lease가_없으면_예외를_던진다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();

        // when & then
        assertThatThrownBy(() -> inbox.currentProcessingLeaseStartedAt())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("processingLease를 보유하고 있지 않습니다.");
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
                () -> assertThat(inbox.hasClaimedProcessingLease()).isFalse(),
                () -> assertThat(inbox.getProcessedTime().isPresent()).isFalse(),
                () -> assertThat(inbox.getFailedTime().occurredAt()).isEqualTo(failedAt),
                () -> assertThat(inbox.getFailure().reason()).isEqualTo("retry"),
                () -> assertThat(inbox.getFailure().type()).isEqualTo(ReviewRequestInboxFailureType.RETRYABLE),
                () -> assertThat(history.getFailure().type()).isEqualTo(ReviewRequestInboxFailureType.RETRYABLE)
        );
    }

    @Test
    void markRetryPending은_PROCESSING_TIMEOUT_failureType을_허용한다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        setProcessingState(inbox, Instant.parse("2026-02-24T00:10:00Z"), 1);

        // when
        ReviewRequestInboxHistory history = inbox.markRetryPending(
                Instant.parse("2026-02-24T00:14:00Z"),
                "timeout",
                ReviewRequestInboxFailureType.PROCESSING_TIMEOUT
        );

        // then
        assertAll(
                () -> assertThat(inbox.getFailure().type()).isEqualTo(ReviewRequestInboxFailureType.PROCESSING_TIMEOUT),
                () -> assertThat(history.getFailure().type()).isEqualTo(ReviewRequestInboxFailureType.PROCESSING_TIMEOUT)
        );
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
                () -> assertThat(inbox.hasClaimedProcessingLease()).isFalse(),
                () -> assertThat(inbox.getProcessedTime().isPresent()).isFalse(),
                () -> assertThat(inbox.getFailedTime().occurredAt()).isEqualTo(failedAt),
                () -> assertThat(inbox.getFailure().reason()).isEqualTo("failure"),
                () -> assertThat(inbox.getFailure().type()).isEqualTo(ReviewRequestInboxFailureType.NON_RETRYABLE),
                () -> assertThat(history.getFailure().type()).isEqualTo(ReviewRequestInboxFailureType.NON_RETRYABLE)
        );
    }

    @Test
    void markFailed는_RETRYABLE_failureType을_허용하지_않는다() {
        // given
        ReviewRequestInbox inbox = pendingInbox();
        setProcessingState(inbox, Instant.parse("2026-02-24T00:10:00Z"), 1);

        // when & then
        assertThatThrownBy(() -> inbox.markFailed(
                Instant.parse("2026-02-24T00:15:00Z"),
                "failure",
                ReviewRequestInboxFailureType.RETRYABLE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("FAILED failureType이 올바르지 않습니다.");
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
        ReflectionTestUtils.setField(inbox, "processingLease", BoxProcessingLease.claimed(processingStartedAt));
        ReflectionTestUtils.setField(inbox, "processedTime", BoxEventTime.absent());
        ReflectionTestUtils.setField(inbox, "failedTime", BoxEventTime.absent());
        ReflectionTestUtils.setField(inbox, "failure", BoxFailureSnapshot.absent());
        ReflectionTestUtils.setField(inbox, "processingAttempt", processingAttempt);
    }
}
