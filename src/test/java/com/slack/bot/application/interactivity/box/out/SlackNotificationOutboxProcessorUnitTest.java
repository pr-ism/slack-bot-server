package com.slack.bot.application.interactivity.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interactivity.box.BoxFailureReasonTruncator;
import com.slack.bot.application.interactivity.box.retry.InteractionRetryExceptionClassifier;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
import com.slack.bot.global.config.properties.InteractionWorkerProperties;
import com.slack.bot.infrastructure.interaction.box.SlackInteractivityFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import com.slack.bot.infrastructure.interaction.box.out.repository.SlackNotificationOutboxRepository;
import com.slack.bot.infrastructure.interaction.client.NotificationTransportApiClient;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackNotificationOutboxProcessorUnitTest {

    @Mock
    NotificationTransportApiClient notificationTransportApiClient;

    @Mock
    SlackNotificationOutboxRepository slackNotificationOutboxRepository;

    @Mock
    WorkspaceRepository workspaceRepository;

    SlackNotificationOutboxProcessor slackNotificationOutboxProcessor;

    @BeforeEach
    void setUp() {
        InteractionRetryProperties retryProperties = new InteractionRetryProperties(
                new InteractionRetryProperties.Retry(2, 100, 2.0, 1000),
                new InteractionRetryProperties.Retry(2, 100, 2.0, 1000)
        );
        InteractionRetryExceptionClassifier classifier = InteractionRetryExceptionClassifier.create();

        slackNotificationOutboxProcessor = new SlackNotificationOutboxProcessor(
                Clock.systemUTC(),
                new ObjectMapper(),
                createOutboxRetryTemplate(retryProperties, classifier),
                workspaceRepository,
                new BoxFailureReasonTruncator(),
                retryProperties,
                new InteractionWorkerProperties(),
                notificationTransportApiClient,
                slackNotificationOutboxRepository,
                classifier
        );
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

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(retryProperties.outbox().initialDelayMs());
        backOffPolicy.setMultiplier(retryProperties.outbox().multiplier());
        backOffPolicy.setMaxInterval(retryProperties.outbox().maxDelayMs());
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }

    @Test
    void markProcessing_선점에_실패하면_처리를_건너뛴다() {
        // given
        SlackNotificationOutbox pending = org.mockito.Mockito.mock(SlackNotificationOutbox.class);
        given(pending.getId()).willReturn(10L);
        given(slackNotificationOutboxRepository.findClaimable(10)).willReturn(List.of(pending));
        given(slackNotificationOutboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(false);

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
        SlackNotificationOutbox outbox = org.mockito.Mockito.mock(SlackNotificationOutbox.class);
        given(outbox.getId()).willReturn(10L);
        given(outbox.getMessageType()).willReturn(null);
        given(outbox.getTeamId()).willReturn("T1");

        Workspace workspace = org.mockito.Mockito.mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));

        given(slackNotificationOutboxRepository.findClaimable(10)).willReturn(List.of(outbox));
        given(slackNotificationOutboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(true);
        given(slackNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(outbox));

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(outbox).markFailed(any(), reasonCaptor.capture(), eq(SlackInteractivityFailureType.BUSINESS_INVARIANT));
        verify(slackNotificationOutboxRepository, times(1)).save(outbox);
        verify(notificationTransportApiClient, never()).sendMessage(anyString(), anyString(), anyString());

        assertThat(reasonCaptor.getValue()).isEqualTo("지원하지 않는 메시지 타입입니다: null");
    }

    @Test
    void markProcessing_성공후_outbox_재조회에_실패하면_추가_처리없이_종료한다() {
        // given
        SlackNotificationOutbox pending = org.mockito.Mockito.mock(SlackNotificationOutbox.class);
        given(pending.getId()).willReturn(10L);

        given(slackNotificationOutboxRepository.findClaimable(10)).willReturn(List.of(pending));
        given(slackNotificationOutboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(true);
        given(slackNotificationOutboxRepository.findById(10L)).willReturn(Optional.empty());

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        verify(slackNotificationOutboxRepository, never()).save(any());
        verify(workspaceRepository, never()).findByTeamId(anyString());
        verify(notificationTransportApiClient, never()).sendMessage(anyString(), anyString(), anyString());
    }

    @Test
    void CHANNEL_TEXT_정상처리시_전송후_SENT로_저장된다() {
        // given
        SlackNotificationOutbox outbox = org.mockito.Mockito.mock(SlackNotificationOutbox.class);
        given(outbox.getId()).willReturn(10L);
        given(outbox.getMessageType()).willReturn(SlackNotificationOutboxMessageType.CHANNEL_TEXT);
        given(outbox.getTeamId()).willReturn("T1");
        given(outbox.getChannelId()).willReturn("C1");
        given(outbox.getText()).willReturn("hello");

        Workspace workspace = org.mockito.Mockito.mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));
        given(slackNotificationOutboxRepository.findClaimable(10)).willReturn(List.of(outbox));
        given(slackNotificationOutboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(true);
        given(slackNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(outbox));

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        verify(notificationTransportApiClient).sendMessage("xoxb-test-token", "C1", "hello");
        verify(outbox).markSent(any());
        verify(slackNotificationOutboxRepository).save(outbox);
        verify(outbox, never()).markFailed(any(), anyString(), any());
    }

    @Test
    void 예외메시지가_null이면_unknown_failure로_저장된다() {
        // given
        SlackNotificationOutbox outbox = org.mockito.Mockito.mock(SlackNotificationOutbox.class);
        given(outbox.getId()).willReturn(10L);
        given(outbox.getMessageType()).willReturn(SlackNotificationOutboxMessageType.CHANNEL_TEXT);
        given(outbox.getTeamId()).willReturn("T1");
        given(outbox.getChannelId()).willReturn("C1");
        given(outbox.getText()).willReturn("hello");

        Workspace workspace = org.mockito.Mockito.mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));
        given(slackNotificationOutboxRepository.findClaimable(10)).willReturn(List.of(outbox));
        given(slackNotificationOutboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(true);
        given(slackNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(outbox));

        RuntimeException noMessageException = new RuntimeException();
        willThrow(noMessageException)
                .given(notificationTransportApiClient)
                .sendMessage("xoxb-test-token", "C1", "hello");

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(outbox).markFailed(any(), reasonCaptor.capture(), eq(SlackInteractivityFailureType.BUSINESS_INVARIANT));
        verify(slackNotificationOutboxRepository).save(outbox);
        assertThat(reasonCaptor.getValue()).isEqualTo("unknown failure");
    }

    @Test
    void 첫_pending_처리_실패가_다음_pending_처리를_막지_않는다() {
        // given
        SlackNotificationOutbox firstPending = org.mockito.Mockito.mock(SlackNotificationOutbox.class);
        SlackNotificationOutbox firstOutbox = org.mockito.Mockito.mock(SlackNotificationOutbox.class);
        SlackNotificationOutbox secondPending = org.mockito.Mockito.mock(SlackNotificationOutbox.class);
        SlackNotificationOutbox secondOutbox = org.mockito.Mockito.mock(SlackNotificationOutbox.class);

        given(firstPending.getId()).willReturn(10L);
        given(secondPending.getId()).willReturn(20L);

        given(firstOutbox.getId()).willReturn(10L);
        given(firstOutbox.getMessageType()).willReturn(SlackNotificationOutboxMessageType.CHANNEL_TEXT);
        given(firstOutbox.getTeamId()).willReturn("T1");
        given(firstOutbox.getChannelId()).willReturn("C1");
        given(firstOutbox.getText()).willReturn("first");

        given(secondOutbox.getMessageType()).willReturn(SlackNotificationOutboxMessageType.CHANNEL_TEXT);
        given(secondOutbox.getTeamId()).willReturn("T1");
        given(secondOutbox.getChannelId()).willReturn("C2");
        given(secondOutbox.getText()).willReturn("second");

        Workspace workspace = org.mockito.Mockito.mock(Workspace.class);
        given(workspace.getAccessToken()).willReturn("xoxb-test-token");
        given(workspaceRepository.findByTeamId("T1")).willReturn(Optional.of(workspace));

        given(slackNotificationOutboxRepository.findClaimable(10)).willReturn(List.of(firstPending, secondPending));
        given(slackNotificationOutboxRepository.markProcessingIfClaimable(eq(10L), any())).willReturn(true);
        given(slackNotificationOutboxRepository.markProcessingIfClaimable(eq(20L), any())).willReturn(true);
        given(slackNotificationOutboxRepository.findById(10L)).willReturn(Optional.of(firstOutbox));
        given(slackNotificationOutboxRepository.findById(20L)).willReturn(Optional.of(secondOutbox));

        willThrow(new RuntimeException("first failed"))
                .given(notificationTransportApiClient)
                .sendMessage("xoxb-test-token", "C1", "first");

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        verify(firstOutbox).markFailed(any(), anyString(), eq(SlackInteractivityFailureType.BUSINESS_INVARIANT));
        verify(firstOutbox, never()).markSent(any());
        verify(secondOutbox).markSent(any());
        verify(secondOutbox, never()).markFailed(any(), anyString(), any());
        verify(notificationTransportApiClient).sendMessage("xoxb-test-token", "C1", "first");
        verify(notificationTransportApiClient).sendMessage("xoxb-test-token", "C2", "second");
        verify(slackNotificationOutboxRepository, times(2)).save(any());
    }
}
