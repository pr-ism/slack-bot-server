package com.slack.bot.application.interactivity.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
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
import com.slack.bot.infrastructure.interaction.persistence.box.in.JpaSlackInteractionInboxRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackInteractionInboxProcessorTest {

    @Autowired
    SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    @Autowired
    NotificationApiClient notificationApiClient;

    @Autowired
    ReviewReservationRepository reviewReservationRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JpaSlackInteractionInboxRepository jpaSlackInteractionInboxRepository;

    @Autowired
    SlackInteractionInboxRepository slackInteractionInboxRepository;

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql"
    })
    void block_actions_인박스를_처리하면_워커는_엔트리를_PROCESSED로_마킹한다() throws Exception {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1"))
                .willReturn("D-REVIEWER");
        String payloadJson = objectMapper.writeValueAsString(cancelReservationPayload("100"));

        // when
        boolean actual = slackInteractionInboxProcessor.enqueueBlockAction(payloadJson);

        slackInteractionInboxProcessor.processPendingBlockActions(10);

        // then
        assertAll(
                () -> assertThat(actual).isTrue(),
                () -> assertThat(slackInteractionInboxRepository.findPending(SlackInteractionInboxType.BLOCK_ACTIONS, 10)).isEmpty(),
                () -> verify(notificationApiClient, never()).openDirectMessageChannel(any(), any()),
                () -> assertThat(reviewReservationRepository.findById(100L))
                        .isPresent()
                        .get()
                        .extracting(reservation -> reservation.getStatus())
                        .isEqualTo(ReservationStatus.ACTIVE)
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql"
    })
    void 동일_payload를_중복_enqueue하면_한번만_적재되고_한번만_처리된다() throws Exception {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1"))
                .willReturn("D-REVIEWER");
        String payloadJson = objectMapper.writeValueAsString(cancelReservationPayload("100"));

        // when
        boolean first = slackInteractionInboxProcessor.enqueueBlockAction(payloadJson);
        boolean second = slackInteractionInboxProcessor.enqueueBlockAction(payloadJson);
        slackInteractionInboxProcessor.processPendingBlockActions(10);

        // then
        assertAll(
                () -> assertThat(first).isTrue(),
                () -> assertThat(second).isFalse(),
                () -> verify(notificationApiClient, never()).openDirectMessageChannel(any(), any())
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interactivity/project_123.sql"
    })
    void view_submission_인박스를_처리하면_리뷰_예약_워크플로우가_비동기로_수행된다() throws Exception {
        // given
        String payloadJson = objectMapper.writeValueAsString(viewSubmissionPayload("30"));

        // when
        boolean enqueued = slackInteractionInboxProcessor.enqueueViewSubmission(payloadJson);
        slackInteractionInboxProcessor.processPendingViewSubmissions(10);

        // then
        Optional<ReviewReservation> saved = reviewReservationRepository.findActive("T1", 123L, "U1");

        assertAll(
                () -> assertThat(enqueued).isTrue(),
                () -> assertThat(saved).isPresent(),
                () -> assertThat(saved.get().getReservationPullRequest().getPullRequestId()).isEqualTo(10L)
        );
    }

    @Test
    void 비즈니스_유효성_실패는_재시도하지않고_즉시_FAILED로_마킹된다() {
        // given
        String invalidPayload = "{invalid-json";

        // when
        boolean actual = slackInteractionInboxProcessor.enqueueBlockAction(invalidPayload);
        SlackInteractionInbox inbox = singleRetryableInbox(SlackInteractionInboxType.BLOCK_ACTIONS);

        slackInteractionInboxProcessor.processPendingBlockActions(10);
        SlackInteractionInbox afterFirst = jpaSlackInteractionInboxRepository.findById(inbox.getId()).orElseThrow();

        // then
        assertAll(
                () -> assertThat(actual).isTrue(),
                () -> assertThat(afterFirst.getStatus()).isEqualTo(SlackInteractionInboxStatus.FAILED),
                () -> assertThat(afterFirst.getProcessingAttempt()).isEqualTo(1),
                () -> assertThat(afterFirst.getFailureType()).isEqualTo(SlackInteractivityFailureType.BUSINESS_INVARIANT)
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
                                      .put("pull_request_id", 10L)
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

    private SlackInteractionInbox singleRetryableInbox(SlackInteractionInboxType type) {
        List<SlackInteractionInbox> inboxes = slackInteractionInboxRepository.findPending(type, 10);

        assertThat(inboxes).hasSize(1);
        return inboxes.getFirst();
    }
}
