package com.slack.bot.application.interactivity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.block.BlockActionType;
import com.slack.bot.application.interactivity.box.in.SlackInteractionInboxProcessor;
import com.slack.bot.application.interactivity.client.NotificationApiClient;
import com.slack.bot.domain.reservation.ReservationStatus;
import com.slack.bot.domain.reservation.repository.ReviewReservationRepository;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.AopTestUtils;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BlockActionInteractionServiceTest {

    @Autowired
    BlockActionInteractionService blockActionInteractionService;

    @Autowired
    NotificationApiClient notificationApiClient;

    @Autowired
    SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    @Autowired
    SlackInteractionInboxRepository slackInteractionInboxRepository;

    @Autowired
    ReviewReservationRepository reviewReservationRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/reservation/project_123.sql",
            "classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql"
    })
    void 동일_PR에_이미_활성_리뷰_예약이_있을때_스케줄러를_다시_열면_중복_예약_안내를_보낸다() {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1"))
                .willReturn("D-REVIEWER");
        JsonNode payload = openReviewSchedulerPayload(metaJsonWithProjectId("123"));

        // when
        targetService().handle(payload);

        // then
        assertAll(
                () -> verify(notificationApiClient).openDirectMessageChannel("xoxb-test-token", "U1"),
                () -> verify(notificationApiClient).sendBlockMessage(
                        eq("xoxb-test-token"),
                        eq("D-REVIEWER"),
                        any(),
                        any()
                ),
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
    void 예약자가_리뷰_예약_취소_버튼을_누르면_예약은_CANCELLED로_전이되고_취소_안내를_보낸다() {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1"))
                .willReturn("D-REVIEWER");
        JsonNode payload = cancelReservationPayload("100");

        // when
        targetService().handle(payload);

        // then
        assertAll(
                () -> verify(notificationApiClient).openDirectMessageChannel("xoxb-test-token", "U1"),
                () -> verify(notificationApiClient).sendBlockMessage(
                        eq("xoxb-test-token"),
                        eq("D-REVIEWER"),
                        any(),
                        any()
                ),
                () -> assertThat(reviewReservationRepository.findById(100L))
                        .isPresent()
                        .get()
                        .extracting(reservation -> reservation.getStatus())
                        .isEqualTo(ReservationStatus.CANCELLED)
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql"
    })
    void block_action_핸들_호출은_AOP로_인박스에_enqueue되고_워커_처리후에도_예약상태는_유지된다() {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1"))
                .willReturn("D-REVIEWER");
        JsonNode payload = cancelReservationPayload("100");

        // when
        blockActionInteractionService.handle(payload);
        List<SlackInteractionInbox> pendings = slackInteractionInboxRepository.findPending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                10
        );

        // then
        assertAll(
                () -> assertThat(pendings).hasSize(1),
                () -> verify(notificationApiClient, never()).openDirectMessageChannel(any(), any()),
                () -> assertThat(reviewReservationRepository.findById(100L))
                        .isPresent()
                        .get()
                        .extracting(reservation -> reservation.getStatus())
                        .isEqualTo(ReservationStatus.ACTIVE)
        );

        // when
        slackInteractionInboxProcessor.processPendingBlockActions(10);

        // then
        assertAll(
                () -> verify(notificationApiClient, never()).openDirectMessageChannel(any(), any()),
                () -> assertThat(reviewReservationRepository.findById(100L))
                        .isPresent()
                        .get()
                        .extracting(reservation -> reservation.getStatus())
                        .isEqualTo(ReservationStatus.ACTIVE)
        );
    }

    private JsonNode openReviewSchedulerPayload(String metaJson) {
        ObjectNode payload = basePayload();
        payload.put("trigger_id", "TRIGGER_1");
        payload.set("actions", actions(BlockActionType.OPEN_REVIEW_SCHEDULER.value(), metaJson));
        return payload;
    }

    private JsonNode cancelReservationPayload(String reservationId) {
        ObjectNode payload = basePayload();
        payload.set("actions", actions(BlockActionType.CANCEL_REVIEW_RESERVATION.value(), reservationId));
        return payload;
    }

    private ObjectNode basePayload() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("team", objectMapper.createObjectNode().put("id", "T1"));
        payload.set("channel", objectMapper.createObjectNode().put("id", "C1"));
        payload.set("user", objectMapper.createObjectNode().put("id", "U1"));
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
        return objectMapper.createObjectNode()
                           .put("team_id", "T1")
                           .put("channel_id", "C1")
                           .put("pull_request_id", 10L)
                           .put("pull_request_number", 10)
                           .put("pull_request_title", "PR 제목")
                           .put("pull_request_url", "https://github.com/org/repo/pull/10")
                           .put("project_id", projectId)
                           .put("author_github_id", "author-gh")
                           .put("author_slack_id", "U_AUTHOR")
                           .put("reservation_id", "R1")
                           .toString();
    }

    private BlockActionInteractionService targetService() {
        return AopTestUtils.getTargetObject(blockActionInteractionService);
    }
}
