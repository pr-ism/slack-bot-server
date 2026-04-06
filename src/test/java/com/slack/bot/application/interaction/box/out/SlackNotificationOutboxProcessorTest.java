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
import com.slack.bot.application.worker.PollingHintPublisher;
import com.slack.bot.application.worker.PollingHintTarget;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxHistory;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxId;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
@MockitoSpyBean(types = PollingHintPublisher.class)
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

    @Autowired
    PollingHintPublisher pollingHintPublisher;

    @Autowired
    NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

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
            List<SlackNotificationOutboxHistory> actualHistories = historiesOf(200L);

            verify(notificationTransportApiClient).sendMessage("xoxb-test-token", "C1", "hello-timeout-200");
            verify(pollingHintPublisher).publish(PollingHintTarget.INTERACTION_OUTBOX);
            assertAll(
                    () -> assertThat(actual.getStatus()).isEqualTo(SlackNotificationOutboxStatus.SENT),
                    () -> assertThat(actual.getProcessingAttempt()).isEqualTo(2),
                    () -> assertThat(actualHistories).hasSize(2),
                    () -> assertThat(actualHistories.getFirst().getFailure().type()).isEqualTo(
                            SlackInteractionFailureType.PROCESSING_TIMEOUT
                    )
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
                    () -> assertThat(actual.getFailure().type()).isEqualTo(SlackInteractionFailureType.RETRY_EXHAUSTED),
                    () -> assertThat(actual.getFailure().reason()).isNotBlank()
            );
        });
    }

    @Test
    @Sql(scripts = "/sql/fixtures/notification/workspace_t1.sql")
    void PROCESSING_타임아웃_outbox_복구는_배치_크기만큼만_처리한다() {
        // given
        Instant base = clock.instant();
        List<Long> outboxIds = new java.util.ArrayList<>();
        for (int index = 0; index < 101; index++) {
            SlackNotificationOutbox timeoutOutbox = SlackNotificationOutbox.builder()
                                                                           .messageType(SlackNotificationOutboxMessageType.CHANNEL_TEXT)
                                                                           .idempotencyKey("OUTBOX-TIMEOUT-BATCH-" + index)
                                                                           .teamId("T1")
                                                                           .channelId("C1")
                                                                           .text("hello-timeout-batch-" + index)
                                                                           .build();
            setProcessingState(timeoutOutbox, base.minusSeconds(120L + index), 1);
            SlackNotificationOutbox saved = slackNotificationOutboxRepository.save(timeoutOutbox);
            outboxIds.add(saved.getId());
        }

        // when
        int recoveredCount = slackNotificationOutboxProcessor.recoverTimeoutProcessing();

        // then
        List<SlackNotificationOutbox> actualOutboxes = outboxIds.stream()
                                                                .map(outboxId -> slackNotificationOutboxRepository.findById(
                                                                        outboxId
                                                                ).orElseThrow())
                                                                .toList();
        List<SlackNotificationOutboxHistory> actualHistories = jpaSlackNotificationOutboxHistoryRepository.findAllDomains();

        assertAll(
                () -> assertThat(recoveredCount).isEqualTo(100),
                () -> assertThat(actualOutboxes).filteredOn(outbox -> outbox.getStatus() == SlackNotificationOutboxStatus.RETRY_PENDING)
                        .hasSize(100),
                () -> assertThat(actualOutboxes).filteredOn(outbox -> outbox.getStatus() == SlackNotificationOutboxStatus.PROCESSING)
                        .hasSize(1),
                () -> assertThat(actualHistories).hasSize(100),
                () -> assertThat(actualHistories).extracting(history -> history.getStatus())
                        .containsOnly(SlackNotificationOutboxStatus.RETRY_PENDING)
        );
    }

    @Test
    @Sql(scripts = "/sql/fixtures/notification/workspace_t1.sql")
    void PROCESSING_타임아웃_outbox_복구는_재시도_가능과_소진건을_합쳐_배치_크기만큼만_처리한다() {
        // given
        Instant base = clock.instant();
        List<Long> outboxIds = new java.util.ArrayList<>();
        for (int index = 0; index < 50; index++) {
            SlackNotificationOutbox retryableOutbox = SlackNotificationOutbox.builder()
                                                                             .messageType(SlackNotificationOutboxMessageType.CHANNEL_TEXT)
                                                                             .idempotencyKey("OUTBOX-TIMEOUT-MIXED-RETRY-" + index)
                                                                             .teamId("T1")
                                                                             .channelId("C1")
                                                                             .text("hello-timeout-mixed-retry-" + index)
                                                                             .build();
            setProcessingState(retryableOutbox, base.minusSeconds(200L + index), 1);
            SlackNotificationOutbox saved = slackNotificationOutboxRepository.save(retryableOutbox);
            outboxIds.add(saved.getId());
        }
        for (int index = 0; index < 51; index++) {
            SlackNotificationOutbox exhaustedOutbox = SlackNotificationOutbox.builder()
                                                                             .messageType(SlackNotificationOutboxMessageType.CHANNEL_TEXT)
                                                                             .idempotencyKey("OUTBOX-TIMEOUT-MIXED-FAILED-" + index)
                                                                             .teamId("T1")
                                                                             .channelId("C1")
                                                                             .text("hello-timeout-mixed-failed-" + index)
                                                                             .build();
            setProcessingState(exhaustedOutbox, base.minusSeconds(100L + index), 2);
            SlackNotificationOutbox saved = slackNotificationOutboxRepository.save(exhaustedOutbox);
            outboxIds.add(saved.getId());
        }

        // when
        int recoveredCount = slackNotificationOutboxProcessor.recoverTimeoutProcessing();

        // then
        List<SlackNotificationOutbox> actualOutboxes = outboxIds.stream()
                                                                .map(outboxId -> slackNotificationOutboxRepository.findById(
                                                                        outboxId
                                                                ).orElseThrow())
                                                                .toList();
        List<SlackNotificationOutboxHistory> actualHistories = jpaSlackNotificationOutboxHistoryRepository.findAllDomains();

        assertAll(
                () -> assertThat(recoveredCount).isEqualTo(100),
                () -> assertThat(actualOutboxes).filteredOn(outbox -> outbox.getStatus() == SlackNotificationOutboxStatus.RETRY_PENDING)
                        .hasSize(50),
                () -> assertThat(actualOutboxes).filteredOn(outbox -> outbox.getStatus() == SlackNotificationOutboxStatus.FAILED)
                        .hasSize(50),
                () -> assertThat(actualOutboxes).filteredOn(outbox -> outbox.getStatus() == SlackNotificationOutboxStatus.PROCESSING)
                        .hasSize(1),
                () -> assertThat(actualHistories).filteredOn(
                        history -> history.getFailure().type() == SlackInteractionFailureType.PROCESSING_TIMEOUT
                ).hasSize(50),
                () -> assertThat(actualHistories).filteredOn(
                        history -> history.getFailure().type() == SlackInteractionFailureType.RETRY_EXHAUSTED
                ).hasSize(50)
        );
    }

    @Test
    @Sql(scripts = "/sql/fixtures/notification/workspace_t1.sql")
    void 잠겨있는_timeout_outbox_행은_recovery에서_건너뛴다() throws Exception {
        // given
        SlackNotificationOutbox timeoutOutbox = SlackNotificationOutbox.builder()
                                                                       .messageType(SlackNotificationOutboxMessageType.CHANNEL_TEXT)
                                                                       .idempotencyKey("OUTBOX-TIMEOUT-LOCKED")
                                                                       .teamId("T1")
                                                                       .channelId("C1")
                                                                       .text("hello-timeout-locked")
                                                                       .build();
        setProcessingState(timeoutOutbox, Instant.parse("2026-03-24T00:01:00Z"), 1);
        SlackNotificationOutbox saved = slackNotificationOutboxRepository.save(timeoutOutbox);
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<?> lockFuture = executorService.submit(() -> {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    namedParameterJdbcTemplate.query(
                            """
                            SELECT id
                            FROM slack_notification_outbox
                            WHERE id = :outboxId
                            FOR UPDATE
                            """,
                            new MapSqlParameterSource().addValue("outboxId", saved.getId()),
                            (resultSet, rowNum) -> resultSet.getLong(1)
                    );
                    lockAcquired.countDown();
                    awaitLatch(releaseLock);
                });
            });
            awaitLatch(lockAcquired);

            // when
            int skippedCount = slackNotificationOutboxProcessor.recoverTimeoutProcessing();

            // then
            SlackNotificationOutbox lockedOutbox = slackNotificationOutboxRepository.findById(saved.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(skippedCount).isZero(),
                    () -> assertThat(lockedOutbox.getStatus()).isEqualTo(SlackNotificationOutboxStatus.PROCESSING),
                    () -> assertThat(jpaSlackNotificationOutboxHistoryRepository.findAllDomains()).isEmpty()
            );

            releaseLock.countDown();
            lockFuture.get(5, TimeUnit.SECONDS);
        } finally {
            executorService.shutdownNow();
        }
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
                () -> assertThat(actualSecondFailed.getFailure().type()).isEqualTo(SlackInteractionFailureType.RETRY_EXHAUSTED),
                () -> assertThat(actualSecondFailed.getFailure().reason()).isNotNull(),
                () -> assertThat(actualSecondFailed.getFailure().reason()).hasSize(500),
                () -> assertThat(actualSecondFailed.getFailure().reason()).isEqualTo("x".repeat(500)),
                () -> assertThat(histories).hasSize(2),
                () -> assertThat(histories).extracting(history -> history.getStatus())
                        .containsExactly(
                                SlackNotificationOutboxStatus.RETRY_PENDING,
                                SlackNotificationOutboxStatus.FAILED
                        )
        );
    }

    private List<SlackNotificationOutboxHistory> historiesOf(Long outboxId) {
        return jpaSlackNotificationOutboxHistoryRepository.findAllDomains()
                                                          .stream()
                                                          .filter(history -> outboxId.equals(history.getOutboxId()))
                                                          .sorted(Comparator.comparingInt(history -> history.getProcessingAttempt()))
                                                          .toList();
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            boolean completed = latch.await(5, TimeUnit.SECONDS);
            if (!completed) {
                throw new IllegalStateException("락 대기 중 시간이 초과되었습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("락 대기 중 인터럽트가 발생했습니다.", e);
        }
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
                    () -> assertThat(actual.getFailure().type()).isEqualTo(SlackInteractionFailureType.BUSINESS_INVARIANT)
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
                    () -> assertThat(actual.getFailure().type()).isEqualTo(SlackInteractionFailureType.BUSINESS_INVARIANT)
            );
        });
    }

    private void setProcessingState(
            SlackNotificationOutbox outbox,
            Instant processingStartedAt,
            int processingAttempt
    ) {
        if (!outbox.hasId()) {
            ReflectionTestUtils.setField(outbox, "identity", SlackNotificationOutboxId.assigned(999L));
        }
        outbox.claim(processingStartedAt);
        ReflectionTestUtils.setField(outbox, "processingAttempt", processingAttempt);
    }
}
