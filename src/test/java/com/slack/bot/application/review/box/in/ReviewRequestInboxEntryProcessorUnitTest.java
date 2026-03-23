package com.slack.bot.application.review.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interaction.box.BoxFailureReasonTruncator;
import com.slack.bot.application.interaction.box.retry.InteractionRetryExceptionClassifier;
import com.slack.bot.application.review.ReviewNotificationService;
import com.slack.bot.application.review.box.ReviewNotificationSourceContext;
import com.slack.bot.application.review.dto.ReviewNotificationPayload;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
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
class ReviewRequestInboxEntryProcessorUnitTest {

    @Mock
    ReviewNotificationService reviewNotificationService;

    @Mock
    ReviewRequestInboxRepository reviewRequestInboxRepository;

    ReviewRequestInboxEntryProcessor reviewRequestInboxEntryProcessor;

    ReviewNotificationSourceContext reviewNotificationSourceContext;

    @BeforeEach
    void setUp() {
        InteractionRetryProperties retryProperties = new InteractionRetryProperties(
                new InteractionRetryProperties.InboxRetryProperties(2, 100, 2.0, 1000),
                new InteractionRetryProperties.OutboxRetryProperties(2, 100, 2.0, 1000)
        );
        InteractionRetryExceptionClassifier classifier = InteractionRetryExceptionClassifier.create();
        reviewNotificationSourceContext = new ReviewNotificationSourceContext();

        reviewRequestInboxEntryProcessor = new ReviewRequestInboxEntryProcessor(
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
        reviewRequestInboxEntryProcessor.processClaimedInbox(10L);

        // then
        verify(reviewRequestInboxRepository, never()).save(any());
        verify(reviewNotificationService, never()).sendSimpleNotification(any(), any());
    }

    @Test
    void 정상처리시_알림을_전송하고_PROCESSED로_저장된다() {
        // given
        ReviewNotificationPayload request = request(101L, "success");
        ReviewRequestInbox actual = processingInbox("review-entry-success", requestJson(request), 1);

        given(reviewRequestInboxRepository.findById(11L)).willReturn(Optional.of(actual));

        // when
        reviewRequestInboxEntryProcessor.processClaimedInbox(11L);

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewRequestInboxStatus.PROCESSED),
                () -> assertThat(actual.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(reviewNotificationSourceContext.currentSourceKey()).isEmpty()
        );
        verify(reviewNotificationService).sendSimpleNotification("test-api-key", request);
        verify(reviewRequestInboxRepository).save(actual);
    }

    @Test
    void 재시도_가능_예외이고_첫_시도면_RETRY_PENDING으로_마킹된다() {
        // given
        ReviewNotificationPayload request = request(102L, "retry-first-attempt");
        ReviewRequestInbox actual = processingInbox("review-entry-retry-1", requestJson(request), 1);

        given(reviewRequestInboxRepository.findById(12L)).willReturn(Optional.of(actual));
        willThrow(new ResourceAccessException("temporary network failure"))
                .given(reviewNotificationService)
                .sendSimpleNotification(any(), any());

        // when
        reviewRequestInboxEntryProcessor.processClaimedInbox(12L);

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewRequestInboxStatus.RETRY_PENDING),
                () -> assertThat(actual.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actual.getFailureType()).isNull()
        );
        verify(reviewRequestInboxRepository).save(actual);
    }

    @Test
    void 재시도_가능_예외이고_최대_시도에_도달하면_FAILED와_RETRY_EXHAUSTED로_마킹된다() {
        // given
        ReviewNotificationPayload request = request(103L, "retry-max-attempt");
        ReviewRequestInbox actual = processingInbox("review-entry-retry-2", requestJson(request), 1);
        actual.markRetryPending(Instant.parse("2026-02-15T00:01:00Z"), "previous retry failure");
        setProcessingState(actual, Instant.parse("2026-02-15T00:02:00Z"), 2);

        given(reviewRequestInboxRepository.findById(13L)).willReturn(Optional.of(actual));
        willThrow(new ResourceAccessException("temporary network failure"))
                .given(reviewNotificationService)
                .sendSimpleNotification(any(), any());

        // when
        reviewRequestInboxEntryProcessor.processClaimedInbox(13L);

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewRequestInboxStatus.FAILED),
                () -> assertThat(actual.getProcessingAttempt()).isEqualTo(2),
                () -> assertThat(actual.getFailureType()).isEqualTo(ReviewRequestInboxFailureType.RETRY_EXHAUSTED)
        );
        verify(reviewRequestInboxRepository).save(actual);
    }

    @Test
    void 긴_실패사유는_잘려서_저장된다() {
        // given
        ReviewNotificationPayload request = request(104L, "long-failure-reason");
        ReviewRequestInbox actual = processingInbox("review-entry-long-failure", requestJson(request), 1);

        given(reviewRequestInboxRepository.findById(14L)).willReturn(Optional.of(actual));
        willThrow(new IllegalArgumentException("x".repeat(600)))
                .given(reviewNotificationService)
                .sendSimpleNotification(any(), any());

        // when
        reviewRequestInboxEntryProcessor.processClaimedInbox(14L);

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewRequestInboxStatus.FAILED),
                () -> assertThat(actual.getFailureType()).isEqualTo(ReviewRequestInboxFailureType.NON_RETRYABLE),
                () -> assertThat(actual.getFailureReason()).hasSize(500)
        );
        verify(reviewRequestInboxRepository).save(actual);
    }

    @Test
    void 예외메시지가_비어있으면_unknown_failure로_저장된다() {
        // given
        ReviewNotificationPayload request = request(105L, "empty-failure-reason");
        ReviewRequestInbox actual = processingInbox("review-entry-empty-failure", requestJson(request), 1);

        given(reviewRequestInboxRepository.findById(15L)).willReturn(Optional.of(actual));
        willThrow(new RuntimeException())
                .given(reviewNotificationService)
                .sendSimpleNotification(any(), any());

        // when
        reviewRequestInboxEntryProcessor.processClaimedInbox(15L);

        // then
        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(ReviewRequestInboxStatus.FAILED),
                () -> assertThat(actual.getFailureType()).isEqualTo(ReviewRequestInboxFailureType.NON_RETRYABLE),
                () -> assertThat(actual.getFailureReason()).isEqualTo("unknown failure")
        );
        verify(reviewRequestInboxRepository).save(actual);
    }

    @Test
    void availableAt이_null이면_epochMillis는_0을_반환한다() throws Exception {
        // given
        Method method = ReviewRequestInboxEntryProcessor.class.getDeclaredMethod(
                "resolveAvailableAtEpochMillis",
                Instant.class
        );
        method.setAccessible(true);

        // when
        long actual = (long) method.invoke(reviewRequestInboxEntryProcessor, new Object[]{null});

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
        setProcessingState(inbox, Instant.parse("2026-02-15T00:00:00Z"), processingAttempt);
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
        } catch (Exception exception) {
            throw new IllegalStateException("request 직렬화에 실패했습니다.", exception);
        }
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
