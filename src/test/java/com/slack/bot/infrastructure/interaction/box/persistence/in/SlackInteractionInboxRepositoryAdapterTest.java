package com.slack.bot.infrastructure.interaction.box.persistence.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxHistory;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxStatus;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackInteractionInboxRepositoryAdapterTest {

    @Autowired
    SlackInteractionInboxRepository slackInteractionInboxRepository;

    @Autowired
    NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Test
    void recoverTimeoutProcessing은_interactionType이_null이면_예외를_던진다() {
        // given
        SlackInteractionInboxRepositoryAdapter adapter = new SlackInteractionInboxRepositoryAdapter(null, null, null);

        // when & then
        assertThatThrownBy(() -> adapter.recoverTimeoutProcessing(
                null,
                Instant.parse("2026-02-24T00:02:00Z"),
                Instant.parse("2026-02-24T00:05:00Z"),
                "timeout",
                3,
                100
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("interactionType은 비어 있을 수 없습니다.");
    }

    @Test
    void enqueue는_중복_idempotencyKey를_한번만_적재한다() {
        // given
        SlackInteractionInboxType interactionType = SlackInteractionInboxType.BLOCK_ACTIONS;
        String idempotencyKey = "interaction-inbox-enqueue-duplicate";
        String payloadJson = "{\"value\":1}";

        // when
        boolean firstEnqueued = slackInteractionInboxRepository.enqueue(interactionType, idempotencyKey, payloadJson);
        boolean secondEnqueued = slackInteractionInboxRepository.enqueue(interactionType, idempotencyKey, payloadJson);

        // then
        SlackInteractionInbox savedInbox = findInboxByIdempotencyKey(idempotencyKey);
        assertAll(
                () -> assertThat(firstEnqueued).isTrue(),
                () -> assertThat(secondEnqueued).isFalse(),
                () -> assertThat(countInboxByIdempotencyKey(idempotencyKey)).isEqualTo(1),
                () -> assertThat(savedInbox.getInteractionType()).isEqualTo(SlackInteractionInboxType.BLOCK_ACTIONS),
                () -> assertThat(savedInbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.PENDING),
                () -> assertThat(savedInbox.getProcessingAttempt()).isZero()
        );
    }

    @Test
    void claimNextId는_제외된_inbox를_건너뛰고_다음_inbox를_claim한다() {
        // given
        SlackInteractionInbox firstInbox = slackInteractionInboxRepository.save(
                SlackInteractionInbox.pending(
                        SlackInteractionInboxType.BLOCK_ACTIONS,
                        "interaction-inbox-claim-first",
                        "{\"value\":1}"
                )
        );
        SlackInteractionInbox secondInbox = slackInteractionInboxRepository.save(
                SlackInteractionInbox.pending(
                        SlackInteractionInboxType.BLOCK_ACTIONS,
                        "interaction-inbox-claim-second",
                        "{\"value\":2}"
                )
        );
        Instant processingStartedAt = Instant.parse("2026-04-16T01:00:00Z");

        // when
        Long claimedInboxId = slackInteractionInboxRepository.claimNextId(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                processingStartedAt,
                List.of(firstInbox.getId())
        ).orElseThrow();

        // then
        SlackInteractionInbox reloadedFirstInbox = slackInteractionInboxRepository.findById(firstInbox.getId()).orElseThrow();
        SlackInteractionInbox reloadedSecondInbox = slackInteractionInboxRepository.findById(secondInbox.getId()).orElseThrow();
        assertAll(
                () -> assertThat(claimedInboxId).isEqualTo(secondInbox.getId()),
                () -> assertThat(reloadedFirstInbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.PENDING),
                () -> assertThat(reloadedSecondInbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSING),
                () -> assertThat(reloadedSecondInbox.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(reloadedSecondInbox.currentProcessingLeaseStartedAt()).isEqualTo(processingStartedAt)
        );
    }

    @Test
    void saveIfProcessingLeaseMatched는_lease가_일치하면_상태와_history를_같이_저장한다() {
        // given
        Instant claimedProcessingStartedAt = Instant.parse("2026-04-16T02:00:00Z");
        Instant processedAt = Instant.parse("2026-04-16T02:01:00Z");
        SlackInteractionInbox processingInbox = createProcessingInbox(
                "interaction-inbox-save-if-matched",
                claimedProcessingStartedAt
        );
        SlackInteractionInbox reloadedInbox = slackInteractionInboxRepository.findById(processingInbox.getId()).orElseThrow();
        SlackInteractionInboxHistory history = reloadedInbox.markProcessed(processedAt);

        // when
        boolean updated = slackInteractionInboxRepository.saveIfProcessingLeaseMatched(
                reloadedInbox,
                history,
                claimedProcessingStartedAt
        );

        // then
        SlackInteractionInbox savedInbox = slackInteractionInboxRepository.findById(processingInbox.getId()).orElseThrow();
        List<SlackInteractionInboxHistory> histories = historiesOf(processingInbox.getId());
        assertAll(
                () -> assertThat(updated).isTrue(),
                () -> assertThat(savedInbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSED),
                () -> assertThat(savedInbox.getProcessedTime().occurredAt()).isEqualTo(processedAt),
                () -> assertThat(histories).hasSize(1),
                () -> assertThat(histories.getFirst().getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSED),
                () -> assertThat(histories.getFirst().getCompletedAt()).isEqualTo(processedAt)
        );
    }

    @Test
    void saveIfProcessingLeaseMatched는_lease가_다르면_상태와_history를_저장하지_않는다() {
        // given
        Instant actualClaimedProcessingStartedAt = Instant.parse("2026-04-16T03:00:00Z");
        SlackInteractionInbox processingInbox = createProcessingInbox(
                "interaction-inbox-save-if-not-matched",
                actualClaimedProcessingStartedAt
        );
        SlackInteractionInbox reloadedInbox = slackInteractionInboxRepository.findById(processingInbox.getId()).orElseThrow();
        SlackInteractionInboxHistory history = reloadedInbox.markFailed(
                Instant.parse("2026-04-16T03:01:00Z"),
                "failure",
                SlackInteractionFailureType.BUSINESS_INVARIANT
        );

        // when
        boolean updated = slackInteractionInboxRepository.saveIfProcessingLeaseMatched(
                reloadedInbox,
                history,
                actualClaimedProcessingStartedAt.plusSeconds(1)
        );

        // then
        SlackInteractionInbox savedInbox = slackInteractionInboxRepository.findById(processingInbox.getId()).orElseThrow();
        assertAll(
                () -> assertThat(updated).isFalse(),
                () -> assertThat(savedInbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSING),
                () -> assertThat(savedInbox.currentProcessingLeaseStartedAt()).isEqualTo(actualClaimedProcessingStartedAt),
                () -> assertThat(historiesOf(processingInbox.getId())).isEmpty()
        );
    }

    @Test
    void save는_업데이트_대상이_사라지면_예외를_던진다() {
        // given
        SlackInteractionInbox savedInbox = slackInteractionInboxRepository.save(
                SlackInteractionInbox.pending(
                        SlackInteractionInboxType.BLOCK_ACTIONS,
                        "interaction-inbox-save-missing-target",
                        "{\"value\":1}"
                )
        );
        savedInbox.claim(Instant.parse("2026-04-16T04:00:00Z"));
        deleteInboxById(savedInbox.getId());

        // when & then
        assertThatThrownBy(() -> slackInteractionInboxRepository.save(savedInbox))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("저장 대상 inbox를 찾을 수 없습니다. id=" + savedInbox.getId())
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(slackInteractionInboxRepository.findById(savedInbox.getId())).isEmpty();
    }

    @Test
    void 완료된_inbox와_history를_같이_삭제한다() {
        // given
        SlackInteractionInbox oldProcessedInbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "interaction-inbox-cleanup-processed",
                "{\"value\":1}"
        );
        oldProcessedInbox.claim(Instant.parse("2026-02-24T00:00:00Z"));
        SlackInteractionInboxHistory oldProcessedHistory = oldProcessedInbox.markProcessed(
                Instant.parse("2026-02-24T00:01:00Z")
        );
        SlackInteractionInbox savedProcessedInbox = slackInteractionInboxRepository.save(
                oldProcessedInbox,
                oldProcessedHistory
        );

        SlackInteractionInbox recentProcessedInbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.VIEW_SUBMISSION,
                "interaction-inbox-cleanup-recent",
                "{\"value\":2}"
        );
        recentProcessedInbox.claim(Instant.parse("2026-02-24T00:02:00Z"));
        SlackInteractionInboxHistory recentProcessedHistory = recentProcessedInbox.markProcessed(
                Instant.parse("2026-02-24T00:09:00Z")
        );
        SlackInteractionInbox savedRecentProcessedInbox = slackInteractionInboxRepository.save(
                recentProcessedInbox,
                recentProcessedHistory
        );

        // when
        int deletedCount = slackInteractionInboxRepository.deleteCompletedBefore(
                Instant.parse("2026-02-24T00:05:00Z"),
                100
        );

        // then
        assertAll(
                () -> assertThat(deletedCount).isEqualTo(1),
                () -> assertThat(slackInteractionInboxRepository.findById(savedProcessedInbox.getId())).isEmpty(),
                () -> assertThat(slackInteractionInboxRepository.findById(savedRecentProcessedInbox.getId())).isPresent(),
                () -> assertThat(countHistoryByInboxId(savedProcessedInbox.getId())).isZero(),
                () -> assertThat(countHistoryByInboxId(savedRecentProcessedInbox.getId())).isEqualTo(1)
        );
    }

    @Test
    void deleteCompletedBefore는_batch_크기만큼만_삭제한다() {
        // given
        SlackInteractionInbox firstInbox = createProcessedInbox(
                "interaction-inbox-cleanup-batch-1",
                Instant.parse("2026-02-24T00:01:00Z")
        );
        SlackInteractionInbox secondInbox = createProcessedInbox(
                "interaction-inbox-cleanup-batch-2",
                Instant.parse("2026-02-24T00:02:00Z")
        );

        // when
        int deletedCount = slackInteractionInboxRepository.deleteCompletedBefore(
                Instant.parse("2026-02-24T00:05:00Z"),
                1
        );

        // then
        assertAll(
                () -> assertThat(deletedCount).isEqualTo(1),
                () -> assertThat(slackInteractionInboxRepository.findById(firstInbox.getId())).isEmpty(),
                () -> assertThat(slackInteractionInboxRepository.findById(secondInbox.getId())).isPresent()
        );
    }

    private SlackInteractionInbox createProcessedInbox(String idempotencyKey, Instant processedAt) {
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                idempotencyKey,
                "{\"payload\":true}"
        );
        inbox.claim(processedAt.minusSeconds(30L));
        SlackInteractionInboxHistory history = inbox.markProcessed(processedAt);
        return slackInteractionInboxRepository.save(inbox, history);
    }

    private SlackInteractionInbox createProcessingInbox(String idempotencyKey, Instant processingStartedAt) {
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                idempotencyKey,
                "{\"payload\":true}"
        );
        inbox.claim(processingStartedAt);
        return slackInteractionInboxRepository.save(inbox);
    }

    private List<SlackInteractionInboxHistory> historiesOf(Long inboxId) {
        return namedParameterJdbcTemplate.query(
                """
                SELECT id,
                       inbox_id,
                       processing_attempt,
                       status,
                       completed_at,
                       failure_reason,
                       failure_type
                FROM slack_interaction_inbox_history
                WHERE inbox_id = :inboxId
                ORDER BY processing_attempt ASC
                """,
                new MapSqlParameterSource().addValue("inboxId", inboxId),
                (resultSet, rowNum) -> mapHistory(resultSet)
        );
    }

    private SlackInteractionInbox findInboxByIdempotencyKey(String idempotencyKey) {
        return namedParameterJdbcTemplate.queryForObject(
                """
                SELECT id,
                       interaction_type,
                       idempotency_key,
                       payload_json,
                       status,
                       processing_attempt,
                       processing_started_at,
                       processed_at,
                       failed_at,
                       failure_reason,
                       failure_type
                FROM slack_interaction_inbox
                WHERE idempotency_key = :idempotencyKey
                """,
                new MapSqlParameterSource().addValue("idempotencyKey", idempotencyKey),
                (resultSet, rowNum) -> mapInbox(resultSet)
        );
    }

    private int countInboxByIdempotencyKey(String idempotencyKey) {
        return namedParameterJdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM slack_interaction_inbox
                WHERE idempotency_key = :idempotencyKey
                """,
                new MapSqlParameterSource().addValue("idempotencyKey", idempotencyKey),
                Integer.class
        );
    }

    private int countHistoryByInboxId(Long inboxId) {
        return namedParameterJdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM slack_interaction_inbox_history
                WHERE inbox_id = :inboxId
                """,
                new MapSqlParameterSource().addValue("inboxId", inboxId),
                Integer.class
        );
    }

    private void deleteInboxById(Long inboxId) {
        namedParameterJdbcTemplate.update(
                """
                DELETE FROM slack_interaction_inbox
                WHERE id = :inboxId
                """,
                new MapSqlParameterSource().addValue("inboxId", inboxId)
        );
    }

    private SlackInteractionInbox mapInbox(ResultSet resultSet) throws SQLException {
        BoxProcessingLease processingLease = toProcessingLease(resultSet);
        BoxEventTime processedTime = toProcessedTime(resultSet);
        BoxEventTime failedTime = toFailedTime(resultSet);
        BoxFailureSnapshot<SlackInteractionFailureType> failure = toFailure(resultSet);

        return SlackInteractionInbox.rehydrate(
                resultSet.getLong("id"),
                SlackInteractionInboxType.valueOf(resultSet.getString("interaction_type")),
                resultSet.getString("idempotency_key"),
                resultSet.getString("payload_json"),
                SlackInteractionInboxStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("processing_attempt"),
                processingLease,
                processedTime,
                failedTime,
                failure
        );
    }

    private SlackInteractionInboxHistory mapHistory(ResultSet resultSet) throws SQLException {
        return SlackInteractionInboxHistory.rehydrate(
                resultSet.getLong("id"),
                resultSet.getLong("inbox_id"),
                resultSet.getInt("processing_attempt"),
                SlackInteractionInboxStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("completed_at").toInstant(),
                toFailure(resultSet)
        );
    }

    private BoxProcessingLease toProcessingLease(ResultSet resultSet) throws SQLException {
        if (resultSet.getTimestamp("processing_started_at") == null) {
            return BoxProcessingLease.idle();
        }

        return BoxProcessingLease.claimed(
                resultSet.getTimestamp("processing_started_at").toInstant()
        );
    }

    private BoxEventTime toProcessedTime(ResultSet resultSet) throws SQLException {
        if (resultSet.getTimestamp("processed_at") == null) {
            return BoxEventTime.absent();
        }

        return BoxEventTime.present(
                resultSet.getTimestamp("processed_at").toInstant()
        );
    }

    private BoxEventTime toFailedTime(ResultSet resultSet) throws SQLException {
        if (resultSet.getTimestamp("failed_at") == null) {
            return BoxEventTime.absent();
        }

        return BoxEventTime.present(
                resultSet.getTimestamp("failed_at").toInstant()
        );
    }

    private BoxFailureSnapshot<SlackInteractionFailureType> toFailure(ResultSet resultSet) throws SQLException {
        String failureReason = resultSet.getString("failure_reason");
        String failureType = resultSet.getString("failure_type");

        if (failureReason == null && failureType == null) {
            return BoxFailureSnapshot.absent();
        }

        return BoxFailureSnapshot.present(
                failureReason,
                SlackInteractionFailureType.valueOf(failureType)
        );
    }
}
