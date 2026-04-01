package com.slack.bot.application.interaction.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interaction.box.BoxFailureReasonTruncator;
import com.slack.bot.application.interaction.box.retry.EqualJitterExponentialBackOffPolicy;
import com.slack.bot.application.interaction.box.retry.InteractionRetryExceptionClassifier;
import com.slack.bot.application.worker.PollingHintPublisher;
import com.slack.bot.application.worker.PollingHintTarget;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
import com.slack.bot.global.config.properties.InteractionWorkerProperties;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import com.slack.bot.infrastructure.interaction.box.out.repository.SlackNotificationOutboxRepository;
import com.slack.bot.infrastructure.interaction.client.NotificationTransportApiClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackNotificationOutboxProcessorUnitTest {

    private static final Instant CLAIMED_PROCESSING_STARTED_AT = Instant.parse("2026-03-24T00:00:00.123456Z");
    private static final int OUTBOX_RECOVERY_BATCH_SIZE = 37;

    @Mock
    NotificationTransportApiClient notificationTransportApiClient;

    @Mock
    SlackNotificationOutboxRepository slackNotificationOutboxRepository;

    @Mock
    WorkspaceRepository workspaceRepository;

    @Mock
    PollingHintPublisher pollingHintPublisher;

    SlackNotificationOutboxProcessor slackNotificationOutboxProcessor;

    @BeforeEach
    void setUp() {
        InteractionRetryProperties retryProperties = new InteractionRetryProperties(
                new InteractionRetryProperties.InboxRetryProperties(2, 100, 2.0, 1000),
                new InteractionRetryProperties.OutboxRetryProperties(2, 100, 2.0, 1000)
        );
        InteractionRetryExceptionClassifier classifier = InteractionRetryExceptionClassifier.create();
        Clock fixedClock = Clock.fixed(CLAIMED_PROCESSING_STARTED_AT, ZoneOffset.UTC);

        slackNotificationOutboxProcessor = new SlackNotificationOutboxProcessor(
                fixedClock,
                new ObjectMapper(),
                createOutboxRetryTemplate(retryProperties, classifier),
                workspaceRepository,
                new BoxFailureReasonTruncator(),
                retryProperties,
                new InteractionWorkerProperties(
                        new InteractionWorkerProperties.InboxProperties(),
                        new InteractionWorkerProperties.OutboxProperties(
                                1_000L,
                                60_000L,
                                30_000L,
                                OUTBOX_RECOVERY_BATCH_SIZE
                        )
                ),
                pollingHintPublisher,
                notificationTransportApiClient,
                slackNotificationOutboxRepository,
                classifier
        );

        lenient().when(slackNotificationOutboxRepository.renewProcessingLease(any(), any(), any())).thenReturn(true);
    }

    private RetryTemplate createOutboxRetryTemplate(
            InteractionRetryProperties retryProperties,
            InteractionRetryExceptionClassifier classifier
    ) {
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(
                retryProperties.outbox().maxAttempts(),
                classifier.getRetryableExceptions(),
                true,
                false
        ));

        EqualJitterExponentialBackOffPolicy backOffPolicy = new EqualJitterExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(retryProperties.outbox().initialDelayMs());
        backOffPolicy.setMultiplier(retryProperties.outbox().multiplier());
        backOffPolicy.setMaxInterval(retryProperties.outbox().maxDelayMs());
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }

    @Test
    void processing_timeout_복구시_batch_size를_전달하고_polling_hint를_발행한다() {
        // given
        given(slackNotificationOutboxRepository.recoverTimeoutProcessing(
                any(),
                any(),
                anyString(),
                eq(2),
                eq(OUTBOX_RECOVERY_BATCH_SIZE)
        )).willReturn(3);

        // when
        slackNotificationOutboxProcessor.recoverTimeoutProcessing();

        // then
        verify(slackNotificationOutboxRepository).recoverTimeoutProcessing(
                any(),
                any(),
                anyString(),
                eq(2),
                eq(OUTBOX_RECOVERY_BATCH_SIZE)
        );
        verify(pollingHintPublisher).publish(PollingHintTarget.INTERACTION_OUTBOX);
    }

    @Test
    void processing_timeout_복구건이_없으면_polling_hint를_발행하지_않는다() {
        // given
        given(slackNotificationOutboxRepository.recoverTimeoutProcessing(
                any(),
                any(),
                anyString(),
                eq(2),
                eq(OUTBOX_RECOVERY_BATCH_SIZE)
        )).willReturn(0);

        // when
        slackNotificationOutboxProcessor.recoverTimeoutProcessing();

        // then
        verify(pollingHintPublisher, never()).publish(PollingHintTarget.INTERACTION_OUTBOX);
    }

    @Test
    void markProcessing_선점에_실패하면_처리를_건너뛴다() {
        // given
        given(slackNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.empty());

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        verify(slackNotificationOutboxRepository, never()).findById(any());
        verify(slackNotificationOutboxRepository, never()).save(any());
        verify(workspaceRepository, never()).findByTeamId(anyString());
        verify(notificationTransportApiClient, never()).sendMessage(anyString(), anyString(), anyString());
    }

    @Test
    void 지원하지_않는_message_type이면_processPending에서_FAILED_마킹으로_처리한다() {
        // given
        SlackNotificationOutbox outbox = mock(SlackNotificationOutbox.class);
        given(outbox.getId()).willReturn(10L);
        given(outbox.getMessageType()).willReturn(null);
        given(outbox.getTeamId()).willReturn("T1");
        given(outbox.getProcessingStartedAt()).willReturn(CLAIMED_PROCESSING_STARTED_AT);

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));

        given(slackNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.empty());
        given(slackNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(outbox));

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(outbox).markFailed(any(), reasonCaptor.capture(), eq(SlackInteractionFailureType.BUSINESS_INVARIANT));
        verify(slackNotificationOutboxRepository, times(1))
                .saveIfProcessingLeaseMatched(eq(outbox), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
        verify(notificationTransportApiClient, never()).sendMessage(anyString(), anyString(), anyString());

        assertThat(reasonCaptor.getValue()).isEqualTo("지원하지 않는 메시지 타입입니다: null");
    }

    @Test
    void markProcessing_성공후_outbox_재조회에_실패하면_추가_처리없이_종료한다() {
        // given
        given(slackNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.empty());
        given(slackNotificationOutboxRepository.findById(10L)).willReturn(Optional.empty());

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        verify(slackNotificationOutboxRepository, never()).saveIfProcessingLeaseMatched(any(), any(), any());
        verify(workspaceRepository, never()).findByTeamId(anyString());
        verify(notificationTransportApiClient, never()).sendMessage(anyString(), anyString(), anyString());
    }

    @Test
    void CHANNEL_TEXT_정상처리시_전송후_SENT로_저장된다() {
        // given
        SlackNotificationOutbox outbox = mock(SlackNotificationOutbox.class);
        given(outbox.getMessageType()).willReturn(SlackNotificationOutboxMessageType.CHANNEL_TEXT);
        given(outbox.getTeamId()).willReturn("T1");
        given(outbox.getChannelId()).willReturn("C1");
        given(outbox.getText()).willReturn("hello");
        given(outbox.getProcessingStartedAt()).willReturn(CLAIMED_PROCESSING_STARTED_AT);

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));
        given(slackNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.empty());
        given(slackNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(outbox));

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        verify(notificationTransportApiClient).sendMessage("xoxb-test-token", "C1", "hello");
        verify(outbox).markSent(any());
        verify(slackNotificationOutboxRepository)
                .saveIfProcessingLeaseMatched(eq(outbox), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
        verify(outbox, never()).markFailed(any(), anyString(), any());
    }

    @Test
    void SENT_상태_저장이_lease_mismatch면_실패상태로_재마킹하지_않는다() {
        // given
        SlackNotificationOutbox outbox = mock(SlackNotificationOutbox.class);
        given(outbox.getId()).willReturn(10L);
        given(outbox.getMessageType()).willReturn(SlackNotificationOutboxMessageType.CHANNEL_TEXT);
        given(outbox.getTeamId()).willReturn("T1");
        given(outbox.getChannelId()).willReturn("C1");
        given(outbox.getText()).willReturn("hello");
        given(outbox.getProcessingStartedAt()).willReturn(CLAIMED_PROCESSING_STARTED_AT);

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));
        given(slackNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.empty());
        given(slackNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(outbox));
        given(slackNotificationOutboxRepository.saveIfProcessingLeaseMatched(outbox, null, CLAIMED_PROCESSING_STARTED_AT))
                .willReturn(false);

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        verify(notificationTransportApiClient).sendMessage("xoxb-test-token", "C1", "hello");
        verify(outbox).markSent(any());
        verify(outbox, never()).markFailed(any(), anyString(), any());
    }

    @Test
    void SENT_상태_저장에_실패해도_실패상태로_재마킹하지_않는다() {
        // given
        SlackNotificationOutbox outbox = mock(SlackNotificationOutbox.class);
        given(outbox.getId()).willReturn(10L);
        given(outbox.getMessageType()).willReturn(SlackNotificationOutboxMessageType.CHANNEL_TEXT);
        given(outbox.getTeamId()).willReturn("T1");
        given(outbox.getChannelId()).willReturn("C1");
        given(outbox.getText()).willReturn("hello");
        given(outbox.getProcessingStartedAt()).willReturn(CLAIMED_PROCESSING_STARTED_AT);

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));
        given(slackNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.empty());
        given(slackNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(outbox));
        doThrow(new RuntimeException("db failure"))
                .when(slackNotificationOutboxRepository)
                .saveIfProcessingLeaseMatched(eq(outbox), any(), eq(CLAIMED_PROCESSING_STARTED_AT));

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        verify(notificationTransportApiClient).sendMessage("xoxb-test-token", "C1", "hello");
        verify(outbox).markSent(any());
        verify(outbox, never()).markFailed(any(), anyString(), any());
    }

    @Test
    void 예외메시지가_null이면_unknown_failure로_저장된다() {
        // given
        SlackNotificationOutbox outbox = mock(SlackNotificationOutbox.class);
        given(outbox.getId()).willReturn(10L);
        given(outbox.getMessageType()).willReturn(SlackNotificationOutboxMessageType.CHANNEL_TEXT);
        given(outbox.getTeamId()).willReturn("T1");
        given(outbox.getChannelId()).willReturn("C1");
        given(outbox.getText()).willReturn("hello");
        given(outbox.getProcessingStartedAt()).willReturn(CLAIMED_PROCESSING_STARTED_AT);

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));
        given(slackNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.empty());
        given(slackNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(outbox));

        RuntimeException noMessageException = new RuntimeException();
        willThrow(noMessageException)
                .given(notificationTransportApiClient)
                .sendMessage("xoxb-test-token", "C1", "hello");

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(outbox).markFailed(any(), reasonCaptor.capture(), eq(SlackInteractionFailureType.BUSINESS_INVARIANT));
        verify(slackNotificationOutboxRepository)
                .saveIfProcessingLeaseMatched(eq(outbox), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
        assertThat(reasonCaptor.getValue()).isEqualTo("unknown failure");
    }

    @Test
    void 실패상태_저장이_lease_mismatch여도_예외없이_종료한다() {
        // given
        SlackNotificationOutbox outbox = mock(SlackNotificationOutbox.class);
        given(outbox.getId()).willReturn(10L);
        given(outbox.getMessageType()).willReturn(SlackNotificationOutboxMessageType.CHANNEL_TEXT);
        given(outbox.getTeamId()).willReturn("T1");
        given(outbox.getChannelId()).willReturn("C1");
        given(outbox.getText()).willReturn("hello");
        given(outbox.getProcessingStartedAt()).willReturn(CLAIMED_PROCESSING_STARTED_AT);

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));
        given(slackNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.empty());
        given(slackNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(outbox));
        willThrow(new RuntimeException("dispatch failure"))
                .given(notificationTransportApiClient)
                .sendMessage("xoxb-test-token", "C1", "hello");
        given(slackNotificationOutboxRepository.saveIfProcessingLeaseMatched(outbox, null, CLAIMED_PROCESSING_STARTED_AT))
                .willReturn(false);

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        verify(outbox).markFailed(any(), anyString(), eq(SlackInteractionFailureType.BUSINESS_INVARIANT));
    }

    @Test
    void 실패상태_저장에_실패해도_다음_outbox_처리를_계속한다() {
        // given
        SlackNotificationOutbox firstOutbox = mock(SlackNotificationOutbox.class);
        SlackNotificationOutbox secondOutbox = mock(SlackNotificationOutbox.class);

        given(firstOutbox.getId()).willReturn(10L);
        given(firstOutbox.getMessageType()).willReturn(SlackNotificationOutboxMessageType.CHANNEL_TEXT);
        given(firstOutbox.getTeamId()).willReturn("T1");
        given(firstOutbox.getChannelId()).willReturn("C1");
        given(firstOutbox.getText()).willReturn("first");
        given(firstOutbox.getProcessingStartedAt()).willReturn(CLAIMED_PROCESSING_STARTED_AT);

        given(secondOutbox.getId()).willReturn(20L);
        given(secondOutbox.getMessageType()).willReturn(SlackNotificationOutboxMessageType.CHANNEL_TEXT);
        given(secondOutbox.getTeamId()).willReturn("T1");
        given(secondOutbox.getChannelId()).willReturn("C2");
        given(secondOutbox.getText()).willReturn("second");
        given(secondOutbox.getProcessingStartedAt()).willReturn(CLAIMED_PROCESSING_STARTED_AT);

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));

        given(slackNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.of(20L), Optional.empty());
        given(slackNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(firstOutbox));
        given(slackNotificationOutboxRepository.findById(20L)).willReturn(Optional.of(secondOutbox));

        willThrow(new RuntimeException("dispatch failure"))
                .given(notificationTransportApiClient)
                .sendMessage("xoxb-test-token", "C1", "first");
        doThrow(new RuntimeException("db failure"))
                .when(slackNotificationOutboxRepository)
                .saveIfProcessingLeaseMatched(eq(firstOutbox), any(), eq(CLAIMED_PROCESSING_STARTED_AT));

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        verify(firstOutbox).markFailed(any(), anyString(), eq(SlackInteractionFailureType.BUSINESS_INVARIANT));
        verify(secondOutbox).markSent(any());
        verify(notificationTransportApiClient).sendMessage("xoxb-test-token", "C2", "second");
        verify(slackNotificationOutboxRepository)
                .saveIfProcessingLeaseMatched(eq(secondOutbox), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
    }

    @Test
    void 첫_pending_처리_실패가_다음_pending_처리를_막지_않는다() {
        // given
        SlackNotificationOutbox firstOutbox = mock(SlackNotificationOutbox.class);
        SlackNotificationOutbox secondOutbox = mock(SlackNotificationOutbox.class);

        given(firstOutbox.getId()).willReturn(10L);
        given(firstOutbox.getMessageType()).willReturn(SlackNotificationOutboxMessageType.CHANNEL_TEXT);
        given(firstOutbox.getTeamId()).willReturn("T1");
        given(firstOutbox.getChannelId()).willReturn("C1");
        given(firstOutbox.getText()).willReturn("first");
        given(firstOutbox.getProcessingStartedAt()).willReturn(CLAIMED_PROCESSING_STARTED_AT);

        given(secondOutbox.getMessageType()).willReturn(SlackNotificationOutboxMessageType.CHANNEL_TEXT);
        given(secondOutbox.getTeamId()).willReturn("T1");
        given(secondOutbox.getChannelId()).willReturn("C2");
        given(secondOutbox.getText()).willReturn("second");
        given(secondOutbox.getProcessingStartedAt()).willReturn(CLAIMED_PROCESSING_STARTED_AT);

        Workspace workspace = mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));

        given(slackNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.of(20L), Optional.empty());
        given(slackNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(firstOutbox));
        given(slackNotificationOutboxRepository.findById(20L)).willReturn(Optional.of(secondOutbox));

        willThrow(new RuntimeException("first failed"))
                .given(notificationTransportApiClient)
                .sendMessage("xoxb-test-token", "C1", "first");

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        verify(firstOutbox).markFailed(any(), anyString(), eq(SlackInteractionFailureType.BUSINESS_INVARIANT));
        verify(firstOutbox, never()).markSent(any());
        verify(secondOutbox).markSent(any());
        verify(secondOutbox, never()).markFailed(any(), anyString(), any());
        verify(notificationTransportApiClient).sendMessage("xoxb-test-token", "C1", "first");
        verify(notificationTransportApiClient).sendMessage("xoxb-test-token", "C2", "second");
        verify(slackNotificationOutboxRepository, times(2))
                .saveIfProcessingLeaseMatched(any(), any(), eq(CLAIMED_PROCESSING_STARTED_AT));
    }

    @Test
    void claim_lease가_달라지면_전송을_건너뛴다() {
        // given
        SlackNotificationOutbox outbox = mock(SlackNotificationOutbox.class);
        given(outbox.getId()).willReturn(10L);
        given(outbox.getProcessingStartedAt()).willReturn(Instant.parse("2026-03-24T00:00:01Z"));
        given(slackNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.empty());
        given(slackNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(outbox));

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        verify(notificationTransportApiClient, never()).sendMessage(anyString(), anyString(), anyString());
        verify(slackNotificationOutboxRepository, never())
                .saveIfProcessingLeaseMatched(any(), any(), any());
    }

    @Test
    void lease_연장에_실패하면_전송을_건너뛴다() {
        // given
        SlackNotificationOutbox outbox = mock(SlackNotificationOutbox.class);
        given(outbox.getId()).willReturn(10L);
        given(outbox.getProcessingStartedAt()).willReturn(CLAIMED_PROCESSING_STARTED_AT);
        given(slackNotificationOutboxRepository.claimNextId(any(), anyCollection()))
                .willReturn(Optional.of(10L), Optional.empty());
        given(slackNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(outbox));
        given(slackNotificationOutboxRepository.renewProcessingLease(any(), any(), any())).willReturn(false);

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        verify(notificationTransportApiClient, never()).sendMessage(anyString(), anyString(), anyString());
        verify(outbox, never()).markSent(any());
        verify(outbox, never()).markFailed(any(), anyString(), any());
        verify(slackNotificationOutboxRepository, never()).saveIfProcessingLeaseMatched(any(), any(), any());
    }
}
