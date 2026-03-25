package com.slack.bot.application.interaction.box.in;

import static org.mockito.Mockito.verify;

import com.slack.bot.application.worker.PollingHintEvent;
import com.slack.bot.application.worker.PollingHintTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackBlockActionInboxWorkerTest {

    @Mock
    SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    SlackBlockActionInboxWorker slackBlockActionInboxWorker;

    @BeforeEach
    void setUp() {
        slackBlockActionInboxWorker = new SlackBlockActionInboxWorker(
                slackInteractionInboxProcessor,
                1_000L,
                30_000L
        );
    }

    @Test
    void 워커는_기본_배치_크기로_block_actions_인박스를_처리한다() {
        // when
        slackBlockActionInboxWorker.processBlockActionInbox();

        // then
        verify(slackInteractionInboxProcessor).processPendingBlockActions(30);
    }

    @Test
    void wake_up_hint와_stop은_예외없이_동작한다() {
        slackBlockActionInboxWorker.wakeUp(new PollingHintEvent(PollingHintTarget.BLOCK_ACTION_INBOX));
        slackBlockActionInboxWorker.stop();
    }
}
