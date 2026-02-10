package com.slack.bot.application.interactivity.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import com.slack.bot.application.interactivity.reply.dto.response.SlackActionResponse;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewTimeSubmissionProcessorTest {

    @Autowired
    ReviewTimeSubmissionProcessor reviewTimeSubmissionProcessor;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void 커스텀_시간_선택이면_커스텀_모달_push_응답을_반환한다() {
        // given
        JsonNode payload = defaultSubmitPayload("custom");
        ReviewScheduleMetaDto meta = meta("123");

        // when
        SlackActionResponse actual = reviewTimeSubmissionProcessor.handleDefaultTimeSubmit(
                payload,
                metaJson("123"),
                meta,
                "U1",
                "xoxb-test-token"
        );

        // then
        assertAll(
                () -> assertThat(actual.responseAction()).isEqualTo("push")
        );
    }

    @Test
    void 리뷰_시작_시간_값이_잘못되면_errors_응답을_반환한다() {
        // given
        JsonNode payload = defaultSubmitPayload("wrong-value");
        ReviewScheduleMetaDto meta = meta("123");

        // when
        SlackActionResponse actual = reviewTimeSubmissionProcessor.handleDefaultTimeSubmit(
                payload,
                metaJson("123"),
                meta,
                "U1",
                "xoxb-test-token"
        );

        // then
        assertAll(
                () -> assertThat(actual.responseAction()).isEqualTo("errors"),
                () -> assertThat(actual.errors()).containsKey("time_block")
        );
    }

    @Test
    @Sql("classpath:sql/fixtures/reservation/project_123.sql")
    void 분_옵션을_선택하면_예약_워크플로우를_수행한다() {
        // given
        JsonNode payload = defaultSubmitPayload("30");
        ReviewScheduleMetaDto meta = meta("123");

        // when
        SlackActionResponse actual = reviewTimeSubmissionProcessor.handleDefaultTimeSubmit(
                payload,
                metaJson("123"),
                meta,
                "U1",
                "xoxb-test-token"
        );

        // then
        assertThat(actual).isEqualTo(SlackActionResponse.clear());
    }

    @Test
    void 커스텀_시간_입력이_잘못되면_errors_응답을_반환한다() {
        // given
        JsonNode payload = customSubmitPayload("2026-02-10", "99:10");
        ReviewScheduleMetaDto meta = meta("123");

        // when
        SlackActionResponse actual = reviewTimeSubmissionProcessor.handleCustomTimeSubmit(
                payload,
                meta,
                "U1",
                "xoxb-test-token"
        );

        // then
        assertAll(
                () -> assertThat(actual.responseAction()).isEqualTo("errors"),
                () -> assertThat(actual.errors()).containsKey("time_block")
        );
    }

    private JsonNode defaultSubmitPayload(String selectedOption) {
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode view = objectMapper.createObjectNode();
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

    private JsonNode customSubmitPayload(String date, String time) {
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode view = objectMapper.createObjectNode();
        ObjectNode state = objectMapper.createObjectNode();
        ObjectNode values = objectMapper.createObjectNode();

        ObjectNode dateBlock = objectMapper.createObjectNode();
        dateBlock.set("date_action", objectMapper.createObjectNode().put("selected_date", date));

        ObjectNode timeBlock = objectMapper.createObjectNode();
        timeBlock.set("time_action", objectMapper.createObjectNode().put("value", time));

        values.set("date_block", dateBlock);
        values.set("time_block", timeBlock);
        state.set("values", values);
        view.set("state", state);
        payload.set("view", view);

        return payload;
    }

    private ReviewScheduleMetaDto meta(String projectId) {
        return ReviewScheduleMetaDto.builder()
                                    .teamId("T1")
                                    .channelId("C1")
                                    .pullRequestId(10L)
                                    .pullRequestNumber(10)
                                    .pullRequestTitle("PR 제목")
                                    .pullRequestUrl("https://github.com/org/repo/pull/10")
                                    .authorGithubId("author-gh")
                                    .authorSlackId("U_AUTHOR")
                                    .reservationId(null)
                                    .projectId(projectId)
                                    .build();
    }

    private String metaJson(String projectId) {
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
