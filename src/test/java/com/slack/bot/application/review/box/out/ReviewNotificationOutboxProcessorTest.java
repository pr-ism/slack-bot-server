package com.slack.bot.application.review.box.out;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interaction.box.BoxFailureReasonTruncator;
import com.slack.bot.application.interaction.box.retry.InteractionRetryExceptionClassifier;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
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
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResourceAccessException;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
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
                new InteractionRetryProperties.InboxRetryProperties(2, 100, 2.0, 1_000),
                new InteractionRetryProperties.OutboxRetryProperties(2, 100, 2.0, 1_000)
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
        assertThatThrownBy(() -> processor.recoverTimeoutProcessing(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("processingTimeoutMs");
    }

    @Test
    void processingTimeout이_음수이면_예외가_발생한다() {
        assertThatThrownBy(() -> processor.recoverTimeoutProcessing(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("processingTimeoutMs");
    }

    @Test
    void processing_timeout_복구시_maxAttempts를_전달한다() {
        // when
        processor.recoverTimeoutProcessing(60_000L);

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
        processor.processPending(10);

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
        processor.processPending(10);

        // then
        verify(reviewNotificationOutboxRepository, never()).save(any());
    }

    @Test
    void 정상_처리되면_메시지를_전송하고_SENT로_저장한다() {
        // given
        ReviewNotificationOutbox pending = mockPending(10L);
        ReviewNotificationOutbox claimed = spyClaimed("C1", 1, null);
        given(reviewNotificationOutboxRepository.findClaimable(10)).willReturn(List.of(pending));
        given(reviewNotificationOutboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(true);
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(claimed));

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));

        // when
        processor.processPending(10);

        // then
        verify(notificationTransportApiClient).sendBlockMessage(
                eq("xoxb-test-token"),
                eq("C1"),
                any(JsonNode.class),
                eq(null),
                eq("fallback")
        );
        verify(claimed).markSent(any());
        verify(reviewNotificationOutboxRepository).save(claimed);
    }

    @Test
    void 전송_성공후_SENT_저장_실패는_실패상태로_재마킹하지_않는다() {
        // given
        ReviewNotificationOutbox pending = mockPending(10L);
        ReviewNotificationOutbox claimed = spyClaimed("C1", 1, null);
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
        processor.processPending(10);

        // then
        verify(notificationTransportApiClient).sendBlockMessage(
                eq("xoxb-test-token"),
                eq("C1"),
                any(JsonNode.class),
                eq(null),
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
        ReviewNotificationOutbox claimed = spyClaimed("C1", 1, null);
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
                        eq(null),
                        eq("fallback")
                );

        // when
        processor.processPending(10);

        // then
        verify(claimed).markFailed(any(), anyString(), eq(SlackInteractionFailureType.BUSINESS_INVARIANT));
        verify(reviewNotificationOutboxRepository).save(claimed);
    }

    @Test
    void 실패상태_저장에_실패해도_다음_outbox_처리를_계속한다() {
        // given
        ReviewNotificationOutbox firstPending = mockPending(10L);
        ReviewNotificationOutbox secondPending = mockPending(20L);
        ReviewNotificationOutbox firstClaimed = spyClaimed("C1", 1, null);
        ReviewNotificationOutbox secondClaimed = spyClaimed("C2", 1, null);

        given(reviewNotificationOutboxRepository.findClaimable(10)).willReturn(List.of(firstPending, secondPending));
        given(reviewNotificationOutboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(true);
        given(reviewNotificationOutboxRepository.markProcessingIfClaimable(eq(20L), any())).willReturn(true);
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(firstClaimed));
        given(reviewNotificationOutboxRepository.findById(20L)).willReturn(Optional.of(secondClaimed));

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));

        doThrow(new ResourceAccessException("io failure"))
                .when(notificationTransportApiClient)
                .sendBlockMessage(eq("xoxb-test-token"), eq("C1"), any(JsonNode.class), eq(null), eq("fallback"));
        doThrow(new RuntimeException("db failure"))
                .when(reviewNotificationOutboxRepository)
                .save(firstClaimed);

        // when & then
        assertThatCode(() -> processor.processPending(10)).doesNotThrowAnyException();

        verify(firstClaimed).markRetryPending(any(), anyString());
        verify(secondClaimed).markSent(any());
        verify(reviewNotificationOutboxRepository).save(secondClaimed);
        verify(reviewNotificationOutboxRepository, times(2)).save(any(ReviewNotificationOutbox.class));
    }

    @Test
    void 예상치못한_예외가_발생해도_다음_outbox_처리를_계속한다() {
        // given
        ReviewNotificationOutbox firstPending = mockPending(10L);
        ReviewNotificationOutbox secondPending = mockPending(20L);
        ReviewNotificationOutbox firstClaimed = spyClaimed("C1", 1, null);
        ReviewNotificationOutbox secondClaimed = spyClaimed("C2", 1, null);

        given(reviewNotificationOutboxRepository.findClaimable(10)).willReturn(List.of(firstPending, secondPending));
        given(reviewNotificationOutboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(true);
        given(reviewNotificationOutboxRepository.markProcessingIfClaimable(eq(20L), any())).willReturn(true);
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(firstClaimed));
        given(reviewNotificationOutboxRepository.findById(20L)).willReturn(Optional.of(secondClaimed));

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));

        doThrow(new IllegalStateException("invalid status"))
                .when(firstClaimed)
                .markSent(any());

        // when & then
        assertThatCode(() -> processor.processPending(10)).doesNotThrowAnyException();

        verify(notificationTransportApiClient).sendBlockMessage(
                eq("xoxb-test-token"),
                eq("C1"),
                any(JsonNode.class),
                eq(null),
                eq("fallback")
        );
        verify(notificationTransportApiClient).sendBlockMessage(
                eq("xoxb-test-token"),
                eq("C2"),
                any(JsonNode.class),
                eq(null),
                eq("fallback")
        );
        verify(reviewNotificationOutboxRepository, never()).save(firstClaimed);
        verify(secondClaimed).markSent(any());
        verify(reviewNotificationOutboxRepository).save(secondClaimed);
    }

    @Test
    void 재시도_가능_예외이고_시도횟수가_남아있으면_RETRY_PENDING으로_저장한다() {
        // given
        ReviewNotificationOutbox pending = mockPending(10L);
        ReviewNotificationOutbox claimed = spyClaimed("C1", 1, null);
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
                        eq(null),
                        eq("fallback")
                );

        // when
        processor.processPending(10);

        // then
        verify(claimed).markRetryPending(any(), anyString());
        verify(reviewNotificationOutboxRepository).save(claimed);
    }

    @Test
    void 재시도_가능_예외이고_최대시도_도달이면_FAILED_RETRY_EXHAUSTED로_저장한다() {
        // given
        ReviewNotificationOutbox pending = mockPending(10L);
        ReviewNotificationOutbox claimed = spyClaimed("C1", 2, null);
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
                        eq(null),
                        eq("fallback")
                );

        // when
        processor.processPending(10);

        // then
        verify(claimed).markFailed(any(), anyString(), eq(SlackInteractionFailureType.RETRY_EXHAUSTED));
        verify(reviewNotificationOutboxRepository).save(claimed);
    }

    @Test
    void 팀에_해당하는_workspace가_없으면_FAILED_BUSINESS_INVARIANT로_저장한다() {
        // given
        ReviewNotificationOutbox pending = mockPending(10L);
        ReviewNotificationOutbox claimed = spyClaimed("C1", 1, null);
        given(reviewNotificationOutboxRepository.findClaimable(10)).willReturn(List.of(pending));
        given(reviewNotificationOutboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(true);
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(claimed));
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.empty());

        // when
        processor.processPending(10);

        // then
        verify(claimed).markFailed(any(), anyString(), eq(SlackInteractionFailureType.BUSINESS_INVARIANT));
        verify(reviewNotificationOutboxRepository).save(claimed);
    }

    @Test
    void attachments_json이_있으면_attachment를_보존해_전송한다() {
        // given
        ReviewNotificationOutbox pending = mockPending(10L);
        ReviewNotificationOutbox claimed = spyClaimed(
                "C1",
                1,
                "[{\"color\":\"#6366F1\",\"blocks\":[{\"type\":\"actions\"}]}]"
        );
        given(reviewNotificationOutboxRepository.findClaimable(10)).willReturn(List.of(pending));
        given(reviewNotificationOutboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(true);
        given(reviewNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(claimed));

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));

        // when
        processor.processPending(10);

        // then
        verify(notificationTransportApiClient).sendBlockMessage(
                eq("xoxb-test-token"),
                eq("C1"),
                any(JsonNode.class),
                any(JsonNode.class),
                eq("fallback")
        );
        verify(reviewNotificationOutboxRepository).save(claimed);
    }

    private ReviewNotificationOutbox mockPending(Long id) {
        ReviewNotificationOutbox outbox = mock(ReviewNotificationOutbox.class);
        given(outbox.getId()).willReturn(id);

        return outbox;
    }

    private ReviewNotificationOutbox spyClaimed(String channelId, int processingAttempt, String attachmentsJson) {
        ReviewNotificationOutbox outbox = ReviewNotificationOutbox.builder()
                                                                  .idempotencyKey("OUTBOX-" + channelId + "-" + processingAttempt)
                                                                  .teamId("T1")
                                                                  .channelId(channelId)
                                                                  .blocksJson("[]")
                                                                  .attachmentsJson(attachmentsJson)
                                                                  .fallbackText("fallback")
                                                                  .build();
        Instant base = Instant.parse("2026-02-24T00:00:00Z");
        outbox.markProcessing(base.minusSeconds(120));

        for (int attempt = 1; attempt < processingAttempt; attempt++) {
            outbox.markRetryPending(base.minusSeconds(110 - attempt), "retry failure");
            outbox.markProcessing(base.minusSeconds(100 - attempt));
        }

        return spy(outbox);
    }
}
