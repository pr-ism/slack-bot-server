package com.slack.bot.application.interactivity.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.command.exception.WorkspaceNotFoundException;
import com.slack.bot.application.interactivity.reply.dto.response.SlackActionResponse;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ViewSubmissionRouterTest {

    @Autowired
    ViewSubmissionRouter viewSubmissionRouter;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void team_ID가_없으면_예외가_발생한다() {
        // given
        JsonNode payload = payloadWithoutTeam(ViewCallbackId.REVIEW_TIME_SUBMIT.value(), metaJson("123"));

        // when & then
        assertThatThrownBy(() -> viewSubmissionRouter.handle(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("team_id");
    }

    @Test
    void 워크스페이스가_없으면_예외가_발생한다() {
        // given
        JsonNode payload = payload("T404", ViewCallbackId.REVIEW_TIME_SUBMIT.value(), metaJson("123"), "");

        // when & then
        assertThatThrownBy(() -> viewSubmissionRouter.handle(payload))
                .isInstanceOf(WorkspaceNotFoundException.class);
    }

    @Test
    @Sql("classpath:sql/fixtures/notification/workspace_t1.sql")
    void 알_수_없는_callback_ID면_빈_응답을_반환한다() {
        // given
        JsonNode payload = payload("T1", "unknown", metaJson("123"), "");

        // when
        Object actual = viewSubmissionRouter.handle(payload);

        // then
        assertThat(actual).isEqualTo(SlackActionResponse.empty());
    }

    @Test
    @Sql("classpath:sql/fixtures/notification/workspace_t1.sql")
    void 기본_시간_제출_callback이면_프로세서를_통해_응답을_반환한다() {
        // given
        JsonNode payload = payload("T1", ViewCallbackId.REVIEW_TIME_SUBMIT.value(), metaJson("123"), "");

        // when
        Object actual = viewSubmissionRouter.handle(payload);

        // then
        assertThat(actual).isEqualTo(SlackActionResponse.empty());
    }

    private JsonNode payload(String teamId, String callbackId, String privateMetadata, String selectedOption) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("team", objectMapper.createObjectNode().put("id", teamId));
        payload.set("user", objectMapper.createObjectNode().put("id", "U1"));

        ObjectNode view = objectMapper.createObjectNode();
        view.put("callback_id", callbackId);
        view.put("private_metadata", privateMetadata);

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

    private JsonNode payloadWithoutTeam(String callbackId, String privateMetadata) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("user", objectMapper.createObjectNode().put("id", "U1"));

        ObjectNode view = objectMapper.createObjectNode();
        view.put("callback_id", callbackId);
        view.put("private_metadata", privateMetadata);
        payload.set("view", view);

        return payload;
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
                .put("reservation_id", "R1")
                .toString();
    }
}
