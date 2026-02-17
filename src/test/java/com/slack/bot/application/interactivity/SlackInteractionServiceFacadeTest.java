package com.slack.bot.application.interactivity;

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
import com.slack.bot.application.interactivity.reply.dto.response.SlackActionResponse;
import com.slack.bot.domain.reservation.ReservationStatus;
import com.slack.bot.domain.reservation.repository.ReviewReservationRepository;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackInteractionServiceFacadeTest {

    @Autowired
    SlackInteractionServiceFacade slackInteractionServiceFacade;

    @Autowired
    NotificationApiClient notificationApiClient;

    @Autowired
    ReviewReservationRepository reviewReservationRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql"
    })
    void 슬랙_type이_누락된_인터랙션은_block_actions로_간주되어_리뷰_예약_취소_도메인_흐름을_수행한다() throws Exception {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1"))
                .willReturn("D-REVIEWER");
        String payloadJson = objectMapper.writeValueAsString(cancelReservationPayloadWithoutType("100"));

        // when
        SlackActionResponse actual = slackInteractionServiceFacade.handle(payloadJson);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(SlackActionResponse.empty()),
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
    void view_submission_요청은_블록_액션_처리를_건너뛰고_모달_제출_라우터로_흘러간다() throws Exception {
        // given
        String payloadJson = objectMapper.writeValueAsString(viewSubmissionPayloadWithUnknownCallback());

        // when
        SlackActionResponse actual = slackInteractionServiceFacade.handle(payloadJson);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(SlackActionResponse.empty()),
                () -> verify(notificationApiClient, never()).openDirectMessageChannel(any(), any()),
                () -> assertThat(reviewReservationRepository.findById(100L))
                        .isPresent()
                        .get()
                        .extracting(reservation -> reservation.getStatus())
                        .isEqualTo(ReservationStatus.ACTIVE)
        );
    }

    @Test
    void 슬랙_인터랙션_payload가_JSON으로_해석되지_않으면_예외를_삼키고_빈_응답을_반환한다() {
        // given
        String invalidPayload = "{invalid-json";

        // when
        SlackActionResponse actual = slackInteractionServiceFacade.handle(invalidPayload);

        // then
        assertThat(actual).isEqualTo(SlackActionResponse.empty());
    }

    @Test
    void 등록되지_않은_워크스페이스의_블록_액션이면_도메인_예외를_삼키고_빈_응답을_반환한다() throws Exception {
        // given
        String payloadJson = objectMapper.writeValueAsString(cancelReservationPayloadWithTeam("T404", "100"));

        // when
        SlackActionResponse actual = slackInteractionServiceFacade.handle(payloadJson);

        // then
        assertThat(actual).isEqualTo(SlackActionResponse.empty());
    }

    @Test
    void view_submission_요청에서_team식별자가_누락되면_입력값_예외를_삼키고_빈_응답을_반환한다() throws Exception {
        // given
        String payloadJson = objectMapper.writeValueAsString(viewSubmissionPayloadWithoutTeam());

        // when
        SlackActionResponse actual = slackInteractionServiceFacade.handle(payloadJson);

        // then
        assertThat(actual).isEqualTo(SlackActionResponse.empty());
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interactivity/active_review_reservation_t1_project_123_u1.sql"
    })
    void 블록_알림_전달중_예상치못한_런타임_예외가_나도_슬랙_재시도를_피하기위해_빈_응답을_반환한다() throws Exception {
        // given
        given(notificationApiClient.openDirectMessageChannel("xoxb-test-token", "U1"))
                .willThrow(new RuntimeException("테스트 런타임 예외"));
        String payloadJson = objectMapper.writeValueAsString(cancelReservationPayloadWithoutType("100"));

        // when
        SlackActionResponse actual = slackInteractionServiceFacade.handle(payloadJson);

        // then
        assertThat(actual).isEqualTo(SlackActionResponse.empty());
    }

    private ObjectNode cancelReservationPayloadWithoutType(String reservationId) {
        return cancelReservationPayloadWithTeam("T1", reservationId);
    }

    private ObjectNode cancelReservationPayloadWithTeam(String teamId, String reservationId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("team", objectMapper.createObjectNode().put("id", teamId));
        payload.set("channel", objectMapper.createObjectNode().put("id", "C1"));
        payload.set("user", objectMapper.createObjectNode().put("id", "U1"));
        payload.set("actions", actions(BlockActionType.CANCEL_REVIEW_RESERVATION.value(), reservationId));
        return payload;
    }

    private ObjectNode viewSubmissionPayloadWithUnknownCallback() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "view_submission");
        payload.set("team", objectMapper.createObjectNode().put("id", "T1"));
        payload.set("user", objectMapper.createObjectNode().put("id", "U1"));

        ObjectNode view = objectMapper.createObjectNode();
        view.put("callback_id", "unknown");
        view.put("private_metadata", metaJsonWithProjectId("123"));
        view.set("state", objectMapper.createObjectNode().set("values", objectMapper.createObjectNode()));
        payload.set("view", view);
        return payload;
    }

    private ObjectNode viewSubmissionPayloadWithoutTeam() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "view_submission");
        payload.set("user", objectMapper.createObjectNode().put("id", "U1"));

        ObjectNode view = objectMapper.createObjectNode();
        view.put("callback_id", "review_time_submit");
        view.put("private_metadata", metaJsonWithProjectId("123"));
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
}
