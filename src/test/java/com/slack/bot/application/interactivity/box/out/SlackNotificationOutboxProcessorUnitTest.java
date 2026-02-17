package com.slack.bot.application.interactivity.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interactivity.box.InteractivityFailureReasonTruncator;
import com.slack.bot.application.interactivity.box.retry.InteractivityRetryExceptionClassifier;
import com.slack.bot.global.config.properties.InteractivityRetryProperties;
import com.slack.bot.infrastructure.interaction.box.SlackInteractivityFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.repository.SlackNotificationOutboxRepository;
import com.slack.bot.infrastructure.interaction.client.NotificationTransportApiClient;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
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
        InteractivityRetryProperties retryProperties = new InteractivityRetryProperties(
                new InteractivityRetryProperties.Retry(2, 100, 2.0, 1000),
                new InteractivityRetryProperties.Retry(2, 100, 2.0, 1000)
        );

        slackNotificationOutboxProcessor = new SlackNotificationOutboxProcessor(
                Clock.systemUTC(),
                new ObjectMapper(),
                new RetryTemplate(),
                notificationTransportApiClient,
                slackNotificationOutboxRepository,
                retryProperties,
                new InteractivityFailureReasonTruncator(),
                InteractivityRetryExceptionClassifier.create(),
                workspaceRepository
        );
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

        given(slackNotificationOutboxRepository.findPending(10)).willReturn(List.of(outbox));
        given(slackNotificationOutboxRepository.markProcessingIfPending(eq(10L), any())).willReturn(true);
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

        given(slackNotificationOutboxRepository.findPending(10)).willReturn(List.of(pending));
        given(slackNotificationOutboxRepository.markProcessingIfPending(eq(10L), any())).willReturn(true);
        given(slackNotificationOutboxRepository.findById(10L)).willReturn(Optional.empty());

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        verify(slackNotificationOutboxRepository, never()).save(any());
        verify(workspaceRepository, never()).findByTeamId(anyString());
        verify(notificationTransportApiClient, never()).sendMessage(anyString(), anyString(), anyString());
    }
}
