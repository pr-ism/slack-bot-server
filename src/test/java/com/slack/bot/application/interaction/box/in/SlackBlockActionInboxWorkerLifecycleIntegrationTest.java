package com.slack.bot.application.interaction.box.in;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.slack.bot.application.WorkerIntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@WorkerIntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackBlockActionInboxWorkerLifecycleIntegrationTest {

    @Autowired
    SlackBlockActionInboxWorker slackBlockActionInboxWorker;

    @Autowired
    SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    @AfterEach
    void tearDown() {
        slackBlockActionInboxWorker.stop();
    }

    @Test
    void stop후_restart해도_worker가_다시_poll한다() {
        // given
        stubEmptyPoll();

        // when
        slackBlockActionInboxWorker.start();

        // then
        await().atMost(Duration.ofSeconds(3L)).untilAsserted(() ->
                verify(slackInteractionInboxProcessor, atLeastOnce()).processPendingBlockActions(anyInt())
        );

        // when
        slackBlockActionInboxWorker.stop();
        await().atMost(Duration.ofSeconds(1L)).until(() -> {
            try {
                slackBlockActionInboxWorker.start();
                return true;
            } catch (IllegalStateException e) {
                return false;
            }
        });

        // then
        await().atMost(Duration.ofSeconds(3L)).untilAsserted(() ->
                verify(slackInteractionInboxProcessor, atLeast(2)).processPendingBlockActions(anyInt())
        );
    }

    private void stubEmptyPoll() {
        doReturn(0).when(slackInteractionInboxProcessor).processPendingBlockActions(anyInt());
    }
}
