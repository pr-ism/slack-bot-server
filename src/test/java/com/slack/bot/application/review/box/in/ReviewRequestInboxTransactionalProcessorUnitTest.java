package com.slack.bot.application.review.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interaction.box.BoxFailureReasonTruncator;
import com.slack.bot.application.interaction.box.retry.InteractionRetryExceptionClassifier;
import com.slack.bot.application.review.ReviewNotificationService;
import com.slack.bot.application.review.box.ReviewNotificationSourceContext;
import com.slack.bot.application.review.box.in.exception.ReviewRequestInboxProcessingLeaseLostException;
import com.slack.bot.application.review.dto.ReviewNotificationPayload;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxFailureType;
import com.slack.bot.infrastructure.review.box.in.ReviewRequestInboxStatus;
import com.slack.bot.infrastructure.review.box.in.repository.ReviewRequestInboxRepository;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewRequestInboxTransactionalProcessorUnitTest {

    private static final Instant CLAIMED_PROCESSING_STARTED_AT = Instant.parse("2026-02-15T00:00:05.123456Z");

    @Mock
    ReviewNotificationService reviewNotificationService;

    @Mock
    ReviewRequestInboxRepository reviewRequestInboxRepository;

    ReviewRequestInboxTransactionalProcessor reviewRequestInboxTransactionalProcessor;

    ReviewNotificationSourceContext reviewNotificationSourceContext;

    @BeforeEach
    void setUp() {
        InteractionRetryProperties retryProperties = new InteractionRetryProperties(
                new InteractionRetryProperties.InboxRetryProperties(2, 100, 2.0, 1000),
                new InteractionRetryProperties.OutboxRetryProperties(2, 100, 2.0, 1000)
        );
        InteractionRetryExceptionClassifier classifier = InteractionRetryExceptionClassifier.create();
        reviewNotificationSourceContext = new ReviewNotificationSourceContext();

        reviewRequestInboxTransactionalProcessor = new ReviewRequestInboxTransactionalProcessor(
                Clock.systemUTC(),
                new ObjectMapper(),
                reviewNotificationService,
                reviewNotificationSourceContext,
                new BoxFailureReasonTruncator(),
                retryProperties,
                reviewRequestInboxRepository,
                classifier
        );
    }

    @Test
    void claimed_inbox를_조회하지_못하면_추가_처리없이_종료한다() {
        // given
        given(reviewRequestInboxRepository.findById(10L)).willReturn(Optional.empty());

        // when
        reviewRequestInboxTransactionalProcessor.processInTransaction(10L, CLAIMED_PROCESSING_STARTED_AT);

        // then
        verify(reviewRequestInboxRepository, never()).save(any());
        verify(reviewRequestInboxRepository, never()).saveIfProcessingLeaseMatched(any(), any(), any());
        verify(reviewNotificationService, never()).sendSimpleNotification(any(), any());
    }

    @Test
    void 정상처리시_알림을_전송하고_PROCESSED로_저장된다() {
        // given
        ReviewNotificationPayload request = request(101L, "success");
        ReviewRequestInbox actual = processingInbox("review-entry-success", requestJson(request), 1);

        given(reviewRequestInboxRepository.findById(11L)).willReturn(Optional.of(actual));
        given(reviewRequestInboxRepository.saveIfProcessingLeaseMatched(eq(actual), any(), eq(CLAIMED_PROCESSING_STARTED_AT)))
                .willReturn(true);

        // when
        reviewRequestInboxTransactionalProcessor.processInTransaction(11L, CLAIMED_PROCESSING_STARTED_AT);

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSED),
                () -> assertThat(actual.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(reviewNotificationSourceContext.currentSourceKey()).isEmpty()
        );
        verify(reviewNotificationService).sendSimpleNotification("test-api-key", request);
        verify(reviewRequestInboxRepository).saveIfProcessingLeaseMatched(eq(actual), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
    }

    @Test
    void 비즈니스_구간_예외는_전파하고_실패상태를_저장하지_않는다() {
        // given
        ReviewNotificationPayload request = request(102L, "retry-first-attempt");
        ReviewRequestInbox actual = processingInbox("review-entry-retry-1", requestJson(request), 1);

        given(reviewRequestInboxRepository.findById(12L)).willReturn(Optional.of(actual));
        willThrow(new ResourceAccessException("temporary network failure"))
                .given(reviewNotificationService)
                .sendSimpleNotification(any(), any());

        // when & then
        assertThatThrownBy(() -> reviewRequestInboxTransactionalProcessor.processInTransaction(12L, CLAIMED_PROCESSING_STARTED_AT))
                .isInstanceOf(ResourceAccessException.class)
                .hasMessageContaining("temporary network failure");

        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSING),
                () -> assertThat(actual.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actual.getFailureType()).isEqualTo(ReviewRequestInboxFailureType.NONE)
        );
        verify(reviewRequestInboxRepository, never()).save(actual);
        verify(reviewRequestInboxRepository, never()).saveIfProcessingLeaseMatched(any(), any(), any());
    }

    @Test
    void 최종_저장시_lease를_상실하면_예외를_던진다() {
        // given
        ReviewNotificationPayload request = request(103L, "lease-lost");
        ReviewRequestInbox actual = processingInbox("review-entry-lease-lost", requestJson(request), 1);

        given(reviewRequestInboxRepository.findById(13L)).willReturn(Optional.of(actual));
        given(reviewRequestInboxRepository.saveIfProcessingLeaseMatched(eq(actual), any(), eq(CLAIMED_PROCESSING_STARTED_AT)))
                .willReturn(false);

        // when & then
        assertThatThrownBy(() -> reviewRequestInboxTransactionalProcessor.processInTransaction(13L, CLAIMED_PROCESSING_STARTED_AT))
                .isInstanceOf(ReviewRequestInboxProcessingLeaseLostException.class)
                .hasMessageContaining("processing lease");
    }

    @Test
    void 긴_실패사유는_JSON_파싱_실패에서_잘려서_저장된다() {
        // given
        ReviewRequestInbox actual = processingInbox("review-entry-long-failure", "x".repeat(600), 1);

        given(reviewRequestInboxRepository.findById(14L)).willReturn(Optional.of(actual));
        given(reviewRequestInboxRepository.saveIfProcessingLeaseMatched(eq(actual), any(), eq(CLAIMED_PROCESSING_STARTED_AT)))
                .willReturn(true);

        // when
        reviewRequestInboxTransactionalProcessor.processInTransaction(14L, CLAIMED_PROCESSING_STARTED_AT);

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewRequestInboxStatus.FAILED),
                () -> assertThat(actual.getFailureType()).isEqualTo(ReviewRequestInboxFailureType.NON_RETRYABLE),
                () -> assertThat(actual.getFailureReason()).isNotBlank(),
                () -> assertThat(actual.getFailureReason().length()).isLessThanOrEqualTo(500)
        );
        verify(reviewRequestInboxRepository).saveIfProcessingLeaseMatched(eq(actual), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
    }

    @Test
    void JSON_파싱_예외_메시지는_실패사유로_저장된다() {
        // given
        ReviewRequestInbox actual = processingInbox("review-entry-empty-failure", "{", 1);

        given(reviewRequestInboxRepository.findById(15L)).willReturn(Optional.of(actual));
        given(reviewRequestInboxRepository.saveIfProcessingLeaseMatched(eq(actual), any(), eq(CLAIMED_PROCESSING_STARTED_AT)))
                .willReturn(true);

        // when
        reviewRequestInboxTransactionalProcessor.processInTransaction(15L, CLAIMED_PROCESSING_STARTED_AT);

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewRequestInboxStatus.FAILED),
                () -> assertThat(actual.getFailureType()).isEqualTo(ReviewRequestInboxFailureType.NON_RETRYABLE),
                () -> assertThat(actual.getFailureReason()).isNotBlank()
        );
        verify(reviewRequestInboxRepository).saveIfProcessingLeaseMatched(eq(actual), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
    }

    @Test
    void retryable_예외면_markFailureStatus가_RETRY_PENDING으로_전이한다() {
        // given
        ReviewRequestInbox actual = processingInbox("review-entry-mark-retry", requestJson(request(106L, "retry")), 1);

        // when
        ReflectionTestUtils.invokeMethod(
                reviewRequestInboxTransactionalProcessor,
                "markFailureStatus",
                actual,
                new ResourceAccessException("temporary network failure")
        );

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewRequestInboxStatus.RETRY_PENDING),
                () -> assertThat(actual.getFailureType()).isEqualTo(ReviewRequestInboxFailureType.NONE),
                () -> assertThat(actual.getFailureReason()).isNotBlank()
        );
    }

    @Test
    void retryable_예외이고_최대_시도에_도달하면_markFailureStatus가_FAILED로_전이한다() {
        // given
        ReviewRequestInbox actual = processingInbox("review-entry-mark-failed", requestJson(request(107L, "failed")), 2);

        // when
        ReflectionTestUtils.invokeMethod(
                reviewRequestInboxTransactionalProcessor,
                "markFailureStatus",
                actual,
                new ResourceAccessException("temporary network failure")
        );

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewRequestInboxStatus.FAILED),
                () -> assertThat(actual.getFailureType()).isEqualTo(ReviewRequestInboxFailureType.RETRY_EXHAUSTED),
                () -> assertThat(actual.getFailureReason()).isNotBlank()
        );
    }

    @Test
    void PROCESSING이_아닌_상태면_handleFailure는_아무것도_변경하지_않는다() {
        // given
        ReviewRequestInbox actual = ReviewRequestInbox.pending(
                "review-entry-no-op",
                "test-api-key",
                108L,
                requestJson(request(108L, "noop")),
                Instant.parse("2026-02-15T00:00:00Z")
        );

        // when
        ReflectionTestUtils.invokeMethod(
                reviewRequestInboxTransactionalProcessor,
                "handleFailure",
                actual,
                CLAIMED_PROCESSING_STARTED_AT,
                new IllegalArgumentException("invalid request")
        );

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewRequestInboxStatus.PENDING),
                () -> assertThat(actual.getFailureType()).isEqualTo(ReviewRequestInboxFailureType.NONE),
                () -> assertThat(actual.getFailureReason()).isEqualTo(FailureSnapshotDefaults.NO_FAILURE_REASON)
        );
    }

    @Test
    void availableAt이_null이면_epochMillis는_0을_반환한다() throws Exception {
        // given
        Method method = ReviewRequestInboxTransactionalProcessor.class.getDeclaredMethod(
                "resolveAvailableAtEpochMillis",
                Instant.class
        );
        method.setAccessible(true);

        // when
        long actual = (long) method.invoke(reviewRequestInboxTransactionalProcessor, new Object[]{null});

        // then
        assertThat(actual).isZero();
    }

    private ReviewRequestInbox processingInbox(
            String idempotencyKey,
            String requestJson,
            int processingAttempt
    ) {
        ReviewRequestInbox inbox = ReviewRequestInbox.pending(
                idempotencyKey,
                "test-api-key",
                100L,
                requestJson,
                Instant.parse("2026-02-15T00:00:00Z")
        );
        setProcessingState(inbox, CLAIMED_PROCESSING_STARTED_AT, processingAttempt);
        return inbox;
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

    private String requestJson(ReviewNotificationPayload request) {
        try {
            return new ObjectMapper().writeValueAsString(request);
        } catch (Exception e) {
            throw new IllegalStateException("request 직렬화에 실패했습니다.", e);
        }
    }

    private void setProcessingState(ReviewRequestInbox inbox, Instant processingStartedAt, int processingAttempt) {
        ReflectionTestUtils.setField(inbox, "status", ReviewRequestInboxStatus.PROCESSING);
        ReflectionTestUtils.setField(inbox, "processingStartedAt", processingStartedAt);
        ReflectionTestUtils.setField(inbox, "processedAt", FailureSnapshotDefaults.NO_PROCESSED_AT);
        ReflectionTestUtils.setField(inbox, "processingAttempt", processingAttempt);
    }
}
