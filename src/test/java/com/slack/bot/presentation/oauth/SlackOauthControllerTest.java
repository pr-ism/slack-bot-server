package com.slack.bot.presentation.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.slack.bot.application.oauth.SlackOauthService;
import com.slack.bot.application.oauth.SlackWorkspaceService;
import com.slack.bot.application.oauth.dto.response.SlackTokenResponse;
import com.slack.bot.application.oauth.dto.response.SlackTokenResponse.AuthedUser;
import com.slack.bot.application.oauth.dto.response.SlackTokenResponse.Team;
import com.slack.bot.global.config.properties.SlackProperties;
import com.slack.bot.presentation.CommonControllerSliceTestSupport;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackOauthControllerTest extends CommonControllerSliceTestSupport {

    @Autowired
    SlackProperties slackProperties;

    @Autowired
    SlackOauthService slackOauthService;

    @Autowired
    SlackWorkspaceService slackWorkspaceService;

    @Test
    void 설치_URL_조회_문서화() throws Exception {
        // given
        given(slackProperties.clientId()).willReturn("client-id");
        given(slackProperties.scopes()).willReturn("chat:write,commands");
        given(slackProperties.redirectUri()).willReturn("https://example.com/callback");

        // when & then
        ResultActions resultActions = mockMvc.perform(get("/api/slack/install").accept(MediaType.APPLICATION_JSON))
                                             .andExpect(status().isOk())
                                             .andExpect(jsonPath("$.url", containsString("state=")));

        HttpSession session = resultActions.andReturn().getRequest().getSession(false);
        String body = resultActions.andReturn().getResponse().getContentAsString();
        String slackUrl = objectMapper.readTree(body).get("url").asText();
        MultiValueMap<String, String> params = UriComponentsBuilder.fromUriString(slackUrl)
                                                                   .build()
                                                                   .getQueryParams();

        assertAll(
                () -> assertThat(session).isNotNull(),
                () -> assertThat(params.getFirst("client_id")).isEqualTo("client-id"),
                () -> assertThat(params.getFirst("scope")).isEqualTo("chat:write,commands"),
                () -> assertThat(params.getFirst("redirect_uri")).isEqualTo("https://example.com/callback"),
                () -> assertThat(params.getFirst("state")).isEqualTo(session.getAttribute("SLACK_OAUTH_STATE"))
        );

        설치_URL_조회_문서화(resultActions);
    }

    private void 설치_URL_조회_문서화(ResultActions resultActions) throws Exception {
        resultActions.andDo(
                restDocs.document(
                        responseFields(
                                fieldWithPath("url").type(JsonFieldType.STRING).description("슬랙 봇 설치 URL (state 포함)")
                        )
                )
        );
    }

    @Test
    void 콜백_코드_수신_및_워크스페이스_등록_문서화() throws Exception {
        // given
        String code = "auth-code";
        String state = "saved-state";
        SlackTokenResponse tokenResponse = new SlackTokenResponse(
                true,
                "xoxb-token",
                new Team("T123", "테스트 워크스페이스"),
                new AuthedUser("U123")
        );
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("SLACK_OAUTH_STATE", state);

        given(slackOauthService.exchangeCodeForToken(code)).willReturn(tokenResponse);
        willDoNothing().given(slackWorkspaceService).registerWorkspace(tokenResponse);

        // when & then
        ResultActions resultActions = mockMvc.perform(
                        get("/api/slack/callback")
                                .queryParam("code", code)
                                .queryParam("state", state)
                                .session(session)
                        )
                        .andExpect(status().isNoContent());

        콜백_코드_수신_문서화(resultActions);
    }

    private void 콜백_코드_수신_문서화(ResultActions resultActions) throws Exception {
        resultActions.andDo(
                restDocs.document(
                        queryParameters(
                                parameterWithName("code").description("슬랙에서 전달하는 OAuth 인증 코드"),
                                parameterWithName("state").description("CSRF 방지를 위한 OAuth state 값")
                        )
                )
        );
    }

    @Test
    void teamId가_없으면_콜백은_실패한다() throws Exception {
        // given
        String code = "auth-code";
        String state = "saved-state";
        SlackTokenResponse tokenResponse = new SlackTokenResponse(
                true,
                "xoxb-token",
                new Team(null, "테스트 워크스페이스"),
                new AuthedUser("U123")
        );
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("SLACK_OAUTH_STATE", state);

        given(slackOauthService.exchangeCodeForToken(code)).willReturn(tokenResponse);
        willThrow(new IllegalArgumentException("슬랙 봇의 team ID는 비어 있을 수 없습니다."))
                .given(slackWorkspaceService)
                .registerWorkspace(tokenResponse);

        // when & then
        mockMvc.perform(
                        get("/api/slack/callback")
                                .queryParam("code", code)
                                .queryParam("state", state)
                                .session(session)
                )
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.errorCode").value("D01"))
               .andExpect(jsonPath("$.message").value("유효하지 않은 입력"));
    }

    @Test
    void state가_일치하지_않으면_콜백은_실패한다() throws Exception {
        // given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("SLACK_OAUTH_STATE", "expected-state");

        // when & then
        mockMvc.perform(
                        get("/api/slack/callback")
                                .queryParam("code", "auth-code")
                                .queryParam("state", "wrong-state")
                                .session(session)
                )
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.errorCode").value("A02"))
               .andExpect(jsonPath("$.message").value("슬랙 OAuth 실패"));
    }

    @Test
    void state가_없으면_콜백은_실패한다() throws Exception {
        // given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("SLACK_OAUTH_STATE", "expected-state");

        // when & then
        mockMvc.perform(
                        get("/api/slack/callback")
                                .queryParam("code", "auth-code")
                                .session(session)
                )
               .andExpect(status().isUnauthorized())
               .andExpect(jsonPath("$.errorCode").value("A02"))
               .andExpect(jsonPath("$.message").value("슬랙 OAuth 실패"));
    }
}
