package com.slack.bot.application.worker;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interaction.block.BlockActionType;
import com.slack.bot.application.interaction.box.in.SlackInteractionInboxProcessor;
import com.slack.bot.application.interaction.client.NotificationApiClient;
import com.slack.bot.domain.reservation.ReservationStatus;
import com.slack.bot.domain.reservation.repository.ReviewReservationRepository;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxStatus;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import com.slack.bot.infrastructure.interaction.box.persistence.in.JpaSlackInteractionInboxRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AdaptivePollingRunnerIntegrationTest {

    @Autowired
    SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    @Autowired
    NotificationApiClient notificationApiClient;

    @Autowired
    ReviewReservationRepository reviewReservationRepository;

    @Autowired
    JpaSlackInteractionInboxRepository jpaSlackInteractionInboxRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interaction/active_review_reservation_t1_project_123_u1.sql"
    })
    void db_polling만으로_pending_block_action_inbox를_eventually_처리한다() {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1"))
                .willReturn("D-REVIEWER");
        AtomicInteger pollCount = new AtomicInteger();
        AdaptivePollingRunner adaptivePollingRunner = new AdaptivePollingRunner(
                "db-polling-eventual",
                () -> {
                    pollCount.incrementAndGet();
                    return slackInteractionInboxProcessor.processPendingBlockActions(10);
                },
                new AdaptivePollingBackoff(
                        Duration.ofMillis(100L),
                        Duration.ofMillis(100L),
                        boundExclusive -> boundExclusive - 1L
                ),
                new AdaptivePollingRunner.MonitorPollingSleeper(),
                Duration.ofMillis(100L)
        );

        try {
            adaptivePollingRunner.start();
            await().atMost(Duration.ofSeconds(3L)).untilAsserted(() ->
                    assertThat(pollCount.get()).isGreaterThanOrEqualTo(1)
            );
            SlackInteractionInbox inbox = savePendingBlockActionInbox(cancelReservationPayload("100"));

            // when
            await().atMost(Duration.ofSeconds(3L)).untilAsserted(() -> {
                SlackInteractionInbox actualInbox = jpaSlackInteractionInboxRepository.findById(inbox.getId()).orElseThrow();

                // then
                assertAll(
                        () -> assertThat(actualInbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSED),
                        () -> assertThat(actualInbox.getProcessingAttempt()).isEqualTo(1),
                        () -> assertThat(reviewReservationRepository.findById(100L))
                                .isPresent()
                                .get()
                                .extracting(reservation -> reservation.getStatus())
                                .isEqualTo(ReservationStatus.CANCELLED),
                        () -> assertThat(pollCount.get()).isGreaterThanOrEqualTo(2)
                );
            });
        } finally {
            adaptivePollingRunner.stop();
        }
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interaction/active_review_reservation_t1_project_123_u1.sql"
    })
    void sleep중_wake_up_hint가_오면_db_polling을_조기_재개한다() throws InterruptedException {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1"))
                .willReturn("D-REVIEWER");
        AtomicInteger pollCount = new AtomicInteger();
        CountDownLatch enteredSleep = new CountDownLatch(1);
        AdaptivePollingRunner.PollingSleeper pollingSleeper = new AdaptivePollingRunner.PollingSleeper() {
            private final AdaptivePollingRunner.MonitorPollingSleeper delegate =
                    new AdaptivePollingRunner.MonitorPollingSleeper();

            @Override
            public AdaptivePollingRunner.PollingSleepResult sleep(Duration delay) throws InterruptedException {
                enteredSleep.countDown();
                return delegate.sleep(delay);
            }

            @Override
            public void wakeUp() {
                delegate.wakeUp();
            }
        };
        AdaptivePollingRunner adaptivePollingRunner = new AdaptivePollingRunner(
                "db-polling-wakeup",
                () -> {
                    pollCount.incrementAndGet();
                    return slackInteractionInboxProcessor.processPendingBlockActions(10);
                },
                new AdaptivePollingBackoff(
                        Duration.ofSeconds(5L),
                        Duration.ofSeconds(5L),
                        boundExclusive -> boundExclusive - 1L
                ),
                pollingSleeper,
                Duration.ofSeconds(5L)
        );

        try {
            adaptivePollingRunner.start();
            assertThat(enteredSleep.await(3L, TimeUnit.SECONDS)).isTrue();
            SlackInteractionInbox inbox = savePendingBlockActionInbox(cancelReservationPayload("100"));
            Instant wakeUpRequestedAt = Instant.now();

            // when
            adaptivePollingRunner.wakeUp();

            // then
            await().atMost(Duration.ofSeconds(2L)).untilAsserted(() -> {
                SlackInteractionInbox actualInbox = jpaSlackInteractionInboxRepository.findById(inbox.getId()).orElseThrow();

                assertAll(
                        () -> assertThat(actualInbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSED),
                        () -> assertThat(actualInbox.getProcessedAt()).isAfterOrEqualTo(wakeUpRequestedAt),
                        () -> assertThat(Duration.between(wakeUpRequestedAt, actualInbox.getProcessedAt()))
                                .isLessThan(Duration.ofSeconds(2L)),
                        () -> assertThat(reviewReservationRepository.findById(100L))
                                .isPresent()
                                .get()
                                .extracting(reservation -> reservation.getStatus())
                                .isEqualTo(ReservationStatus.CANCELLED),
                        () -> assertThat(pollCount.get()).isGreaterThanOrEqualTo(2)
                );
            });
        } finally {
            adaptivePollingRunner.stop();
        }
    }

    private SlackInteractionInbox savePendingBlockActionInbox(ObjectNode payload) {
        return jpaSlackInteractionInboxRepository.save(
                SlackInteractionInbox.pending(
                        SlackInteractionInboxType.BLOCK_ACTIONS,
                        "adaptive-polling-" + System.nanoTime(),
                        payload.toString()
                )
        );
    }

    private ObjectNode cancelReservationPayload(String reservationId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("team", objectMapper.createObjectNode().put("id", "T1"));
        payload.set("channel", objectMapper.createObjectNode().put("id", "C1"));
        payload.set("user", objectMapper.createObjectNode().put("id", "U1"));
        payload.set("actions", actions(BlockActionType.CANCEL_REVIEW_RESERVATION.value(), reservationId));
        return payload;
    }

    private ArrayNode actions(String actionId, String value) {
        ArrayNode actions = objectMapper.createArrayNode();
        actions.add(objectMapper.createObjectNode()
                                .put("action_id", actionId)
                                .put("value", value));
        return actions;
    }
}
