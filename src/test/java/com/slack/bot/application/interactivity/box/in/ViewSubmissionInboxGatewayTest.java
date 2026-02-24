package com.slack.bot.application.interactivity.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.box.ProcessingSourceContext;
import com.slack.bot.application.interactivity.reply.dto.response.SlackActionResponse;
import com.slack.bot.application.interactivity.view.dto.ViewSubmissionImmediateDto;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ViewSubmissionInboxGatewayTest {

    @Autowired
    ViewSubmissionInboxGateway viewSubmissionInboxGateway;

    @Autowired
    SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    @Autowired
    ProcessingSourceContext processingSourceContext;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void inbox_외부_실행에서_now_선택이면_clear_응답을_반환하고_enqueue를_시도한다() {
        // given
        JsonNode payload = defaultSubmitPayload("now");
        doReturn(false)
                .when(slackInteractionInboxProcessor)
                .enqueueViewSubmission(payload.toString());

        // when
        ViewSubmissionImmediateDto actual = viewSubmissionInboxGateway.handle(payload);

        // then
        assertAll(
                () -> assertThat(actual.response()).isEqualTo(SlackActionResponse.clear()),
                () -> assertThat(actual.shouldEnqueue()).isTrue()
        );

        verify(slackInteractionInboxProcessor).enqueueViewSubmission(payload.toString());
    }

    @Test
    void inbox_외부_실행에서_custom_선택이면_enqueue하지_않고_push_응답을_반환한다() {
        // given
        JsonNode payload = defaultSubmitPayload("custom");

        // when
        ViewSubmissionImmediateDto actual = viewSubmissionInboxGateway.handle(payload);

        // then
        assertAll(
                () -> assertThat(actual.shouldEnqueue()).isFalse(),
                () -> assertThat(actual.response().responseAction()).isEqualTo("push")
        );

        verify(slackInteractionInboxProcessor, never()).enqueueViewSubmission(anyString());
    }

    @Test
    void inbox_내부_실행이면_shouldEnqueue_true여도_enqueue하지_않는다() {
        // given
        JsonNode payload = defaultSubmitPayload("now");

        // when
        ViewSubmissionImmediateDto actual = processingSourceContext.withInboxProcessing(
                () -> viewSubmissionInboxGateway.handle(payload)
        );

        // then
        assertAll(
                () -> assertThat(actual.response()).isEqualTo(SlackActionResponse.clear()),
                () -> assertThat(actual.shouldEnqueue()).isTrue()
        );
        verify(slackInteractionInboxProcessor, never()).enqueueViewSubmission(anyString());
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
