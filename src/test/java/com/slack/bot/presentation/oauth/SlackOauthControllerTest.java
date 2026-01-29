package com.slack.bot.presentation.oauth;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.slack.bot.application.oauth.OauthService;
import com.slack.bot.application.oauth.OauthVerificationStateService;
import com.slack.bot.application.oauth.RegisterWorkspaceService;
import com.slack.bot.application.oauth.TokenParsingService;
import com.slack.bot.application.oauth.dto.response.SlackTokenResponse;
import com.slack.bot.application.oauth.dto.response.SlackTokenResponse.Team;
import com.slack.bot.application.oauth.exception.ExpiredSlackOauthStateException;
import com.slack.bot.global.config.properties.SlackProperties;
import com.slack.bot.presentation.CommonControllerSliceTestSupport;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackOauthControllerTest extends CommonControllerSliceTestSupport {

    @Autowired
    SlackProperties slackProperties;

    @Autowired
    OauthService oauthService;

    @Autowired
    RegisterWorkspaceService registerWorkspaceService;

    @Autowired
    OauthVerificationStateService oauthVerificationStateService;

    @Autowired
    TokenParsingService tokenParsingService;

    @Test
    void 설치_URL_조회_성공_테스트() throws Exception {
        // given
        String accessToken = "test-access-token";
        Long userId = 7L;
        String expectedState = "state-token";

        given(slackProperties.clientId()).willReturn("client-id");
        given(slackProperties.scopes()).willReturn("chat:write,commands");
        given(slackProperties.redirectUri()).willReturn("https://example.com/callback");
        given(tokenParsingService.extractUserId(accessToken)).willReturn(userId);
        given(oauthVerificationStateService.generateSlackOauthState(userId)).willReturn(expectedState);

        // when & then
        ResultActions resultActions = mockMvc.perform(
                                                     get("/slack/install").accept(MediaType.APPLICATION_JSON)
                                                                          .header("Authorization", "Bearer " + accessToken)
                                             )
                                             .andExpect(status().isOk())
                                             .andExpect(
                                                     jsonPath(
                                                             "$.url",
                                                             allOf(
                                                                     containsString("state="),
                                                                     containsString("client_id=client-id"),
                                                                     containsString("scope=chat:write,commands"),
                                                                     containsString("redirect_uri=https://example.com/callback")
                                                             )
                                                     )
                                             );

        설치_URL_조회_문서화(resultActions);
    }

    private void 설치_URL_조회_문서화(ResultActions resultActions) throws Exception {
        resultActions.andDo(
                restDocs.document(
                        requestHeaders(
                                headerWithName("Authorization").description("로그인한 사용자의 액세스 토큰")
                        ),
                        responseFields(
                                fieldWithPath("url").type(JsonFieldType.STRING).description("슬랙 봇 설치 URL (state 포함)")
                        )
                )
        );
    }

    @Test
    void 콜백_코드_수신_성공_테스트() throws Exception {
        // given
        String code = "auth-code";
        String state = "saved-state";
        SlackTokenResponse tokenResponse = new SlackTokenResponse(
                true,
                "xoxb-token",
                "B123",
                new Team("T123", "테스트 워크스페이스")
        );
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("SLACK_OAUTH_STATE", state);

        Long userId = 99L;

        given(oauthVerificationStateService.resolveUserIdByState(state)).willReturn(userId);
        given(oauthService.exchangeCodeForToken(code)).willReturn(tokenResponse);
        willDoNothing().given(registerWorkspaceService).registerWorkspace(tokenResponse, userId);

        // when & then
        ResultActions resultActions = mockMvc.perform(
                                                     get("/slack/callback")
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
    void Bearer_접두사가_없는_토큰으로도_설치_URL를_조회할_수_있다() throws Exception {
        // given
        String accessToken = "raw-access-token";
        Long userId = 7L;
        String expectedState = "state-token";

        given(slackProperties.clientId()).willReturn("client-id");
        given(slackProperties.scopes()).willReturn("chat:write,commands");
        given(slackProperties.redirectUri()).willReturn("https://example.com/callback");
        given(tokenParsingService.extractUserId(accessToken)).willReturn(userId);
        given(oauthVerificationStateService.generateSlackOauthState(userId)).willReturn(expectedState);

        // when & then
        mockMvc.perform(
                       get("/slack/install").accept(MediaType.APPLICATION_JSON)
                                            .header("Authorization", accessToken)
               )
               .andExpect(status().isOk())
               .andExpect(
                       jsonPath(
                               "$.url",
                               allOf(
                                       containsString("state="),
                                       containsString("client_id=client-id")
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
                "B123",
                new Team(null, "테스트 워크스페이스")
        );
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("SLACK_OAUTH_STATE", state);

        Long userId = 33L;
        given(oauthVerificationStateService.resolveUserIdByState(state)).willReturn(userId);
        given(oauthService.exchangeCodeForToken(code)).willReturn(tokenResponse);
        willThrow(new IllegalArgumentException("슬랙 봇의 team ID는 비어 있을 수 없습니다."))
                .given(registerWorkspaceService)
                .registerWorkspace(tokenResponse, userId);

        // when & then
        mockMvc.perform(
                       get("/slack/callback")
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

        given(oauthVerificationStateService.resolveUserIdByState("wrong-state"))
                .willThrow(new ExpiredSlackOauthStateException());

        // when & then
        mockMvc.perform(
                       get("/slack/callback")
                               .queryParam("code", "auth-code")
                               .queryParam("state", "wrong-state")
                               .session(session)
               )
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.errorCode").value("O02"))
               .andExpect(jsonPath("$.message").value("만료된 state"));
    }

    @Test
    void state가_없으면_콜백은_실패한다() throws Exception {
        // given
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("SLACK_OAUTH_STATE", "expected-state");

        // when & then
        mockMvc.perform(
                       get("/slack/callback")
                               .queryParam("code", "auth-code")
                               .session(session)
               )
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.errorCode").value("D02"))
               .andExpect(jsonPath("$.message").value("필수 파라미터 누락"));
    }
}
