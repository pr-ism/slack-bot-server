package com.slack.bot.application.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.slack.bot.application.oauth.dto.response.SlackTokenResponse;
import com.slack.bot.application.oauth.exception.SlackOauthEmptyResponseException;
import com.slack.bot.application.oauth.exception.SlackOauthErrorResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@SpringBootTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackOauthServiceTest {

    @Autowired
    SlackOauthService slackOauthService;

    @Autowired
    RestClient.Builder slackRestClientBuilder;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        mockServer = MockRestServiceServer.bindTo(slackRestClientBuilder)
                                          .ignoreExpectOrder(true)
                                          .build();
    }

    @Test
    void 유효한_응답이_오면_토큰_교환에_성공한다() {
        // given
        String body = """
                {
                  "ok": true,
                  "access_token": "xoxb-test-token",
                  "team": {"id": "T123", "name": "Test Team"},
                  "authed_user": {"id": "U123"}
                }
                """;

        mockServer.expect(requestTo("https://slack.com/api/oauth.v2.access"))
                  .andExpect(method(POST))
                  .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        // when
        SlackTokenResponse response = slackOauthService.exchangeCodeForToken("auth-code");

        // then
        assertAll(
                () -> assertThat(response).isNotNull(),
                () -> assertThat(response.ok()).isTrue(),
                () -> assertThat(response.accessToken()).isEqualTo("xoxb-test-token"),
                () -> assertThat(response.team().id()).isEqualTo("T123"),
                () -> assertThat(response.authedUser().id()).isEqualTo("U123"),
                () -> mockServer.verify()
        );
    }

    @Test
    void 유효하지_않은_응답이_오면_토큰_교환은_실패한다() {
        // given
        String body = """
                {
                  "ok": false
                }
                """;

        mockServer.expect(requestTo("https://slack.com/api/oauth.v2.access"))
                  .andExpect(method(POST))
                  .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> slackOauthService.exchangeCodeForToken("auth-code"))
                        .isInstanceOf(SlackOauthErrorResponseException.class)
                        .hasMessageContaining("요청에 실패했습니다."),
                () -> mockServer.verify()
        );
    }

    @Test
    void 비어있는_응답이_오면_토큰_교환은_실패한다() {
        // given
        mockServer.expect(requestTo("https://slack.com/api/oauth.v2.access"))
                  .andExpect(method(POST))
                  .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> slackOauthService.exchangeCodeForToken("auth-code"))
                        .isInstanceOf(SlackOauthEmptyResponseException.class)
                        .hasMessageContaining("응답이 비어 있습니다."),
                () -> mockServer.verify()
        );
    }
}
