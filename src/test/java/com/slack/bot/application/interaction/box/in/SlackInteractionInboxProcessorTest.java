package com.slack.bot.application.interaction.box.in;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interaction.block.BlockActionType;
import com.slack.bot.application.interaction.client.NotificationApiClient;
import com.slack.bot.application.worker.PollingHintPublisher;
import com.slack.bot.application.worker.PollingHintTarget;
import com.slack.bot.domain.reservation.ReservationStatus;
import com.slack.bot.domain.reservation.ReviewReservation;
import com.slack.bot.domain.reservation.repository.ReviewReservationRepository;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
class SlackInteractionInboxProcessorTest {

    @Autowired
    SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    @Autowired
    NotificationApiClient notificationApiClient;

    @Autowired
    ReviewReservationRepository actualReviewReservationRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    SlackInteractionInboxRepository actualSlackInteractionInboxRepository;

    @Autowired
    Clock clock;

    @Autowired
    PollingHintPublisher pollingHintPublisher;

    @Autowired
    NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interaction/active_review_reservation_t1_project_123_u1.sql"
    })
    void block_actions_인박스를_처리하면_취소_예약_도메인_흐름이_수행된다() throws Exception {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1"))
                .willReturn("D-REVIEWER");
        String payloadJson = objectMapper.writeValueAsString(cancelReservationPayload("100"));

        // when
        boolean actual = slackInteractionInboxProcessor.enqueueBlockAction(payloadJson);

        slackInteractionInboxProcessor.processPendingBlockActions(10);

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertAll(
                    () -> assertThat(actual).isTrue(),
                    () -> assertThat(findAllInboxes())
                            .hasSize(1)
                            .first()
                            .extracting(inbox -> inbox.getStatus())
                            .isEqualTo(SlackInteractionInboxStatus.PROCESSED),
                    () -> assertThat(actualReviewReservationRepository.findById(100L))
                            .map(reservation -> reservation.getStatus())
                            .hasValue(ReservationStatus.CANCELLED)
            );
            verify(notificationApiClient, times(1)).openDirectMessageChannel("xoxb-test-token", "U1");
            verify(notificationApiClient, times(1)).sendBlockMessage(
                    eq("xoxb-test-token"),
                    eq("D-REVIEWER"),
                    any(),
                    any()
            );
        });
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interaction/active_review_reservation_t1_project_123_u1.sql",
            "classpath:sql/fixtures/box/processing_timeout_block_action_inbox.sql"
    })
    void PROCESSING_타임아웃_block_actions_inbox는_RETRY_PENDING으로_복구된_후_정상_재처리된다() {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1"))
                .willReturn("D-REVIEWER");

        // when
        slackInteractionInboxProcessor.recoverBlockActionTimeoutProcessing();
        slackInteractionInboxProcessor.processPendingBlockActions(10);

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            SlackInteractionInbox actualProcessedInbox = actualSlackInteractionInboxRepository.findById(200L)
                                                                                              .orElseThrow();
            List<SlackInteractionInboxHistory> actualHistories = historiesOf(200L);

            assertAll(
                    () -> assertThat(actualProcessedInbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSED),
                    () -> assertThat(actualProcessedInbox.getProcessingAttempt()).isEqualTo(2),
                    () -> assertThat(actualHistories).hasSize(2),
                    () -> assertThat(actualHistories.getFirst().getFailure().type()).isEqualTo(
                            SlackInteractionFailureType.PROCESSING_TIMEOUT
                    ),
                    () -> assertThat(actualReviewReservationRepository.findById(100L))
                            .map(reservation -> reservation.getStatus())
                            .hasValue(ReservationStatus.CANCELLED)
            );
            verify(notificationApiClient, times(1)).openDirectMessageChannel("xoxb-test-token", "U1");
            verify(notificationApiClient, times(1)).sendBlockMessage(
                    eq("xoxb-test-token"),
                    eq("D-REVIEWER"),
                    any(),
                    any()
            );
            verify(pollingHintPublisher).publish(PollingHintTarget.BLOCK_ACTION_INBOX);
        });
    }

    @Test
    void PROCESSING_타임아웃_block_actions_inbox가_최대시도에_도달하면_FAILED_RETRY_EXHAUSTED로_격리된다() {
        // given
        SlackInteractionInbox timeoutInbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "BLOCK-ACTION-TIMEOUT-POISON-PILL",
                "{\"team\":{\"id\":\"T1\"},\"channel\":{\"id\":\"C1\"},\"user\":{\"id\":\"U1\"},\"actions\":[{\"action_id\":\"cancel_review_reservation\",\"value\":\"100\"}]}"
        );
        Instant base = clock.instant();
        setProcessingState(timeoutInbox, base.minusSeconds(120), 1);
        timeoutInbox.markRetryPending(base.minusSeconds(110), "actualFirst failure");
        setProcessingState(timeoutInbox, base.minusSeconds(100), 2);
        SlackInteractionInbox actualSaved = actualSlackInteractionInboxRepository.save(timeoutInbox);

        // when
        slackInteractionInboxProcessor.recoverBlockActionTimeoutProcessing();

        // then
        SlackInteractionInbox actual = actualSlackInteractionInboxRepository.findById(actualSaved.getId()).orElseThrow();

        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED),
                () -> assertThat(actual.getProcessingAttempt()).isEqualTo(2),
                () -> assertThat(actual.getFailure().type()).isEqualTo(SlackInteractionFailureType.RETRY_EXHAUSTED),
                () -> assertThat(actual.getFailure().reason()).isNotBlank()
        );
    }

    @Test
    void PROCESSING_타임아웃_block_actions_inbox_복구는_배치_크기만큼만_처리한다() {
        // given
        Instant base = clock.instant();
        List<Long> savedInboxIds = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            SlackInteractionInbox timeoutInbox = SlackInteractionInbox.pending(
                    SlackInteractionInboxType.BLOCK_ACTIONS,
                    "BLOCK-ACTION-TIMEOUT-BATCH-" + index,
                    "{\"type\":\"block_actions\",\"actions\":[]}"
            );
            setProcessingState(timeoutInbox, base.minusSeconds(120L + index), 1);
            SlackInteractionInbox savedInbox = actualSlackInteractionInboxRepository.save(timeoutInbox);
            savedInboxIds.add(savedInbox.getId());
        }

        // when
        int recoveredCount = slackInteractionInboxProcessor.recoverBlockActionTimeoutProcessing();

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            List<SlackInteractionInbox> actualInboxes = findInboxesByIds(savedInboxIds);
            List<SlackInteractionInboxHistory> actualHistories = findHistoriesByInboxIds(savedInboxIds);

            assertAll(
                    () -> assertThat(recoveredCount).isEqualTo(100),
                    () -> assertThat(actualInboxes).filteredOn(inbox -> inbox.getStatus() == SlackInteractionInboxStatus.RETRY_PENDING)
                            .hasSize(100),
                    () -> assertThat(actualInboxes).filteredOn(inbox -> inbox.getStatus() == SlackInteractionInboxStatus.PROCESSING)
                            .hasSize(1),
                    () -> assertThat(actualHistories).hasSize(100),
                    () -> assertThat(actualHistories).extracting(history -> history.getStatus())
                            .containsOnly(SlackInteractionInboxStatus.RETRY_PENDING)
            );
        });
    }

    @Test
    void PROCESSING_타임아웃_block_actions_inbox_복구는_재시도_가능과_소진건을_합쳐_배치_크기만큼만_처리한다() {
        // given
        Instant base = clock.instant();
        List<Long> savedInboxIds = new ArrayList<>();
        for (int index = 0; index < 50; index++) {
            SlackInteractionInbox retryableInbox = SlackInteractionInbox.pending(
                    SlackInteractionInboxType.BLOCK_ACTIONS,
                    "BLOCK-ACTION-TIMEOUT-MIXED-RETRY-" + index,
                    "{\"type\":\"block_actions\",\"actions\":[]}"
            );
            setProcessingState(retryableInbox, base.minusSeconds(200L + index), 1);
            SlackInteractionInbox savedInbox = actualSlackInteractionInboxRepository.save(retryableInbox);
            savedInboxIds.add(savedInbox.getId());
        }
        for (int index = 0; index < 51; index++) {
            SlackInteractionInbox exhaustedInbox = SlackInteractionInbox.pending(
                    SlackInteractionInboxType.BLOCK_ACTIONS,
                    "BLOCK-ACTION-TIMEOUT-MIXED-FAILED-" + index,
                    "{\"type\":\"block_actions\",\"actions\":[]}"
            );
            setProcessingState(exhaustedInbox, base.minusSeconds(100L + index), 2);
            SlackInteractionInbox savedInbox = actualSlackInteractionInboxRepository.save(exhaustedInbox);
            savedInboxIds.add(savedInbox.getId());
        }

        // when
        int recoveredCount = slackInteractionInboxProcessor.recoverBlockActionTimeoutProcessing();

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            List<SlackInteractionInbox> actualInboxes = findInboxesByIds(savedInboxIds);
            List<SlackInteractionInboxHistory> actualHistories = findHistoriesByInboxIds(savedInboxIds);

            assertAll(
                    () -> assertThat(recoveredCount).isEqualTo(100),
                    () -> assertThat(actualInboxes).filteredOn(inbox -> inbox.getStatus() == SlackInteractionInboxStatus.RETRY_PENDING)
                            .hasSize(50),
                    () -> assertThat(actualInboxes).filteredOn(inbox -> inbox.getStatus() == SlackInteractionInboxStatus.FAILED)
                            .hasSize(50),
                    () -> assertThat(actualInboxes).filteredOn(inbox -> inbox.getStatus() == SlackInteractionInboxStatus.PROCESSING)
                            .hasSize(1),
                    () -> assertThat(actualHistories).filteredOn(
                            history -> history.getFailure().type() == SlackInteractionFailureType.PROCESSING_TIMEOUT
                    ).hasSize(50),
                    () -> assertThat(actualHistories).filteredOn(
                            history -> history.getFailure().type() == SlackInteractionFailureType.RETRY_EXHAUSTED
                    ).hasSize(50)
            );
        });
    }

    @Test
    void 잠겨있는_block_actions_timeout_행은_recovery에서_건너뛴다() throws Exception {
        // given
        SlackInteractionInbox timeoutInbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "BLOCK-ACTION-TIMEOUT-LOCKED",
                "{\"type\":\"block_actions\",\"actions\":[]}"
        );
        setProcessingState(timeoutInbox, Instant.parse("2026-03-24T00:01:00Z"), 1);
        SlackInteractionInbox saved = actualSlackInteractionInboxRepository.save(timeoutInbox);
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<?> lockFuture = executorService.submit(() -> {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    namedParameterJdbcTemplate.query(
                            """
                            SELECT id
                            FROM slack_interaction_inbox
                            WHERE id = :inboxId
                            FOR UPDATE
                            """,
                            new MapSqlParameterSource().addValue("inboxId", saved.getId()),
                            (resultSet, rowNum) -> resultSet.getLong(1)
                    );
                    lockAcquired.countDown();
                    awaitLatch(releaseLock);
                });
            });
            awaitLatch(lockAcquired);

            // when
            int skippedCount = slackInteractionInboxProcessor.recoverBlockActionTimeoutProcessing();

            // then
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
                SlackInteractionInbox lockedInbox = actualSlackInteractionInboxRepository.findById(saved.getId()).orElseThrow();

                assertAll(
                        () -> assertThat(skippedCount).isZero(),
                        () -> assertThat(lockedInbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSING),
                        () -> assertThat(historiesOf(saved.getId())).isEmpty()
                );
            });

            releaseLock.countDown();
            lockFuture.get(5, TimeUnit.SECONDS);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interaction/active_review_reservation_t1_project_123_u1.sql"
    })
    void 동일_payload를_중복_enqueue하면_한번만_적재되고_한번만_처리된다() throws Exception {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1"))
                .willReturn("D-REVIEWER");
        String payloadJson = objectMapper.writeValueAsString(cancelReservationPayload("100"));

        // when
        boolean actualFirst = slackInteractionInboxProcessor.enqueueBlockAction(payloadJson);
        boolean actualSecond = slackInteractionInboxProcessor.enqueueBlockAction(payloadJson);
        slackInteractionInboxProcessor.processPendingBlockActions(10);

        // then
        assertAll(
                () -> assertThat(actualFirst).isTrue(),
                () -> assertThat(actualSecond).isFalse()
        );
        verify(notificationApiClient).openDirectMessageChannel("xoxb-test-token", "U1");
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interaction/project_123.sql"
    })
    void view_submission_인박스를_처리하면_리뷰_예약_워크플로우가_비동기로_수행된다() throws Exception {
        // given
        String payloadJson = objectMapper.writeValueAsString(viewSubmissionPayload("30"));

        // when
        boolean actualEnqueued = slackInteractionInboxProcessor.enqueueViewSubmission(payloadJson);
        slackInteractionInboxProcessor.processPendingViewSubmissions(10);

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertAll(
                    () -> assertThat(actualEnqueued).isTrue(),
                    () -> assertThat(actualReviewReservationRepository.findActive("T1", 123L, "U1"))
                            .map(saved -> saved.getReservationPullRequest().getGithubPullRequestId())
                            .hasValue(10L)
            );
        });
    }

    @Test
    void 비즈니스_유효성_실패는_재시도하지않고_즉시_FAILED로_마킹된다() {
        // given
        String invalidPayload = "{invalid-json";

        // when
        boolean actual = slackInteractionInboxProcessor.enqueueBlockAction(invalidPayload);
        slackInteractionInboxProcessor.processPendingBlockActions(10);
        SlackInteractionInbox inbox = findAllInboxes().getFirst();
        SlackInteractionInbox actualAfterFirst = actualSlackInteractionInboxRepository.findById(inbox.getId()).orElseThrow();
        List<SlackInteractionInboxHistory> histories = historiesOf(inbox.getId());

        // then
        assertAll(
                () -> assertThat(actual).isTrue(),
                () -> assertThat(actualAfterFirst.getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED),
                () -> assertThat(actualAfterFirst.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actualAfterFirst.getFailure().type()).isEqualTo(SlackInteractionFailureType.BUSINESS_INVARIANT),
                () -> assertThat(histories).hasSize(1),
                () -> assertThat(histories.getFirst().getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED),
                () -> assertThat(histories.getFirst().getFailure().type()).isEqualTo(SlackInteractionFailureType.BUSINESS_INVARIANT)
        );
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

    private List<SlackInteractionInbox> findAllInboxes() {
        return namedParameterJdbcTemplate.query(
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
                ORDER BY id ASC
                """,
                (resultSet, rowNum) -> mapInbox(resultSet)
        );
    }

    private List<SlackInteractionInbox> findInboxesByIds(List<Long> inboxIds) {
        return namedParameterJdbcTemplate.query(
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
                WHERE id IN (:inboxIds)
                ORDER BY id ASC
                """,
                new MapSqlParameterSource().addValue("inboxIds", inboxIds),
                (resultSet, rowNum) -> mapInbox(resultSet)
        );
    }

    private List<SlackInteractionInboxHistory> findHistoriesByInboxIds(List<Long> inboxIds) {
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
                WHERE inbox_id IN (:inboxIds)
                ORDER BY inbox_id ASC, processing_attempt ASC
                """,
                new MapSqlParameterSource().addValue("inboxIds", inboxIds),
                (resultSet, rowNum) -> mapHistory(resultSet)
        );
    }

    private SlackInteractionInbox mapInbox(ResultSet resultSet) throws SQLException {
        return SlackInteractionInbox.rehydrate(
                resultSet.getLong("id"),
                SlackInteractionInboxType.valueOf(resultSet.getString("interaction_type")),
                resultSet.getString("idempotency_key"),
                resultSet.getString("payload_json"),
                SlackInteractionInboxStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("processing_attempt"),
                toProcessingLease(resultSet),
                toProcessedTime(resultSet),
                toFailedTime(resultSet),
                toFailure(resultSet)
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

        return BoxProcessingLease.claimed(resultSet.getTimestamp("processing_started_at").toInstant());
    }

    private BoxEventTime toProcessedTime(ResultSet resultSet) throws SQLException {
        if (resultSet.getTimestamp("processed_at") == null) {
            return BoxEventTime.absent();
        }

        return BoxEventTime.present(resultSet.getTimestamp("processed_at").toInstant());
    }

    private BoxEventTime toFailedTime(ResultSet resultSet) throws SQLException {
        if (resultSet.getTimestamp("failed_at") == null) {
            return BoxEventTime.absent();
        }

        return BoxEventTime.present(resultSet.getTimestamp("failed_at").toInstant());
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

    private ObjectNode cancelReservationPayload(String reservationId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("team", objectMapper.createObjectNode().put("id", "T1"));
        payload.set("channel", objectMapper.createObjectNode().put("id", "C1"));
        payload.set("user", objectMapper.createObjectNode().put("id", "U1"));
        payload.set("actions", actions(BlockActionType.CANCEL_REVIEW_RESERVATION.value(), reservationId));
        return payload;
    }

    private ObjectNode viewSubmissionPayload(String selectedOption) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "view_submission");
        payload.set("team", objectMapper.createObjectNode().put("id", "T1"));
        payload.set("user", objectMapper.createObjectNode().put("id", "U1"));

        ObjectNode view = objectMapper.createObjectNode();
        view.put("callback_id", "review_time_submit");
        view.put("private_metadata", metaJsonWithProjectId("123", null));

        ObjectNode state = objectMapper.createObjectNode();
        ObjectNode values = objectMapper.createObjectNode();
        ObjectNode timeBlock = objectMapper.createObjectNode();
        ObjectNode timeAction = objectMapper.createObjectNode();
        ObjectNode selected = objectMapper.createObjectNode();
        selected.put("value", selectedOption);
        timeAction.set("selected_option", selected);
        timeBlock.set("time_action", timeAction);
        values.set("time_block", timeBlock);
        state.set("values", values);
        view.set("state", state);

        payload.set("view", view);
        return payload;
    }

    private ArrayNode actions(String actionId, String value) {
        ArrayNode actions = objectMapper.createArrayNode();
        actions.add(objectMapper.createObjectNode()
                                .put("action_id", actionId)
                                .put("value", value));
        return actions;
    }

    private String metaJsonWithProjectId(String projectId, String reservationId) {
        ObjectNode meta = objectMapper.createObjectNode()
                                      .put("team_id", "T1")
                                      .put("channel_id", "C1")
                                      .put("github_pull_request_id", 10L)
                                      .put("pull_request_number", 10)
                                      .put("pull_request_title", "PR 제목")
                                      .put("pull_request_url", "https://github.com/org/repo/pull/10")
                                      .put("project_id", projectId)
                                      .put("author_github_id", "author-gh")
                                      .put("author_slack_id", "U_AUTHOR");

        if (reservationId != null) {
            meta.put("reservation_id", reservationId);
        }

        return meta.toString();
    }

    private void setProcessingState(
            SlackInteractionInbox inbox,
            Instant processingStartedAt,
            int processingAttempt
    ) {
        inbox.claim(processingStartedAt);
        ReflectionTestUtils.setField(inbox, "processingAttempt", processingAttempt);
    }
}
