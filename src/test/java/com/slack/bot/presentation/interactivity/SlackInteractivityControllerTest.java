package com.slack.bot.presentation.interactivity;

import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.formParameters;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.slack.bot.application.interactivity.SlackInteractionServiceFacade;
import com.slack.bot.application.interactivity.reply.dto.response.SlackActionResponse;
import com.slack.bot.global.security.SlackSignatureVerifier;
import com.slack.bot.presentation.CommonControllerSliceTestSupport;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackInteractivityControllerTest extends CommonControllerSliceTestSupport {

    @Autowired
    SlackSignatureVerifier slackSignatureVerifier;

    @Autowired
    SlackInteractionServiceFacade slackInteractionServiceFacade;

    @Test
    void 인터랙티브_요청_처리_성공_테스트() throws Exception {
        // given
        String payloadJson = """
                {"type":"block_actions"}
                """;
        String encodedPayload = URLEncoder.encode(payloadJson, StandardCharsets.UTF_8);
        String requestBody = "payload=" + encodedPayload;

        given(slackSignatureVerifier.verify("1700000000", "v0=signature", requestBody)).willReturn(true);
        given(slackInteractionServiceFacade.handle(payloadJson)).willReturn(SlackActionResponse.empty());

        // when & then
        ResultActions resultActions = mockMvc.perform(
                                                     post("/slack/interactive")
                                                             .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                                             .header("X-Slack-Request-Timestamp", "1700000000")
                                                             .header("X-Slack-Signature", "v0=signature")
                                                             .content(requestBody)
                                             )
                                             .andExpect(status().isOk())
                                             .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                                             .andExpect(content().json("{}"));

        인터랙티브_요청_처리_성공_문서화(resultActions);
    }

    @Test
    void 시그니처_검증에_실패하면_인터랙티브_요청은_실패한다() throws Exception {
        // given
        String payloadJson = """
                {"type":"block_actions"}
                """;
        String encodedPayload = URLEncoder.encode(payloadJson, StandardCharsets.UTF_8);
        String requestBody = "payload=" + encodedPayload;

        given(slackSignatureVerifier.verify("1700000000", "v0=bad-signature", requestBody)).willReturn(false);

        // when & then
        ResultActions resultActions = mockMvc.perform(
                                                     post("/slack/interactive")
                                                             .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                                             .header("X-Slack-Request-Timestamp", "1700000000")
                                                             .header("X-Slack-Signature", "v0=bad-signature")
                                                             .content(requestBody)
                                             )
                                             .andExpect(status().isUnauthorized())
                                             .andExpect(jsonPath("$.errorCode").value("A00"))
                                             .andExpect(jsonPath("$.message").value("슬랙 요청 시그니처 검증에 실패했습니다."));

        시그니처_검증_실패_문서화(resultActions);
    }

    @Test
    void payload가_비어_있으면_인터랙티브_요청은_실패한다() throws Exception {
        // when & then
        mockMvc.perform(
                       post("/slack/interactive")
                               .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                               .header("X-Slack-Request-Timestamp", "1700000000")
                               .header("X-Slack-Signature", "v0=signature")
                               .content("")
               )
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.errorCode").value("D01"))
               .andExpect(jsonPath("$.message").value("슬랙 인터랙티브 payload는 비어 있을 수 없습니다."));
    }

    @Test
    void timestamp_형식이_올바르지_않으면_인터랙티브_요청은_실패한다() throws Exception {
        // when & then
        mockMvc.perform(
                       post("/slack/interactive")
                               .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                               .header("X-Slack-Request-Timestamp", "invalid-timestamp")
                               .header("X-Slack-Signature", "v0=signature")
                               .content("payload=%7B%22type%22%3A%22block_actions%22%7D")
               )
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.errorCode").value("D01"))
               .andExpect(jsonPath("$.message").value("슬랙 요청 timestamp 형식이 올바르지 않습니다."));
    }

    @Test
    void signature_헤더가_없으면_인터랙티브_요청은_실패한다() throws Exception {
        // when & then
        mockMvc.perform(
                       post("/slack/interactive")
                               .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                               .header("X-Slack-Request-Timestamp", "1700000000")
                               .content("payload=%7B%22type%22%3A%22block_actions%22%7D")
               )
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.errorCode").value("D01"))
               .andExpect(jsonPath("$.message").value("슬랙 요청 signature가 필요합니다."));
    }

    private void 인터랙티브_요청_처리_성공_문서화(ResultActions resultActions) throws Exception {
        resultActions.andDo(
                restDocs.document(
                        requestHeaders(
                                headerWithName("X-Slack-Request-Timestamp").description("슬랙 요청 시각(Unix epoch seconds)"),
                                headerWithName("X-Slack-Signature").description("슬랙 시그니처(v0=...)")
                        ),
                        formParameters(
                                parameterWithName("payload").description("URL 인코딩된 슬랙 인터랙티브 payload JSON")
                        )
                )
        );
    }

    private void 시그니처_검증_실패_문서화(ResultActions resultActions) throws Exception {
        resultActions.andDo(
                restDocs.document(
                        responseFields(
                                fieldWithPath("errorCode").type(JsonFieldType.STRING).description("오류 코드"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("오류 메시지")
                        )
                )
        );
    }
}
