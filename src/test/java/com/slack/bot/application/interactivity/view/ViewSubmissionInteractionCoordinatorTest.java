package com.slack.bot.application.interactivity.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.reply.dto.response.SlackActionResponse;
import com.slack.bot.application.interactivity.view.dto.ViewSubmissionImmediateDto;
import com.slack.bot.domain.reservation.ReviewReservation;
import com.slack.bot.domain.reservation.repository.ReviewReservationRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ViewSubmissionInteractionCoordinatorTest {

    @Autowired
    ViewSubmissionInteractionCoordinator viewSubmissionInteractionCoordinator;

    @Autowired
    ReviewReservationRepository reviewReservationRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void 기본_시간_now_선택_제출이면_clear_응답으로_enqueue를_지시한다() {
        // given
        JsonNode payload = defaultSubmitPayload("now");

        // when
        ViewSubmissionImmediateDto actual = viewSubmissionInteractionCoordinator.handle(payload);

        // then
        assertAll(
                () -> assertThat(actual.response()).isEqualTo(SlackActionResponse.clear()),
                () -> assertThat(actual.shouldEnqueue()).isTrue()
        );
    }

    @Test
    void 기본_시간_선택값이_잘못되면_errors_응답으로_enqueue를_건너뛴다() {
        // given
        JsonNode payload = defaultSubmitPayload("wrong-value");

        // when
        ViewSubmissionImmediateDto actual = viewSubmissionInteractionCoordinator.handle(payload);

        // then
        assertAll(
                () -> assertThat(actual.response().responseAction()).isEqualTo("errors"),
                () -> assertThat(actual.response().errors()).containsKey("time_block"),
                () -> assertThat(actual.shouldEnqueue()).isFalse()
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/notification/workspace_t1.sql",
            "classpath:sql/fixtures/interactivity/project_123.sql"
    })
    void enqueued_view_submission을_처리하면_리뷰_예약이_생성된다() {
        // given
        JsonNode payload = defaultSubmitPayload("30");

        // when
        viewSubmissionInteractionCoordinator.handleEnqueued(payload);

        // then
        Optional<ReviewReservation> actual = reviewReservationRepository.findActive("T1", 123L, "U1");

        assertAll(
                () -> assertThat(actual).isPresent(),
                () -> assertThat(actual.get().getReservationPullRequest().getPullRequestId()).isEqualTo(10L)
        );
    }

    private JsonNode defaultSubmitPayload(String selectedOption) {
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
                           .toString();
    }
}
