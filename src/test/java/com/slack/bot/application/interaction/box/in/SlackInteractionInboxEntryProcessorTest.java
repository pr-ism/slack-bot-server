package com.slack.bot.application.interaction.box.in;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interaction.block.BlockActionType;
import com.slack.bot.application.interaction.box.in.exception.InboxProcessingLeaseLostException;
import com.slack.bot.application.interaction.client.NotificationApiClient;
import com.slack.bot.domain.reservation.ReservationStatus;
import com.slack.bot.domain.reservation.ReviewReservation;
import com.slack.bot.domain.reservation.repository.ReviewReservationRepository;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxHistory;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxStatus;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
@MockitoSpyBean(types = SlackInteractionInboxRepository.class)
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackInteractionInboxEntryProcessorTest {

    private static final Instant CLAIMED_PROCESSING_STARTED_AT = Instant.parse("2026-02-15T00:00:00Z");

    @Autowired
    SlackInteractionInboxEntryProcessor slackInteractionInboxEntryProcessor;

    @Autowired
    SlackInteractionInboxRepository slackInteractionInboxRepository;

    @Autowired
    ReviewReservationRepository reviewReservationRepository;

    @Autowired
    NotificationApiClient notificationApiClient;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interaction/active_review_reservation_t1_project_123_u1.sql"
    })
    void block_actions_엔트리_처리시_PROCESSED로_마킹되고_취소_예약_흐름이_수행된다() throws Exception {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1"))
                .willReturn("D-REVIEWER");
        String payloadJson = objectMapper.writeValueAsString(cancelReservationPayload("100"));
        SlackInteractionInbox inbox = savePendingInbox(SlackInteractionInboxType.BLOCK_ACTIONS, payloadJson);

        // when
        slackInteractionInboxEntryProcessor.processClaimedBlockAction(inbox.getId(), CLAIMED_PROCESSING_STARTED_AT);

        // then
        SlackInteractionInbox actualInbox = slackInteractionInboxRepository.findById(inbox.getId())
                                                                           .orElseThrow();

        assertAll(
                () -> assertThat(actualInbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSED),
                () -> assertThat(actualInbox.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(reviewReservationRepository.findById(100L))
                        .map(reservation -> reservation.getStatus())
                        .hasValue(ReservationStatus.CANCELLED)
        );
        verify(notificationApiClient).openDirectMessageChannel("xoxb-test-token", "U1");
        verify(notificationApiClient).sendBlockMessage(
                        eq("xoxb-test-token"),
                        eq("D-REVIEWER"),
                        any(),
                        any()
                );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/reservation/project_123.sql",
            "classpath:sql/fixtures/interaction/active_review_reservation_t1_project_123_u1.sql"
    })
    void block_actions_엔트리_처리시_중복_예약_안내를_전송하고_기존_예약은_유지된다() throws Exception {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1"))
                .willReturn("D-REVIEWER");
        String payloadJson = objectMapper.writeValueAsString(openReviewSchedulerPayload(metaJsonWithProjectId("123")));
        SlackInteractionInbox inbox = savePendingInbox(SlackInteractionInboxType.BLOCK_ACTIONS, payloadJson);

        // when
        slackInteractionInboxEntryProcessor.processClaimedBlockAction(inbox.getId(), CLAIMED_PROCESSING_STARTED_AT);

        // then
        SlackInteractionInbox actualInbox = slackInteractionInboxRepository.findById(inbox.getId())
                                                                           .orElseThrow();

        assertAll(
                () -> assertThat(actualInbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSED),
                () -> assertThat(actualInbox.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(reviewReservationRepository.findById(100L))
                        .map(reservation -> reservation.getStatus())
                        .hasValue(ReservationStatus.ACTIVE)
        );
        verify(notificationApiClient).openDirectMessageChannel("xoxb-test-token", "U1");
        verify(notificationApiClient).sendBlockMessage(
                        eq("xoxb-test-token"),
                        eq("D-REVIEWER"),
                        any(),
                        any()
                );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interaction/project_123.sql"
    })
    void view_submission_엔트리_처리시_PROCESSED로_마킹되고_리뷰_예약이_생성된다() throws Exception {
        // given
        String payloadJson = objectMapper.writeValueAsString(viewSubmissionPayload("30"));
        SlackInteractionInbox inbox = savePendingInbox(SlackInteractionInboxType.VIEW_SUBMISSION, payloadJson);

        // when
        slackInteractionInboxEntryProcessor.processClaimedViewSubmission(inbox.getId(), CLAIMED_PROCESSING_STARTED_AT);

        // then
        SlackInteractionInbox actualInbox = slackInteractionInboxRepository.findById(inbox.getId())
                                                                           .orElseThrow();

        assertAll(
                () -> assertThat(actualInbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSED),
                () -> assertThat(actualInbox.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(reviewReservationRepository.findActive("T1", 123L, "U1"))
                        .map(reservation -> reservation.getReservationPullRequest().getGithubPullRequestId())
                        .hasValue(10L)
        );
    }

    @Test
    void block_actions_엔트리_비즈니스_유효성_실패는_FAILED와_BUSINESS_INVARIANT로_기록된다() {
        // given
        SlackInteractionInbox inbox = savePendingInbox(SlackInteractionInboxType.BLOCK_ACTIONS, "{invalid-json");

        // when
        slackInteractionInboxEntryProcessor.processClaimedBlockAction(inbox.getId(), CLAIMED_PROCESSING_STARTED_AT);

        // then
        SlackInteractionInbox actual = slackInteractionInboxRepository.findById(inbox.getId())
                                                                      .orElseThrow();

        assertAll(
                () -> assertThat(actual.getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED),
                () -> assertThat(actual.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actual.getFailure().type()).isEqualTo(SlackInteractionFailureType.BUSINESS_INVARIANT),
                () -> assertThat(actual.getFailure().reason()).isNotBlank()
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interaction/active_review_reservation_t1_project_123_u1.sql"
    })
    void inbox_최종_저장에_실패하면_비즈니스_로직도_같이_롤백된다() throws Exception {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1"))
                .willReturn("D-REVIEWER");
        String payloadJson = objectMapper.writeValueAsString(cancelReservationPayload("100"));
        SlackInteractionInbox inbox = savePendingInbox(SlackInteractionInboxType.BLOCK_ACTIONS, payloadJson);

        doThrow(new IllegalStateException("forced save failure"))
                .when(slackInteractionInboxRepository)
                .saveIfProcessingLeaseMatched(
                        argThat(savedInbox -> savedInbox != null && inbox.getId().equals(savedInbox.getId())),
                        any(),
                        eq(CLAIMED_PROCESSING_STARTED_AT)
                );

        // when & then
        assertThatThrownBy(() -> slackInteractionInboxEntryProcessor.processClaimedBlockAction(
                inbox.getId(),
                CLAIMED_PROCESSING_STARTED_AT
        ))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("forced save failure");

        SlackInteractionInbox actualInbox = slackInteractionInboxRepository.findById(inbox.getId())
                                                                           .orElseThrow();
        ReviewReservation actualReservation = reviewReservationRepository.findById(100L).orElseThrow();

        assertAll(
                () -> assertThat(actualInbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSING),
                () -> assertThat(actualInbox.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actualReservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE)
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/reservation/project_123.sql",
            "classpath:sql/fixtures/interaction/active_review_reservation_t1_project_123_u1.sql"
    })
    void block_actions_엔트리_비즈니스_실패시_history가_저장된다() throws Exception {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1"))
                .willReturn("D-REVIEWER");
        doThrow(new IllegalStateException("forced notification failure"))
                .when(notificationApiClient)
                .sendBlockMessage(
                        eq("xoxb-test-token"),
                        eq("D-REVIEWER"),
                        any(),
                        any()
                );
        String payloadJson = objectMapper.writeValueAsString(openReviewSchedulerPayload(metaJsonWithProjectId("123")));
        SlackInteractionInbox inbox = savePendingInbox(SlackInteractionInboxType.BLOCK_ACTIONS, payloadJson);

        // when
        slackInteractionInboxEntryProcessor.processClaimedBlockAction(inbox.getId(), CLAIMED_PROCESSING_STARTED_AT);

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            SlackInteractionInbox actualInbox = slackInteractionInboxRepository.findById(inbox.getId())
                                                                               .orElseThrow();
            List<SlackInteractionInboxHistory> histories = historiesOf(inbox.getId());

            assertAll(
                    () -> assertThat(actualInbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED),
                    () -> assertThat(actualInbox.getProcessingAttempt()).isEqualTo(1),
                    () -> assertThat(actualInbox.getFailure().type()).isEqualTo(SlackInteractionFailureType.BUSINESS_INVARIANT),
                    () -> assertThat(histories).hasSize(1),
                    () -> assertThat(histories.getFirst().getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED),
                    () -> assertThat(histories.getFirst().getFailure().type())
                            .isEqualTo(SlackInteractionFailureType.BUSINESS_INVARIANT),
                    () -> assertThat(reviewReservationRepository.findById(100L))
                            .map(reservation -> reservation.getStatus())
                            .hasValue(ReservationStatus.ACTIVE)
            );
        });
        verify(notificationApiClient).openDirectMessageChannel("xoxb-test-token", "U1");
        verify(notificationApiClient).sendBlockMessage(
                eq("xoxb-test-token"),
                eq("D-REVIEWER"),
                any(),
                any()
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interaction/active_review_reservation_t1_project_123_u1.sql"
    })
    void timeout_recovery가_먼저_반영되면_늦은_완료는_최종_상태를_덮어쓰지_못한다() throws Exception {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1"))
                .willReturn("D-REVIEWER");
        String payloadJson = objectMapper.writeValueAsString(cancelReservationPayload("100"));
        SlackInteractionInbox inbox = savePendingInbox(SlackInteractionInboxType.BLOCK_ACTIONS, payloadJson);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        willAnswer(invocation -> {
            transactionTemplate.executeWithoutResult(status -> slackInteractionInboxRepository.recoverTimeoutProcessing(
                    SlackInteractionInboxType.BLOCK_ACTIONS,
                    CLAIMED_PROCESSING_STARTED_AT.plusSeconds(1),
                    CLAIMED_PROCESSING_STARTED_AT.plusSeconds(2),
                    "timeout",
                    3,
                    10
            ));
            return null;
        }).given(notificationApiClient).sendBlockMessage(
                eq("xoxb-test-token"),
                eq("D-REVIEWER"),
                any(),
                any()
        );

        // when & then
        assertThatThrownBy(() -> slackInteractionInboxEntryProcessor.processClaimedBlockAction(
                inbox.getId(),
                CLAIMED_PROCESSING_STARTED_AT
        ))
                .isInstanceOf(InboxProcessingLeaseLostException.class)
                .hasMessageContaining("processing lease");

        // then
        SlackInteractionInbox actualInbox = slackInteractionInboxRepository.findById(inbox.getId())
                                                                           .orElseThrow();
        List<SlackInteractionInboxHistory> histories = historiesOf(inbox.getId());

        assertAll(
                () -> assertThat(actualInbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.RETRY_PENDING),
                () -> assertThat(actualInbox.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actualInbox.getFailure().type()).isEqualTo(SlackInteractionFailureType.PROCESSING_TIMEOUT),
                () -> assertThat(histories).hasSize(1),
                () -> assertThat(histories.getFirst().getStatus()).isEqualTo(SlackInteractionInboxStatus.RETRY_PENDING),
                () -> assertThat(histories.getFirst().getFailure().type())
                        .isEqualTo(SlackInteractionFailureType.PROCESSING_TIMEOUT),
                () -> assertThat(reviewReservationRepository.findById(100L))
                        .map(reservation -> reservation.getStatus())
                        .hasValue(ReservationStatus.ACTIVE)
        );
        verify(notificationApiClient).openDirectMessageChannel("xoxb-test-token", "U1");
        verify(notificationApiClient).sendBlockMessage(
                eq("xoxb-test-token"),
                eq("D-REVIEWER"),
                any(),
                any()
        );
    }

    private SlackInteractionInbox savePendingInbox(SlackInteractionInboxType interactionType, String payloadJson) {
        String idempotencyKey = interactionType + "-entry-" + System.nanoTime();
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(interactionType, idempotencyKey, payloadJson);
        inbox.claim(CLAIMED_PROCESSING_STARTED_AT);
        ReflectionTestUtils.setField(inbox, "processingAttempt", 1);
        return slackInteractionInboxRepository.save(inbox);
    }

    private ObjectNode cancelReservationPayload(String reservationId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("team", objectMapper.createObjectNode().put("id", "T1"));
        payload.set("channel", objectMapper.createObjectNode().put("id", "C1"));
        payload.set("user", objectMapper.createObjectNode().put("id", "U1"));
        payload.set("actions", actions(BlockActionType.CANCEL_REVIEW_RESERVATION.value(), reservationId));
        return payload;
    }

    private ObjectNode openReviewSchedulerPayload(String metaJson) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("team", objectMapper.createObjectNode().put("id", "T1"));
        payload.set("channel", objectMapper.createObjectNode().put("id", "C1"));
        payload.set("user", objectMapper.createObjectNode().put("id", "U1"));
        payload.put("trigger_id", "TRIGGER_1");
        payload.set("actions", actions(BlockActionType.OPEN_REVIEW_SCHEDULER.value(), metaJson));
        return payload;
    }

    private ObjectNode viewSubmissionPayload(String selectedOption) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "view_submission");
        payload.set("team", objectMapper.createObjectNode().put("id", "T1"));
        payload.set("user", objectMapper.createObjectNode().put("id", "U1"));

        ObjectNode view = objectMapper.createObjectNode();
        view.put("callback_id", "review_time_submit");
        view.put("private_metadata", metaJsonWithProjectId("123"));

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

    private String metaJsonWithProjectId(String projectId) {
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
        return meta.toString();
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
