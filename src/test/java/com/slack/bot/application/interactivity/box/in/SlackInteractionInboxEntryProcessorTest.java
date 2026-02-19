package com.slack.bot.application.interactivity.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.block.BlockActionType;
import com.slack.bot.application.interactivity.client.NotificationApiClient;
import com.slack.bot.domain.reservation.ReservationStatus;
import com.slack.bot.domain.reservation.ReviewReservation;
import com.slack.bot.domain.reservation.repository.ReviewReservationRepository;
import com.slack.bot.infrastructure.interaction.box.SlackInteractivityFailureType;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxStatus;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
import com.slack.bot.infrastructure.interaction.box.persistence.in.JpaSlackInteractionInboxRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackInteractionInboxEntryProcessorTest {

    @Autowired
    SlackInteractionInboxEntryProcessor slackInteractionInboxEntryProcessor;

    @Autowired
    SlackInteractionInboxRepository slackInteractionInboxRepository;

    @Autowired
    JpaSlackInteractionInboxRepository jpaSlackInteractionInboxRepository;

    @Autowired
    ReviewReservationRepository reviewReservationRepository;

    @Autowired
    NotificationApiClient notificationApiClient;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql"
    })
    void block_actions_엔트리_처리시_PROCESSED로_마킹되고_취소_예약_흐름이_수행된다() throws Exception {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1"))
                .willReturn("D-REVIEWER");
        String payloadJson = objectMapper.writeValueAsString(cancelReservationPayload("100"));
        SlackInteractionInbox inbox = savePendingInbox(SlackInteractionInboxType.BLOCK_ACTIONS, payloadJson);

        // when
        slackInteractionInboxEntryProcessor.processBlockAction(inbox);

        // then
        SlackInteractionInbox actualInbox = jpaSlackInteractionInboxRepository.findById(inbox.getId()).orElseThrow();
        Optional<ReviewReservation> actualReservation = reviewReservationRepository.findById(100L);

        assertAll(
                () -> assertThat(actualInbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSED),
                () -> assertThat(actualInbox.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actualReservation).isPresent(),
                () -> assertThat(actualReservation.get().getStatus()).isEqualTo(ReservationStatus.CANCELLED),
                () -> verify(notificationApiClient).openDirectMessageChannel("xoxb-test-token", "U1"),
                () -> verify(notificationApiClient).sendBlockMessage(
                        eq("xoxb-test-token"),
                        eq("D-REVIEWER"),
                        any(),
                        any()
                )
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/reservation/project_123.sql",
            "classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql"
    })
    void block_actions_엔트리_처리시_중복_예약_안내를_전송하고_기존_예약은_유지된다() throws Exception {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1"))
                .willReturn("D-REVIEWER");
        String payloadJson = objectMapper.writeValueAsString(openReviewSchedulerPayload(metaJsonWithProjectId("123")));
        SlackInteractionInbox inbox = savePendingInbox(SlackInteractionInboxType.BLOCK_ACTIONS, payloadJson);

        // when
        slackInteractionInboxEntryProcessor.processBlockAction(inbox);

        // then
        SlackInteractionInbox actualInbox = jpaSlackInteractionInboxRepository.findById(inbox.getId()).orElseThrow();
        Optional<ReviewReservation> actualReservation = reviewReservationRepository.findById(100L);

        assertAll(
                () -> assertThat(actualInbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSED),
                () -> assertThat(actualInbox.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actualReservation).isPresent(),
                () -> assertThat(actualReservation.get().getStatus()).isEqualTo(ReservationStatus.ACTIVE),
                () -> verify(notificationApiClient).openDirectMessageChannel("xoxb-test-token", "U1"),
                () -> verify(notificationApiClient).sendBlockMessage(
                        eq("xoxb-test-token"),
                        eq("D-REVIEWER"),
                        any(),
                        any()
                )
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interactivity/project_123.sql"
    })
    void view_submission_엔트리_처리시_PROCESSED로_마킹되고_리뷰_예약이_생성된다() throws Exception {
        // given
        String payloadJson = objectMapper.writeValueAsString(viewSubmissionPayload("30"));
        SlackInteractionInbox inbox = savePendingInbox(SlackInteractionInboxType.VIEW_SUBMISSION, payloadJson);

        // when
        slackInteractionInboxEntryProcessor.processViewSubmission(inbox);

        // then
        SlackInteractionInbox actualInbox = jpaSlackInteractionInboxRepository.findById(inbox.getId()).orElseThrow();
        Optional<ReviewReservation> actualReservation = reviewReservationRepository.findActive("T1", 123L, "U1");

        assertAll(
                () -> assertThat(actualInbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.PROCESSED),
                () -> assertThat(actualInbox.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actualReservation).isPresent(),
                () -> assertThat(actualReservation.get().getReservationPullRequest().getPullRequestId()).isEqualTo(10L)
        );
    }

    @Test
    void block_actions_엔트리_비즈니스_유효성_실패는_FAILED와_BUSINESS_INVARIANT로_기록된다() {
        // given
        SlackInteractionInbox inbox = savePendingInbox(SlackInteractionInboxType.BLOCK_ACTIONS, "{invalid-json");

        // when
        slackInteractionInboxEntryProcessor.processBlockAction(inbox);

        // then
        SlackInteractionInbox actualInbox = jpaSlackInteractionInboxRepository.findById(inbox.getId()).orElseThrow();

        assertAll(
                () -> assertThat(actualInbox.getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED),
                () -> assertThat(actualInbox.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(actualInbox.getFailureType()).isEqualTo(SlackInteractivityFailureType.BUSINESS_INVARIANT),
                () -> assertThat(actualInbox.getFailureReason()).isNotBlank()
        );
    }

    private SlackInteractionInbox savePendingInbox(SlackInteractionInboxType interactionType, String payloadJson) {
        String idempotencyKey = interactionType + "-entry-" + System.nanoTime();
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(interactionType, idempotencyKey, payloadJson);
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
                                      .put("pull_request_id", 10L)
                                      .put("pull_request_number", 10)
                                      .put("pull_request_title", "PR 제목")
                                      .put("pull_request_url", "https://github.com/org/repo/pull/10")
                                      .put("project_id", projectId)
                                      .put("author_github_id", "author-gh")
                                      .put("author_slack_id", "U_AUTHOR");
        return meta.toString();
    }
}
