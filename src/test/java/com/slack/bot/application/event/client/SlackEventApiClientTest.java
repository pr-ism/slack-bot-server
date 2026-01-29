package com.slack.bot.application.event.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.slack.bot.application.event.client.exception.SlackChatRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackEventApiClientTest {

    private MockRestServiceServer mockServer;
    private SlackEventApiClient slackEventApiClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder()
                                                         .baseUrl("https://slack.com/api/");

        mockServer = MockRestServiceServer.bindTo(restClientBuilder)
                                          .ignoreExpectOrder(true)
                                          .build();

        RestClient slackClient = restClientBuilder.build();

        slackEventApiClient = new SlackEventApiClient(slackClient);
    }

    @Test
    void 채널에_특정_회원에게만_보이는_메시지를_전달한다() {
        // given
        String token = "xoxb-test-token";
        String channelId = "C12345";
        String targetUserId = "U67890";
        String text = "Ephemeral Message Test";
        String requestBody = """
            {
              "channel": "C12345",
              "user": "U67890",
              "text": "Ephemeral Message Test"
            }
            """;
        String responseBody = """
            {
              "ok": true
            }
            """;

        mockServer.expect(requestTo("https://slack.com/api/chat.postEphemeral"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andExpect(content().json(requestBody))
                  .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when
        slackEventApiClient.sendEphemeralMessage(token, channelId, targetUserId, text);

        // then
        mockServer.verify();
    }

    @Test
    void 일반_메시지를_전송한다() {
        // given
        String token = "xoxb-test-token";
        String channelId = "C12345";
        String text = "General Message Test";
        String requestBody = """
            {
              "channel": "C12345",
              "text": "General Message Test"
            }
            """;
        String responseBody = """
            {
              "ok": true
            }
            """;

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andExpect(content().json(requestBody))
                  .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when
        slackEventApiClient.sendMessage(token, channelId, text);

        // then
        mockServer.verify();
    }

    @Test
    void API_호출이_실패하면_예외를_던진다() {
        // given
        String token = "xoxb-test-token";
        String channelId = "C12345";
        String text = "Error Test";

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> slackEventApiClient.sendMessage(token, channelId, text))
                        .isInstanceOf(SlackChatRequestException.class)
                        .hasMessageContaining("HTTP 요청 실패"),
                () -> mockServer.verify()
        );
    }

    @Test
    void 응답이_성공이더라도_ok가_false이고_에러메시지가_있으면_예외를_던진다() {
        // given
        String token = "xoxb-test-token";
        String channelId = "C12345";
        String text = "Business Error Test";
        String responseBody = """
            {
              "ok": false,
              "error": "channel_not_found"
            }
            """;

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> slackEventApiClient.sendMessage(token, channelId, text))
                        .isInstanceOf(SlackChatRequestException.class)
                        .hasMessageContaining("channel_not_found"),
                () -> mockServer.verify()
        );
    }

    @Test
    void 응답이_성공이더라도_ok가_false이고_에러메시지가_없으면_기본_예외를_던진다() {
        // given
        String token = "xoxb-test-token";
        String channelId = "C12345";
        String text = "Unknown Error Test";
        String responseBody = """
            {
              "ok": false
            }
            """;

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> slackEventApiClient.sendMessage(token, channelId, text))
                        .isInstanceOf(SlackChatRequestException.class)
                        .hasMessageContaining("슬랙 봇으로 채팅을 입력하지 못했습니다."),
                () -> mockServer.verify()
        );
    }

    @Test
    void 응답_본문이_비어있으면_기본_예외를_던진다() {
        // given
        String token = "xoxb-test-token";
        String channelId = "C12345";
        String text = "Null Body Test";

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andRespond(withSuccess());

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> slackEventApiClient.sendMessage(token, channelId, text))
                        .isInstanceOf(SlackChatRequestException.class)
                        .hasMessageContaining("슬랙 봇으로 채팅을 입력하지 못했습니다."),
                () -> mockServer.verify()
        );
    }
}
