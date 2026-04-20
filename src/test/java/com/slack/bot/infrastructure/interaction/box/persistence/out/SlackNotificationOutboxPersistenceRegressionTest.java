package com.slack.bot.infrastructure.interaction.box.persistence.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxHistory;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStatus;
import com.slack.bot.infrastructure.interaction.box.out.repository.SlackNotificationOutboxRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackNotificationOutboxPersistenceRegressionTest {

    @Autowired
    SlackNotificationOutboxRepository slackNotificationOutboxRepository;

    @Autowired
    NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Test
    void claimNextId는_retry_pending_outbox를_processing으로_동기화한다() {
        // given
        SlackNotificationOutbox excludedOutbox = slackNotificationOutboxRepository.save(
                createOutbox("claim-excluded-regression")
        );
        SlackNotificationOutbox retryPendingOutbox = createRetryPendingOutbox(
                "claim-retry-regression",
                Instant.parse("2026-02-24T00:00:00Z"),
                Instant.parse("2026-02-24T00:00:30Z")
        );
        Instant processingStartedAt = Instant.parse("2026-02-24T00:01:00Z");

        // when
        assertThat(slackNotificationOutboxRepository.claimNextId(
                processingStartedAt,
                List.of(excludedOutbox.getId())
        )).hasValue(retryPendingOutbox.getId());

        // then
        assertAll(
                () -> assertThat(slackNotificationOutboxRepository.findById(retryPendingOutbox.getId()))
                        .hasValueSatisfying(outbox -> assertAll(
                                () -> assertThat(outbox.getStatus())
                                        .isEqualTo(SlackNotificationOutboxStatus.PROCESSING),
                                () -> assertThat(outbox.getProcessingAttempt()).isEqualTo(2),
                                () -> assertThat(outbox.getProcessingLease().isClaimed()).isTrue(),
                                () -> assertThat(outbox.getProcessingLease().startedAt())
                                        .isEqualTo(processingStartedAt),
                                () -> assertThat(outbox.getFailedTime().isPresent()).isFalse(),
                                () -> assertThat(outbox.getFailure().isPresent()).isFalse()
                        )),
                () -> assertThat(findOutboxState(retryPendingOutbox.getId()))
                        .singleElement()
                        .satisfies(snapshot -> assertAll(
                                () -> assertThat(snapshot.status()).isEqualTo("PROCESSING"),
                                () -> assertThat(snapshot.processingAttempt()).isEqualTo(2),
                                () -> assertThat(snapshot.processingLeaseState()).isEqualTo("CLAIMED"),
                                () -> assertThat(snapshot.sentTimeState()).isEqualTo("ABSENT"),
                                () -> assertThat(snapshot.failedTimeState()).isEqualTo("ABSENT"),
                                () -> assertThat(snapshot.failureState()).isEqualTo("ABSENT")
                        ))
        );
    }

    @Test
    void recoverTimeoutProcessing은_timeout_outbox를_복구하고_history_실패_상태를_저장한다() {
        // given
        SlackNotificationOutbox processingOutbox = slackNotificationOutboxRepository.save(
                createOutbox("recover-timeout-regression")
        );
        Instant processingStartedAt = Instant.parse("2026-02-24T00:00:00Z");
        processingOutbox.claim(processingStartedAt);
        slackNotificationOutboxRepository.save(processingOutbox);

        Instant failedAt = Instant.parse("2026-02-24T00:02:00Z");

        // when
        int recoveredCount = slackNotificationOutboxRepository.recoverTimeoutProcessing(
                Instant.parse("2026-02-24T00:01:00Z"),
                failedAt,
                "processing timeout",
                2,
                10
        );

        // then
        assertAll(
                () -> assertThat(recoveredCount).isEqualTo(1),
                () -> assertThat(slackNotificationOutboxRepository.findById(processingOutbox.getId()))
                        .hasValueSatisfying(outbox -> assertAll(
                                () -> assertThat(outbox.getStatus())
                                        .isEqualTo(SlackNotificationOutboxStatus.RETRY_PENDING),
                                () -> assertThat(outbox.getProcessingLease().isClaimed()).isFalse(),
                                () -> assertThat(outbox.getFailedTime().occurredAt()).isEqualTo(failedAt),
                                () -> assertThat(outbox.getFailure().reason()).isEqualTo("processing timeout"),
                                () -> assertThat(outbox.getFailure().type())
                                        .isEqualTo(SlackInteractionFailureType.PROCESSING_TIMEOUT)
                        )),
                () -> assertThat(findOutboxState(processingOutbox.getId()))
                        .singleElement()
                        .satisfies(snapshot -> assertAll(
                                () -> assertThat(snapshot.status()).isEqualTo("RETRY_PENDING"),
                                () -> assertThat(snapshot.processingLeaseState()).isEqualTo("IDLE"),
                                () -> assertThat(snapshot.failedTimeState()).isEqualTo("PRESENT"),
                                () -> assertThat(snapshot.failureState()).isEqualTo("PRESENT")
                        )),
                () -> assertThat(findHistoryState(processingOutbox.getId()))
                        .singleElement()
                        .satisfies(snapshot -> assertAll(
                                () -> assertThat(snapshot.status()).isEqualTo("RETRY_PENDING"),
                                () -> assertThat(snapshot.failureState()).isEqualTo("PRESENT"),
                                () -> assertThat(snapshot.failureType())
                                        .isEqualTo(SlackInteractionFailureType.PROCESSING_TIMEOUT.name())
                        ))
        );
    }

    private SlackNotificationOutbox createRetryPendingOutbox(
            String idempotencyKey,
            Instant processingStartedAt,
            Instant failedAt
    ) {
        SlackNotificationOutbox outbox = slackNotificationOutboxRepository.save(createOutbox(idempotencyKey));
        outbox.claim(processingStartedAt);
        slackNotificationOutboxRepository.save(outbox);
        SlackNotificationOutboxHistory history = outbox.markRetryPending(
                failedAt,
                "retryable failure",
                SlackInteractionFailureType.RETRYABLE
        );
        slackNotificationOutboxRepository.saveIfProcessingLeaseMatched(outbox, history, processingStartedAt);
        return outbox;
    }

    private SlackNotificationOutbox createOutbox(String idempotencyKey) {
        return SlackNotificationOutbox.builder()
                                      .messageType(SlackNotificationOutboxMessageType.CHANNEL_TEXT)
                                      .idempotencyKey(idempotencyKey)
                                      .teamId("T1")
                                      .channelId("C1")
                                      .text("hello")
                                      .build();
    }

    private List<OutboxStateSnapshot> findOutboxState(Long outboxId) {
        return namedParameterJdbcTemplate.query(
                """
                SELECT status,
                       processing_attempt,
                       processing_lease_state,
                       sent_time_state,
                       failed_time_state,
                       failure_state
                FROM slack_notification_outbox
                WHERE id = :outboxId
                """,
                new MapSqlParameterSource().addValue("outboxId", outboxId),
                (resultSet, rowNumber) -> new OutboxStateSnapshot(
                        resultSet.getString("status"),
                        resultSet.getInt("processing_attempt"),
                        resultSet.getString("processing_lease_state"),
                        resultSet.getString("sent_time_state"),
                        resultSet.getString("failed_time_state"),
                        resultSet.getString("failure_state")
                )
        );
    }

    private List<HistoryStateSnapshot> findHistoryState(Long outboxId) {
        return namedParameterJdbcTemplate.query(
                """
                SELECT status,
                       failure_state,
                       failure_type
                FROM slack_notification_outbox_history
                WHERE outbox_id = :outboxId
                """,
                new MapSqlParameterSource().addValue("outboxId", outboxId),
                (resultSet, rowNumber) -> new HistoryStateSnapshot(
                        resultSet.getString("status"),
                        resultSet.getString("failure_state"),
                        resultSet.getString("failure_type")
                )
        );
    }

    private record OutboxStateSnapshot(
            String status,
            int processingAttempt,
            String processingLeaseState,
            String sentTimeState,
            String failedTimeState,
            String failureState
    ) {
    }

    private record HistoryStateSnapshot(
            String status,
            String failureState,
            String failureType
    ) {
    }
}
