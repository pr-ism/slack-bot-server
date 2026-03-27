package com.slack.bot.application.interaction.box.out;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interaction.client.exception.SlackBotMessageDispatchException;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.infrastructure.common.FailureSnapshotDefaults;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxHistory;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStatus;
import com.slack.bot.infrastructure.interaction.box.out.repository.SlackNotificationOutboxRepository;
import com.slack.bot.infrastructure.interaction.box.persistence.out.JpaSlackNotificationOutboxHistoryRepository;
import com.slack.bot.infrastructure.interaction.client.NotificationTransportApiClient;
import com.slack.bot.infrastructure.workspace.persistence.JpaWorkspaceRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackNotificationOutboxProcessorTest {

    @Autowired
    SlackNotificationOutboxProcessor slackNotificationOutboxProcessor;

    @Autowired
    SlackNotificationOutboxRepository slackNotificationOutboxRepository;

    @Autowired
    NotificationTransportApiClient notificationTransportApiClient;

    @Autowired
    JpaWorkspaceRepository jpaWorkspaceRepository;

    @Autowired
    JpaSlackNotificationOutboxHistoryRepository jpaSlackNotificationOutboxHistoryRepository;

    @Autowired
    Clock clock;

    @Test
    @Sql(scripts = "/sql/fixtures/box/out/pending_outbox.sql")
    void EPHEMERAL_TEXT_outbox는_한번만_전송되고_SENT로_마킹된다() {
        // given
        SlackNotificationOutbox pending = slackNotificationOutboxRepository.findById(100L)
                                                                           .orElseThrow();

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            SlackNotificationOutbox actual = slackNotificationOutboxRepository.findById(pending.getId())
                                                                              .orElseThrow();

            verify(notificationTransportApiClient).sendEphemeralMessage("xoxb-test-token", "C1", "U1", "hello-100");
            assertThat(actual.getStatus()).isEqualTo(SlackNotificationOutboxStatus.SENT);
        });
    }

    @Test
    @Sql(scripts = "/sql/fixtures/box/out/pending_outbox.sql")
    void EPHEMERAL_BLOCKS_outbox는_블록_메시지를_전송한다() {
        // given
        SlackNotificationOutbox pending = slackNotificationOutboxRepository.findById(101L)
                                                                           .orElseThrow();

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            SlackNotificationOutbox actual = slackNotificationOutboxRepository.findById(pending.getId())
                                                                              .orElseThrow();

            verify(notificationTransportApiClient).sendEphemeralBlockMessage(
                    eq("xoxb-test-token"),
                    eq("C1"),
                    eq("U1"),
                    any(JsonNode.class),
                    eq("fallback")
            );
            assertThat(actual.getStatus()).isEqualTo(SlackNotificationOutboxStatus.SENT);
        });
    }

    @Test
    @Sql(scripts = "/sql/fixtures/box/out/pending_outbox.sql")
    void CHANNEL_TEXT_outbox는_채널_텍스트를_전송한다() {
        // given
        SlackNotificationOutbox pending = slackNotificationOutboxRepository.findById(102L)
                                                                           .orElseThrow();

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            SlackNotificationOutbox actual = slackNotificationOutboxRepository.findById(pending.getId())
                                                                              .orElseThrow();

            verify(notificationTransportApiClient).sendMessage("xoxb-test-token", "C1", "hello-102");
            assertThat(actual.getStatus()).isEqualTo(SlackNotificationOutboxStatus.SENT);
        });
    }

    @Test
    @Sql(scripts = "/sql/fixtures/box/out/pending_outbox_channel_text_only.sql")
    void workspace_토큰이_갱신되어도_outbox는_최신_토큰으로_전송한다() {
        // given
        Workspace workspace = jpaWorkspaceRepository.findByTeamId("T1")
                                                    .orElseThrow();
        workspace.reconnect("xoxb-rotated-token", workspace.getBotUserId());
        jpaWorkspaceRepository.save(workspace);

        // when
        slackNotificationOutboxProcessor.processPending(1);

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                verify(notificationTransportApiClient, times(1)).sendMessage("xoxb-rotated-token", "C1", "hello-102")
        );
    }

    @Test
    @Sql(scripts = "/sql/fixtures/box/out/processing_timeout_outbox_channel_text.sql")
    void PROCESSING_타임아웃_outbox는_RETRY_PENDING으로_복구된_후_정상_재처리된다() {
        // when
        slackNotificationOutboxProcessor.recoverTimeoutProcessing();
        slackNotificationOutboxProcessor.processPending(10);

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            SlackNotificationOutbox actual = slackNotificationOutboxRepository.findById(200L)
                                                                              .orElseThrow();

            verify(notificationTransportApiClient).sendMessage("xoxb-test-token", "C1", "hello-timeout-200");
            assertAll(
                    () -> assertThat(actual.getStatus()).isEqualTo(SlackNotificationOutboxStatus.SENT),
                    () -> assertThat(actual.getProcessingAttempt()).isEqualTo(2)
            );
        });
    }

    @Test
    @Sql(scripts = "/sql/fixtures/notification/workspace_t1.sql")
    void PROCESSING_타임아웃_outbox가_최대시도에_도달하면_FAILED_RETRY_EXHAUSTED로_격리된다() {
        // given
        SlackNotificationOutbox timeoutOutbox = SlackNotificationOutbox.builder()
                                                                       .messageType(SlackNotificationOutboxMessageType.CHANNEL_TEXT)
                                                                       .idempotencyKey("OUTBOX-TIMEOUT-POISON-PILL")
                                                                       .teamId("T1")
                                                                       .channelId("C1")
                                                                       .text("hello-timeout-poison-pill")
                                                                       .build();
        Instant base = clock.instant();
        setProcessingState(timeoutOutbox, base.minusSeconds(120), 1);
        timeoutOutbox.markRetryPending(base.minusSeconds(110), "first failure");
        setProcessingState(timeoutOutbox, base.minusSeconds(100), 2);
        SlackNotificationOutbox saved = slackNotificationOutboxRepository.save(timeoutOutbox);

        // when
        slackNotificationOutboxProcessor.recoverTimeoutProcessing();

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            SlackNotificationOutbox actual = slackNotificationOutboxRepository.findById(saved.getId())
                                                                              .orElseThrow();
            assertAll(
                    () -> assertThat(actual.getStatus()).isEqualTo(SlackNotificationOutboxStatus.FAILED),
                    () -> assertThat(actual.getProcessingAttempt()).isEqualTo(2),
                    () -> assertThat(actual.getFailureType()).isEqualTo(SlackInteractionFailureType.RETRY_EXHAUSTED),
                    () -> assertThat(actual.getFailureReason()).isNotBlank()
            );
        });
    }

    @Test
    @Sql(scripts = "/sql/fixtures/box/out/pending_outbox.sql")
    void CHANNEL_BLOCKS_outbox는_채널_블록을_전송한다() {
        // given
        SlackNotificationOutbox pending = slackNotificationOutboxRepository.findById(103L)
                                                                           .orElseThrow();

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            SlackNotificationOutbox actual = slackNotificationOutboxRepository.findById(pending.getId())
                                                                              .orElseThrow();

            verify(notificationTransportApiClient).sendBlockMessage(
                    eq("xoxb-test-token"),
                    eq("C1"),
                    any(JsonNode.class),
                    eq("fallback")
            );
            assertThat(actual.getStatus()).isEqualTo(SlackNotificationOutboxStatus.SENT);
        });
    }

    @Test
    @Sql(scripts = "/sql/fixtures/box/out/pending_outbox.sql")
    void 알림_전송_실패시_outbox는_FAILED로_마킹되고_실패사유는_잘려서_저장된다() {
        // given
        String longMessage = "x".repeat(600);
        willThrow(new SlackBotMessageDispatchException(longMessage))
                .given(notificationTransportApiClient)
                .sendEphemeralMessage("xoxb-test-token", "C1", "U1", "hello-104");
        SlackNotificationOutbox pending = slackNotificationOutboxRepository.findById(104L)
                                                                           .orElseThrow();

        // when
        slackNotificationOutboxProcessor.processPending(10);
        SlackNotificationOutbox actualFirstFailed = slackNotificationOutboxRepository.findById(pending.getId())
                                                                                     .orElseThrow();

        slackNotificationOutboxProcessor.processPending(10);
        SlackNotificationOutbox actualSecondFailed = slackNotificationOutboxRepository.findById(pending.getId())
                                                                                      .orElseThrow();
        List<SlackNotificationOutboxHistory> histories = historiesOf(pending.getId());

        // then
        assertAll(
                () -> assertThat(actualFirstFailed.getStatus()).isEqualTo(SlackNotificationOutboxStatus.RETRY_PENDING),
                () -> assertThat(actualFirstFailed.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actualSecondFailed.getStatus()).isEqualTo(SlackNotificationOutboxStatus.FAILED),
                () -> assertThat(actualSecondFailed.getProcessingAttempt()).isEqualTo(2),
                () -> assertThat(actualSecondFailed.getFailureType()).isEqualTo(SlackInteractionFailureType.RETRY_EXHAUSTED),
                () -> assertThat(actualSecondFailed.getFailureReason()).isNotNull(),
                () -> assertThat(actualSecondFailed.getFailureReason()).hasSize(500),
                () -> assertThat(actualSecondFailed.getFailureReason()).isEqualTo("x".repeat(500)),
                () -> assertThat(histories).hasSize(2),
                () -> assertThat(histories).extracting(history -> history.getStatus())
                        .containsExactly(
                                SlackNotificationOutboxStatus.RETRY_PENDING,
                                SlackNotificationOutboxStatus.FAILED
                        )
        );
    }

    private List<SlackNotificationOutboxHistory> historiesOf(Long outboxId) {
        return jpaSlackNotificationOutboxHistoryRepository.findAll()
                                                          .stream()
                                                          .filter(history -> outboxId.equals(history.getOutboxId()))
                                                          .sorted(Comparator.comparingInt(history -> history.getProcessingAttempt()))
                                                          .toList();
    }

    @Test
    @Sql(scripts = "/sql/fixtures/box/out/pending_outbox.sql")
    void 비즈니스_불변식_실패_outbox는_재시도하지않고_즉시_FAILED로_마킹된다() {
        // given
        SlackNotificationOutbox pending = slackNotificationOutboxRepository.findById(105L)
                                                                           .orElseThrow();

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            SlackNotificationOutbox actual = slackNotificationOutboxRepository.findById(pending.getId())
                                                                              .orElseThrow();

            assertAll(
                    () -> assertThat(actual.getStatus()).isEqualTo(SlackNotificationOutboxStatus.FAILED),
                    () -> assertThat(actual.getProcessingAttempt()).isEqualTo(1),
                    () -> assertThat(actual.getFailureType()).isEqualTo(SlackInteractionFailureType.BUSINESS_INVARIANT)
            );
        });
    }

    @Test
    void workspace가_없는_outbox는_비즈니스_불변식_실패로_FAILED_마킹된다() {
        // given
        SlackNotificationOutbox outbox = SlackNotificationOutbox.builder()
                                                                .messageType(SlackNotificationOutboxMessageType.CHANNEL_TEXT)
                                                                .idempotencyKey("OUTBOX-NO-WORKSPACE")
                                                                .teamId("UNKNOWN-TEAM")
                                                                .channelId("C1")
                                                                .text("hello")
                                                                .build();
        SlackNotificationOutbox pending = slackNotificationOutboxRepository.save(outbox);

        // when
        slackNotificationOutboxProcessor.processPending(10);

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            SlackNotificationOutbox actual = slackNotificationOutboxRepository.findById(pending.getId())
                                                                              .orElseThrow();

            assertAll(
                    () -> assertThat(actual.getStatus()).isEqualTo(SlackNotificationOutboxStatus.FAILED),
                    () -> assertThat(actual.getProcessingAttempt()).isEqualTo(1),
                    () -> assertThat(actual.getFailureType()).isEqualTo(SlackInteractionFailureType.BUSINESS_INVARIANT)
            );
        });
    }

    private void setProcessingState(
            SlackNotificationOutbox outbox,
            Instant processingStartedAt,
            int processingAttempt
    ) {
        ReflectionTestUtils.setField(outbox, "status", SlackNotificationOutboxStatus.PROCESSING);
        ReflectionTestUtils.setField(outbox, "processingStartedAt", processingStartedAt);
        ReflectionTestUtils.setField(outbox, "processingAttempt", processingAttempt);
        ReflectionTestUtils.setField(outbox, "failedAt", FailureSnapshotDefaults.NO_FAILURE_AT);
        ReflectionTestUtils.setField(outbox, "failureReason", FailureSnapshotDefaults.NO_FAILURE_REASON);
        ReflectionTestUtils.setField(outbox, "failureType", SlackInteractionFailureType.NONE);
    }
}
