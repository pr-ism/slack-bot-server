package com.slack.bot.presentation.event;

import static com.slack.bot.docs.RestDocsConfiguration.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.event.SlackEventService;
import com.slack.bot.presentation.CommonControllerSliceTestSupport;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackEventControllerTest extends CommonControllerSliceTestSupport {

    @Autowired
    SlackEventService slackEventService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void url_verification_이벤트_성공_테스트() throws Exception {
        // given
        String challenge = "test-challenge";
        ObjectNode payload = objectMapper.createObjectNode();

        payload.put("type", "url_verification");
        payload.put("challenge", challenge);

        // when & then
        ResultActions resultActions = mockMvc.perform(
                post("/slack/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload))
        )
        .andExpect(status().isOk())
        .andExpect(content().string("\"" + challenge + "\""));

        verify(slackEventService, never()).handleEvent(any(JsonNode.class));

        url_verification_이벤트_문서화(resultActions);
    }

    private void url_verification_이벤트_문서화(ResultActions resultActions) throws Exception {
        resultActions.andDo(
                restDocs.document(
                        requestFields(
                                fieldWithPath("type").type(JsonFieldType.STRING)
                                                     .attributes(field("constraints", "url_verification 이벤트로 고정"))
                                                     .description("url_verification 이벤트 타입"),
                                fieldWithPath("challenge").type(JsonFieldType.STRING)
                                                          .attributes(field("constraints", "slack api event request url 입력 시 호출되는 값"))
                                                          .description("URL 검증용 챌린지 값")
                        )
                )
        );
    }

    @Test
    void 일반_이벤트_성공_테스트() throws Exception {
        // given
        ObjectNode event = objectMapper.createObjectNode();

        event.put("type", "member_joined_channel");

        ObjectNode payload = objectMapper.createObjectNode();

        payload.put("team_id", "T1");
        payload.set("event", event);

        // when & then
        ResultActions resultActions = mockMvc.perform(
                post("/slack/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload))
        ).andExpect(status().isOk());

        verify(slackEventService).handleEvent(payload);

        일반_이벤트_문서화(resultActions);
    }

    private void 일반_이벤트_문서화(ResultActions resultActions) throws Exception {
        resultActions.andDo(
                restDocs.document(
                        requestFields(
                                fieldWithPath("team_id").type(JsonFieldType.STRING)
                                                        .description("슬랙 팀(워크스페이스) ID"),
                                fieldWithPath("event").type(JsonFieldType.OBJECT)
                                                      .description("슬랙이 전달하는 이벤트 데이터"),
                                fieldWithPath("event.type").type(JsonFieldType.STRING)
                                                           .attributes(field("constraints", "member_joined_channel, app_uninstalled 이벤트 타입만 핸들링"))
                                                           .description("이벤트 종류")
                        )
                )
        );
    }
}
