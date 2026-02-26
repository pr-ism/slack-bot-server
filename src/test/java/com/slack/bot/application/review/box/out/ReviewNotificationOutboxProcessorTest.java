package com.slack.bot.application.review.box.out;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interactivity.box.BoxFailureReasonTruncator;
import com.slack.bot.application.interactivity.box.retry.InteractionRetryExceptionClassifier;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
import com.slack.bot.infrastructure.interaction.box.SlackInteractivityFailureType;
import com.slack.bot.infrastructure.interaction.client.NotificationTransportApiClient;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import com.slack.bot.infrastructure.review.box.out.repository.ReviewNotificationOutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResourceAccessException;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewNotificationOutboxProcessorTest {

    @Mock
    WorkspaceRepository workspaceRepository;

    @Mock
    NotificationTransportApiClient notificationTransportApiClient;

    @Mock
    ReviewNotificationOutboxRepository reviewNotificationOutboxRepository;

    ReviewNotificationOutboxProcessor processor;

    InteractionRetryExceptionClassifier classifier;

    @BeforeEach
    void setUp() {
        InteractionRetryProperties retryProperties = new InteractionRetryProperties(
                new InteractionRetryProperties.Retry(2, 100, 2.0, 1_000),
                new InteractionRetryProperties.Retry(2, 100, 2.0, 1_000)
        );
        classifier = InteractionRetryExceptionClassifier.create();

        Clock fixedClock = Clock.fixed(Instant.parse("2026-02-24T00:00:00Z"), ZoneOffset.UTC);

        processor = new ReviewNotificationOutboxProcessor(
                fixedClock,
                new ObjectMapper(),
                createOutboxRetryTemplate(retryProperties, classifier),
                workspaceRepository,
                new BoxFailureReasonTruncator(),
                retryProperties,
                notificationTransportApiClient,
                reviewNotificationOutboxRepository,
                classifier
        );
    }

    private RetryTemplate createOutboxRetryTemplate(
            InteractionRetryProperties retryProperties,
            InteractionRetryExceptionClassifier exceptionClassifier
    ) {
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(
                retryProperties.outbox().maxAttempts(),
                exceptionClassifier.getRetryableExceptions(),
                true,
                false
        ));

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(retryProperties.outbox().initialDelayMs());
        backOffPolicy.setMultiplier(retryProperties.outbox().multiplier());
        backOffPolicy.setMaxInterval(retryProperties.outbox().maxDelayMs());
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }

    @Test
    void processingTimeout이_0이하면_예외가_발생한다() {
        assertThatThrownBy(() -> processor.processPending(10, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("processingTimeoutMs");
    }

    @Test
    void processing_timeout_복구시_maxAttempts를_전달한다() {
        // given
        given(reviewNotificationOutboxRepository.findClaimable(10)).willReturn(List.of());

        // when
        processor.processPending(10, 60_000L);

        // then
        verify(reviewNotificationOutboxRepository).recoverTimeoutProcessing(
                any(),
                any(),
                anyString(),
                eq(2)
        );
    }

    @Test
    void 선점에_실패하면_처리를_진행하지_않는다() {
        // given
        ReviewNotificationOutbox pending = mock(ReviewNotificationOutbox.class);
        given(pending.getId()).willReturn(10L);
        given(reviewNotificationOutboxRepository.findClaimable(10)).willReturn(List.of(pending));
        given(reviewNotificationOutboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(false);

        // when
        processor.processPending(10, 60_000L);

        // then
        verify(reviewNotificationOutboxRepository, never()).findById(anyLong());
        verify(reviewNotificationOutboxRepository, never()).save(any());
    }

    @Test
    void 선점_성공후_재조회에_실패하면_저장없이_종료한다() {
        // given
        ReviewNotificationOutbox pending = mockPending(10L);
        given(reviewNotificationOutboxRepository.findClaimable(10)).willReturn(List.of(pending));
        given(reviewNotificationOutboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(true);
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.empty());

        // when
        processor.processPending(10, 60_000L);

        // then
        verify(reviewNotificationOutboxRepository, never()).save(any());
    }

    @Test
    void 정상_처리되면_메시지를_전송하고_SENT로_저장한다() {
        // given
        ReviewNotificationOutbox pending = mockPending(10L);
        ReviewNotificationOutbox claimed = mockClaimed(10L, 1);
        given(reviewNotificationOutboxRepository.findClaimable(10)).willReturn(List.of(pending));
        given(reviewNotificationOutboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(true);
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(claimed));

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));

        // when
        processor.processPending(10, 60_000L);

        // then
        verify(notificationTransportApiClient).sendBlockMessage(
                eq("xoxb-test-token"),
                eq("C1"),
                any(JsonNode.class),
                eq("fallback")
        );
        verify(claimed).markSent(any());
        verify(reviewNotificationOutboxRepository).save(claimed);
    }

    @Test
    void 전송_성공후_SENT_저장_실패는_실패상태로_재마킹하지_않는다() {
        // given
        ReviewNotificationOutbox pending = mockPending(10L);
        ReviewNotificationOutbox claimed = mockClaimed(10L, 1);
        given(reviewNotificationOutboxRepository.findClaimable(10)).willReturn(List.of(pending));
        given(reviewNotificationOutboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(true);
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(claimed));

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));

        doThrow(new RuntimeException("db failure"))
                .when(reviewNotificationOutboxRepository)
                .save(claimed);

        // when
        processor.processPending(10, 60_000L);

        // then
        verify(notificationTransportApiClient).sendBlockMessage(
                eq("xoxb-test-token"),
                eq("C1"),
                any(JsonNode.class),
                eq("fallback")
        );
        verify(claimed).markSent(any());
        verify(claimed, never()).markRetryPending(any(), anyString());
        verify(claimed, never()).markFailed(any(), anyString(), any());
    }

    @Test
    void 비재시도_예외면_FAILED_BUSINESS_INVARIANT로_저장한다() {
        // given
        ReviewNotificationOutbox pending = mockPending(10L);
        ReviewNotificationOutbox claimed = mockClaimed(10L, 1);
        given(reviewNotificationOutboxRepository.findClaimable(10)).willReturn(List.of(pending));
        given(reviewNotificationOutboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(true);
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(claimed));

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));

        RuntimeException exception = new RuntimeException("business failure");
        doThrow(exception)
                .when(notificationTransportApiClient)
                .sendBlockMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        any(JsonNode.class),
                        eq("fallback")
                );

        // when
        processor.processPending(10, 60_000L);

        // then
        verify(claimed).markFailed(any(), anyString(), eq(SlackInteractivityFailureType.BUSINESS_INVARIANT));
        verify(reviewNotificationOutboxRepository).save(claimed);
    }

    @Test
    void 재시도_가능_예외이고_시도횟수가_남아있으면_RETRY_PENDING으로_저장한다() {
        // given
        ReviewNotificationOutbox pending = mockPending(10L);
        ReviewNotificationOutbox claimed = mockClaimed(10L, 1);
        given(reviewNotificationOutboxRepository.findClaimable(10)).willReturn(List.of(pending));
        given(reviewNotificationOutboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(true);
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(claimed));

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));

        RuntimeException exception = new ResourceAccessException("io failure");
        doThrow(exception)
                .when(notificationTransportApiClient)
                .sendBlockMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        any(JsonNode.class),
                        eq("fallback")
                );

        // when
        processor.processPending(10, 60_000L);

        // then
        verify(claimed).markRetryPending(any(), anyString());
        verify(reviewNotificationOutboxRepository).save(claimed);
    }

    @Test
    void 재시도_가능_예외이고_최대시도_도달이면_FAILED_RETRY_EXHAUSTED로_저장한다() {
        // given
        ReviewNotificationOutbox pending = mockPending(10L);
        ReviewNotificationOutbox claimed = mockClaimed(10L, 2);
        given(reviewNotificationOutboxRepository.findClaimable(10)).willReturn(List.of(pending));
        given(reviewNotificationOutboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(true);
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(claimed));

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));

        RuntimeException exception = new ResourceAccessException("io failure");
        doThrow(exception)
                .when(notificationTransportApiClient)
                .sendBlockMessage(
                        eq("xoxb-test-token"),
                        eq("C1"),
                        any(JsonNode.class),
                        eq("fallback")
                );

        // when
        processor.processPending(10, 60_000L);

        // then
        verify(claimed).markFailed(any(), anyString(), eq(SlackInteractivityFailureType.RETRY_EXHAUSTED));
        verify(reviewNotificationOutboxRepository).save(claimed);
    }

    @Test
    void 팀에_해당하는_workspace가_없으면_FAILED_BUSINESS_INVARIANT로_저장한다() {
        // given
        ReviewNotificationOutbox pending = mockPending(10L);
        ReviewNotificationOutbox claimed = mockClaimed(10L, 1);
        given(reviewNotificationOutboxRepository.findClaimable(10)).willReturn(List.of(pending));
        given(reviewNotificationOutboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(true);
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(claimed));
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.empty());

        // when
        processor.processPending(10, 60_000L);

        // then
        verify(claimed).markFailed(any(), anyString(), eq(SlackInteractivityFailureType.BUSINESS_INVARIANT));
        verify(reviewNotificationOutboxRepository).save(claimed);
    }

    private ReviewNotificationOutbox mockPending(Long id) {
        ReviewNotificationOutbox outbox = mock(ReviewNotificationOutbox.class);
        given(outbox.getId()).willReturn(id);

        return outbox;
    }

    private ReviewNotificationOutbox mockClaimed(Long id, int processingAttempt) {
        ReviewNotificationOutbox outbox = mock(ReviewNotificationOutbox.class);
        given(outbox.getId()).willReturn(id);
        given(outbox.getTeamId()).willReturn("T1");
        given(outbox.getChannelId()).willReturn("C1");
        given(outbox.getBlocksJson()).willReturn("[]");
        given(outbox.getFallbackText()).willReturn("fallback");
        given(outbox.getProcessingAttempt()).willReturn(processingAttempt);

        return outbox;
    }
}
