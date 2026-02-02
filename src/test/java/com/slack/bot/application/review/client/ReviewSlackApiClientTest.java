package com.slack.bot.application.review.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.review.client.exception.ReviewSlackApiException;
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
class ReviewSlackApiClientTest {

    private MockRestServiceServer mockServer;
    private ReviewSlackApiClient reviewSlackApiClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        RestClient.Builder restClientBuilder = RestClient.builder()
                                                         .baseUrl("https://slack.com/api/");

        mockServer = MockRestServiceServer.bindTo(restClientBuilder)
                                          .ignoreExpectOrder(true)
                                          .build();

        RestClient slackClient = restClientBuilder.build();
        reviewSlackApiClient = new ReviewSlackApiClient(slackClient, objectMapper);
    }

    @Test
    void 블록_메시지를_정상적으로_전송한다() throws Exception {
        // given
        String token = "xoxb-test-token";
        String channelId = "C12345";
        JsonNode blocks = objectMapper.readTree("[{\"type\":\"section\",\"text\":{\"type\":\"mrkdwn\",\"text\":\"hello\"}}]");
        JsonNode attachments = objectMapper.readTree("[{\"color\":\"#6366F1\"}]");
        String fallbackText = "fallback";
        String responseBody = """
                {"ok": true}
                """;

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertAll(
                () -> assertDoesNotThrow(() -> reviewSlackApiClient.sendBlockMessage(token, channelId, blocks, attachments, fallbackText)),
                () -> mockServer.verify()
        );
    }

    @Test
    void attachments와_fallbackText가_null이어도_전송에_성공한다() throws Exception {
        // given
        String token = "xoxb-test-token";
        String channelId = "C12345";
        JsonNode blocks = objectMapper.readTree("[{\"type\":\"section\"}]");
        String responseBody = """
                {"ok": true}
                """;

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertAll(
                () -> assertDoesNotThrow(() -> reviewSlackApiClient.sendBlockMessage(token, channelId, blocks, null, null)),
                () -> mockServer.verify()
        );
    }

    @Test
    void Slack_API가_ok_false를_반환하면_예외를_던진다() throws Exception {
        // given
        String token = "xoxb-test-token";
        String channelId = "C12345";
        JsonNode blocks = objectMapper.readTree("[{\"type\":\"section\"}]");
        String responseBody = """
                {"ok": false, "error": "invalid_blocks"}
                """;

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> reviewSlackApiClient.sendBlockMessage(token, channelId, blocks, null, null))
                        .isInstanceOf(ReviewSlackApiException.class)
                        .hasMessageContaining("invalid_blocks"),
                () -> mockServer.verify()
        );
    }

    @Test
    void HTTP_오류_응답이면_예외를_던진다() throws Exception {
        // given
        String token = "xoxb-test-token";
        String channelId = "C12345";
        JsonNode blocks = objectMapper.readTree("[{\"type\":\"section\"}]");

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                          .body("server error")
                          .contentType(MediaType.TEXT_PLAIN));

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> reviewSlackApiClient.sendBlockMessage(token, channelId, blocks, null, null))
                        .isInstanceOf(ReviewSlackApiException.class)
                        .hasMessageContaining("chat.postMessage"),
                () -> mockServer.verify()
        );
    }

    @Test
    void 응답_본문이_비어있으면_예외를_던진다() throws Exception {
        // given
        String token = "xoxb-test-token";
        String channelId = "C12345";
        JsonNode blocks = objectMapper.readTree("[{\"type\":\"section\"}]");

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andRespond(withSuccess());

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> reviewSlackApiClient.sendBlockMessage(token, channelId, blocks, null, null))
                        .isInstanceOf(ReviewSlackApiException.class)
                        .hasMessageContaining("응답이 비어 있습니다"),
                () -> mockServer.verify()
        );
    }

    @Test
    void 응답_본문이_유효한_JSON이_아니면_파싱_실패_예외를_던진다() throws Exception {
        // given
        String token = "xoxb-test-token";
        String channelId = "C12345";
        JsonNode blocks = objectMapper.readTree("[{\"type\":\"section\"}]");
        String invalidJson = "not a json";

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andRespond(withSuccess(invalidJson, MediaType.APPLICATION_JSON));

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> reviewSlackApiClient.sendBlockMessage(token, channelId, blocks, null, null))
                        .isInstanceOf(ReviewSlackApiException.class)
                        .hasMessageContaining("파싱 실패"),
                () -> mockServer.verify()
        );
    }

    @Test
    void HTTP_오류_응답_본문_읽기에_실패해도_예외를_던진다() throws Exception {
        // given
        String token = "xoxb-test-token";
        String channelId = "C12345";
        JsonNode blocks = objectMapper.readTree("[{\"type\":\"section\"}]");

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                          .body(new byte[0])
                          .contentType(MediaType.TEXT_PLAIN));

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> reviewSlackApiClient.sendBlockMessage(token, channelId, blocks, null, null))
                        .isInstanceOf(ReviewSlackApiException.class)
                        .hasMessageContaining("chat.postMessage"),
                () -> mockServer.verify()
        );
    }

    @Test
    void HTTP_오류_응답_본문이_긴_경우_잘려서_표시된다() throws Exception {
        // given
        String token = "xoxb-test-token";
        String channelId = "C12345";
        JsonNode blocks = objectMapper.readTree("[{\"type\":\"section\"}]");
        String longBody = "x".repeat(600);

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                          .body(longBody)
                          .contentType(MediaType.TEXT_PLAIN));

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> reviewSlackApiClient.sendBlockMessage(token, channelId, blocks, null, null))
                        .isInstanceOf(ReviewSlackApiException.class)
                        .hasMessageContaining("truncated"),
                () -> mockServer.verify()
        );
    }
}
