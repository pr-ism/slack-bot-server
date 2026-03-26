package com.slack.bot.application.interaction.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;

import com.slack.bot.application.WorkerIntegrationTest;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
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
        AtomicInteger pollCount = new AtomicInteger();
        stubEmptyPoll(pollCount);

        // when
        slackBlockActionInboxWorker.start();

        // then
        await().atMost(Duration.ofSeconds(3L)).untilAsserted(() ->
                assertThat(pollCount.get()).isPositive()
        );
        int beforeRestart = pollCount.get();

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
                assertThat(pollCount.get()).isGreaterThan(beforeRestart)
        );
    }

    private void stubEmptyPoll(AtomicInteger pollCount) {
        doAnswer(invocation -> {
            pollCount.incrementAndGet();
            return 0;
        }).when(slackInteractionInboxProcessor).processPendingBlockActions(anyInt());
    }
}
