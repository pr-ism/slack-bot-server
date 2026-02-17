package com.slack.bot.application.interactivity.box.in;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interactivity.BlockActionInteractionService;
import com.slack.bot.application.interactivity.box.InteractivityFailureReasonTruncator;
import com.slack.bot.application.interactivity.box.retry.InteractivityRetryExceptionClassifier;
import com.slack.bot.application.interactivity.view.ViewSubmissionInteractionService;
import com.slack.bot.global.config.properties.InteractivityRetryProperties;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.support.RetryTemplate;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackInteractionInboxEntryProcessorUnitTest {

    @Mock
    BlockActionInteractionService blockActionInteractionService;

    @Mock
    ViewSubmissionInteractionService viewSubmissionInteractionService;

    @Mock
    SlackInteractionInboxRepository slackInteractionInboxRepository;

    SlackInteractionInboxEntryProcessor slackInteractionInboxEntryProcessor;

    @BeforeEach
    void setUp() {
        InteractivityRetryProperties retryProperties = new InteractivityRetryProperties(
                new InteractivityRetryProperties.Retry(2, 100, 2.0, 1000),
                new InteractivityRetryProperties.Retry(2, 100, 2.0, 1000)
        );

        slackInteractionInboxEntryProcessor = new SlackInteractionInboxEntryProcessor(
                Clock.systemUTC(),
                new ObjectMapper(),
                blockActionInteractionService,
                viewSubmissionInteractionService,
                new RetryTemplate(),
                retryProperties,
                new InteractivityFailureReasonTruncator(),
                InteractivityRetryExceptionClassifier.create(),
                slackInteractionInboxRepository
        );
    }

    @Test
    void markProcessing_선점에_실패하면_처리를_건너뛴다() {
        // given
        SlackInteractionInbox pending = org.mockito.Mockito.mock(SlackInteractionInbox.class);
        given(pending.getId()).willReturn(10L);
        given(slackInteractionInboxRepository.markProcessingIfPending(10L)).willReturn(false);

        // when
        slackInteractionInboxEntryProcessor.processBlockAction(pending);

        // then
        verify(slackInteractionInboxRepository, never()).findById(anyLong());
        verify(slackInteractionInboxRepository, never()).save(any());
        verify(blockActionInteractionService, never()).handle(any());
    }

    @Test
    void markProcessing_성공후_inbox_재조회에_실패하면_추가_처리없이_종료한다() {
        // given
        SlackInteractionInbox pending = org.mockito.Mockito.mock(SlackInteractionInbox.class);
        given(pending.getId()).willReturn(10L);
        given(slackInteractionInboxRepository.markProcessingIfPending(10L)).willReturn(true);
        given(slackInteractionInboxRepository.findById(10L)).willReturn(Optional.empty());

        // when
        slackInteractionInboxEntryProcessor.processBlockAction(pending);

        // then
        verify(slackInteractionInboxRepository, never()).save(any());
        verify(blockActionInteractionService, never()).handle(any());
    }
}
