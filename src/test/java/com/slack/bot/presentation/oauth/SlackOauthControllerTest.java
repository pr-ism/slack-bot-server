package com.slack.bot.presentation.oauth;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.slack.bot.application.oauth.SlackOauthService;
import com.slack.bot.application.oauth.SlackWorkspaceService;
import com.slack.bot.application.oauth.dto.response.SlackTokenResponse;
import com.slack.bot.application.oauth.dto.response.SlackTokenResponse.AuthedUser;
import com.slack.bot.application.oauth.dto.response.SlackTokenResponse.Team;
import com.slack.bot.global.config.properties.SlackProperties;
import com.slack.bot.presentation.CommonControllerSliceTestSupport;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.ResultActions;
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

        String expectedUrl = UriComponentsBuilder.fromUriString("https://slack.com/oauth/v2/authorize")
                                                 .queryParam("client_id", "client-id")
                                                 .queryParam("scope", "chat:write,commands")
                                                 .queryParam("redirect_uri", "https://example.com/callback")
                                                 .build()
                                                 .toUriString();

        // when & then
        ResultActions resultActions = mockMvc.perform(get("/api/slack/install").accept(MediaType.APPLICATION_JSON))
                                             .andExpect(status().isOk())
                                             .andExpect(jsonPath("$.url").value(expectedUrl));

        설치_URL_조회_문서화(resultActions);
    }

    private void 설치_URL_조회_문서화(ResultActions resultActions) throws Exception {
        resultActions.andDo(
                restDocs.document(
                        responseFields(
                                fieldWithPath("url").type(JsonFieldType.STRING).description("슬랙 봇 설치 URL")
                        )
                )
        );
    }

    @Test
    void 콜백_코드_수신_및_워크스페이스_등록_문서화() throws Exception {
        // given
        String code = "auth-code";
        SlackTokenResponse tokenResponse = new SlackTokenResponse(
                true,
                "xoxb-token",
                new Team("T123", "테스트 워크스페이스"),
                new AuthedUser("U123")
        );

        given(slackOauthService.exchangeCodeForToken(code)).willReturn(tokenResponse);
        willDoNothing().given(slackWorkspaceService).registerWorkspace(tokenResponse);

        // when & then
        ResultActions resultActions = mockMvc.perform(get("/api/slack/callback").queryParam("code", code))
                                             .andExpect(status().isNoContent())
                                             .andExpect(content().string(""));

        콜백_코드_수신_문서화(resultActions);

        then(slackOauthService).should().exchangeCodeForToken(code);
        then(slackWorkspaceService).should().registerWorkspace(tokenResponse);
    }

    private void 콜백_코드_수신_문서화(ResultActions resultActions) throws Exception {
        resultActions.andDo(
                restDocs.document(
                        queryParameters(
                                parameterWithName("code").description("슬랙에서 전달하는 OAuth 인증 코드")
                        )
                )
        );
    }

    @Test
    void teamId가_없으면_콜백은_실패한다() throws Exception {
        // given
        String code = "auth-code";
        SlackTokenResponse tokenResponse = new SlackTokenResponse(
                true,
                "xoxb-token",
                new Team(null, "테스트 워크스페이스"),
                new AuthedUser("U123")
        );

        given(slackOauthService.exchangeCodeForToken(code)).willReturn(tokenResponse);
        willThrow(new IllegalArgumentException("슬랙 봇의 team ID는 비어 있을 수 없습니다."))
                .given(slackWorkspaceService)
                .registerWorkspace(tokenResponse);

        // when & then
        mockMvc.perform(get("/api/slack/callback").queryParam("code", code))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.errorCode").value("D01"))
               .andExpect(jsonPath("$.message").value("유효하지 않은 입력"));

        then(slackOauthService).should().exchangeCodeForToken(code);
        then(slackWorkspaceService).should().registerWorkspace(tokenResponse);
    }
}
