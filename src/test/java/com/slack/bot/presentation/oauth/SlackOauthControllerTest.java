package com.slack.bot.presentation.oauth;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void 설치_URL을_조회한다() throws Exception {
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
        mockMvc.perform(get("/api/slack/install").accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.url").value(expectedUrl));
    }

    @Test
    void 콜백_코드를_받으면_워크스페이스를_등록한다() throws Exception {
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
        mockMvc.perform(get("/api/slack/callback").queryParam("code", code))
               .andExpect(status().isNoContent());
    }
}
