package com.slack.bot.application.interactivity.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.slack.bot.application.interactivity.client.exception.SlackDmException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackDirectMessageClientTest {

    private MockRestServiceServer mockServer;
    private SlackDirectMessageClient slackDirectMessageClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder()
                                                         .baseUrl("https://slack.com/api/");

        mockServer = MockRestServiceServer.bindTo(restClientBuilder)
                                          .ignoreExpectOrder(true)
                                          .build();

        RestClient slackClient = restClientBuilder.build();
        slackDirectMessageClient = new SlackDirectMessageClient(slackClient);
    }

    @Test
    void DM을_성공적으로_보낸다() {
        // given
        String token = "xoxb-token";
        String userId = "U123";
        String text = "hello world";

        String openRequestBody = """
                {
                  "users": "U123"
                }
                """;
        String openResponseBody = """
                {
                  "ok": true,
                  "channel": {
                    "id": "D234"
                  }
                }
                """;
        mockServer.expect(requestTo("https://slack.com/api/conversations.open"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andExpect(content().json(openRequestBody))
                  .andRespond(withSuccess(openResponseBody, MediaType.APPLICATION_JSON));

        String messageRequestBody = """
                {
                  "channel": "D234",
                  "text": "hello world"
                }
                """;
        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andExpect(content().json(messageRequestBody))
                  .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        // when
        slackDirectMessageClient.send(token, userId, text);

        // then
        mockServer.verify();
    }

    @Test
    void DM_채널_오픈_응답이_ok_false이면_예외를_던진다() {
        // given
        String token = "xoxb-token";
        String userId = "U123";

        String openResponseBody = """
                {
                  "ok": false,
                  "error": "channel_not_found"
                }
                """;
        mockServer.expect(requestTo("https://slack.com/api/conversations.open"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andExpect(content().json("{\"users\":\"U123\"}"))
                  .andRespond(withSuccess(openResponseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> slackDirectMessageClient.send(token, userId, "message"))
                        .isInstanceOf(SlackDmException.class)
                        .hasMessageContaining("channel_not_found"),
                () -> mockServer.verify()
        );
    }

    @Test
    void 메시지_전송_HTTP_오류가_나면_예외를_던진다() {
        // given
        String token = "xoxb-token";
        String userId = "U123";
        String longBody = "a".repeat(600);

        String openResponseBody = """
                {
                  "ok": true,
                  "channel": {
                    "id": "D234"
                  }
                }
                """;
        mockServer.expect(requestTo("https://slack.com/api/conversations.open"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andExpect(content().json("{\"users\":\"U123\"}"))
                  .andRespond(withSuccess(openResponseBody, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                          .contentType(MediaType.TEXT_PLAIN)
                          .body(longBody));

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> slackDirectMessageClient.send(token, userId, "message"))
                        .isInstanceOf(SlackDmException.class)
                        .hasMessageContaining("status=500")
                        .hasMessageContaining("...(truncated)"),
                () -> mockServer.verify()
        );
    }

    @Test
    void 오류_응답_본문이_짧으면_잘리지_않는다() {
        // given
        String token = "xoxb-token";
        String userId = "U123";
        String shortBody = "short-body";

        String openResponseBody = """
                {
                  "ok": true,
                  "channel": {
                    "id": "D234"
                  }
                }
                """;
        mockServer.expect(requestTo("https://slack.com/api/conversations.open"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andExpect(content().json("{\"users\":\"U123\"}"))
                  .andRespond(withSuccess(openResponseBody, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                          .contentType(MediaType.TEXT_PLAIN)
                          .body(shortBody));

        // when & then
        assertThatThrownBy(() -> slackDirectMessageClient.send(token, userId, "message"))
                .isInstanceOf(SlackDmException.class)
                .hasMessageContaining("short-body")
                .hasMessageNotContaining("...(truncated)");
    }

    @Test
    void 필수_값이_비어_있으면_API_호출_없이_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> slackDirectMessageClient.send("", "", ""))
                .isInstanceOf(SlackDmException.class);

        mockServer.verify();
    }

    @Test
    void 오류_본문이_긴_경우_트렁케이션된다() {
        String token = "xoxb-token";
        String userId = "U123";
        String longBody = "a".repeat(600);

        String openResponseBody = """
                {
                  "ok": true,
                  "channel": {
                    "id": "D234"
                  }
                }
                """;
        mockServer.expect(requestTo("https://slack.com/api/conversations.open"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andExpect(content().json("{\"users\":\"U123\"}"))
                  .andRespond(withSuccess(openResponseBody, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                          .contentType(MediaType.TEXT_PLAIN)
                          .body(longBody));

        assertThatThrownBy(() -> slackDirectMessageClient.send(token, userId, "message"))
                .isInstanceOf(SlackDmException.class)
                .hasMessageContaining("...(truncated)");
    }

    @Test
    void readResponseBody는_IO예외가_나면_null을_반환한다() {
        String token = "xoxb-token";
        String userId = "U123";
        String openResponseBody = """
                {
                  "ok": true,
                  "channel": {
                    "id": "D234"
                  }
                }
                """;
        mockServer.expect(requestTo("https://slack.com/api/conversations.open"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andExpect(content().json("{\"users\":\"U123\"}"))
                  .andRespond(withSuccess(openResponseBody, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andRespond(request -> withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                          .body(new InputStreamResource(new InputStream() {
                              @Override
                              public int read() throws IOException {
                                  throw new IOException("boom");
                              }
                          }))
                          .createResponse(request));

        assertThatThrownBy(() -> slackDirectMessageClient.send(token, userId, "message"))
                .isInstanceOf(SlackDmException.class)
                .hasMessageNotContaining("body=");
    }
}
