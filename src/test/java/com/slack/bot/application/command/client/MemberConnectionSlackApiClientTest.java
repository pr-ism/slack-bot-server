package com.slack.bot.application.command.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MemberConnectionSlackApiClientTest {

    private MockRestServiceServer mockServer;
    private MemberConnectionSlackApiClient memberConnectionSlackApiClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder()
                                                         .baseUrl("https://slack.com/api/");

        mockServer = MockRestServiceServer.bindTo(restClientBuilder)
                                          .ignoreExpectOrder(true)
                                          .build();

        RestClient slackClient = restClientBuilder.build();

        memberConnectionSlackApiClient = new MemberConnectionSlackApiClient(slackClient);
    }

    @Test
    void 표시_이름이_있으면_표시_이름을_반환한다() {
        // given
        String body = """
                {
                  "ok": true,
                  "user": {
                    "profile": {
                      "display_name": "hong"
                    },
                    "name": "fallback"
                  }
                }
                """;
        mockServer.expect(requestTo("https://slack.com/api/users.info?user=U1"))
                  .andExpect(method(GET))
                  .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        // when
        String displayName = memberConnectionSlackApiClient.resolveUserName("xoxb-token", "U1");

        // then
        assertAll(
                () -> assertThat(displayName).isEqualTo("hong"),
                () -> mockServer.verify()
        );
    }

    @Test
    void 표시_이름이_없으면_실명을_반환한다() {
        // given
        String body = """
                {
                  "ok": true,
                  "user": {
                    "profile": {
                      "real_name": "홍길동"
                    },
                    "name": "fallback"
                  }
                }
                """;
        mockServer.expect(requestTo("https://slack.com/api/users.info?user=U2"))
                  .andExpect(method(GET))
                  .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        // when
        String displayName = memberConnectionSlackApiClient.resolveUserName("xoxb-token", "U2");

        // then
        assertAll(
                () -> assertThat(displayName).isEqualTo("홍길동"),
                () -> mockServer.verify()
        );
    }

    @Test
    void 응답이_정상이_아니면_슬랙_ID를_반환한다() {
        // given
        String body = """
                {
                  "ok": false
                }
                """;
        mockServer.expect(requestTo("https://slack.com/api/users.info?user=U3"))
                  .andExpect(method(GET))
                  .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        // when
        String displayName = memberConnectionSlackApiClient.resolveUserName("xoxb-token", "U3");

        // then
        assertAll(
                () -> assertThat(displayName).isEqualTo("U3"),
                () -> mockServer.verify()
        );
    }
}
