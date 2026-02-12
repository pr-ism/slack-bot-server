package com.slack.bot.application.interactivity.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.slack.api.model.view.Views;
import com.slack.bot.application.interactivity.client.exception.SlackBotMessageDispatchException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NotificationApiClientTest {

    private MockRestServiceServer mockServer;
    private NotificationApiClient notificationApiClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder()
                                                         .baseUrl("https://slack.com/api/");

        mockServer = MockRestServiceServer.bindTo(restClientBuilder)
                                          .ignoreExpectOrder(true)
                                          .build();

        RestClient slackClient = restClientBuilder.build();

        notificationApiClient = new NotificationApiClient(slackClient, new ObjectMapper());
    }

    @Test
    void 에페메랄_메시지를_전송한다() {
        // given
        String token = "xoxb-token";
        String channelId = "C123";
        String targetUserId = "U123";
        String text = "ephemeral message";

        String requestBody = """
                {
                  "channel": "C123",
                  "user": "U123",
                  "text": "ephemeral message"
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

        // when & then
        assertAll(
                () -> assertDoesNotThrow(() -> notificationApiClient.sendEphemeralMessage(token, channelId, targetUserId, text)),
                () -> mockServer.verify()
        );
    }

    @Test
    void 에페메랄_메시지_전송_실패_시_예외를_던진다() {
        // given
        String token = "xoxb-token";
        String channelId = "C123";
        String targetUserId = "U123";
        String text = "ephemeral message";

        String responseBody = """
                {
                  "ok": false,
                  "error": "channel_not_found"
                }
                """;

        mockServer.expect(requestTo("https://slack.com/api/chat.postEphemeral"))
                  .andExpect(method(POST))
                  .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> notificationApiClient.sendEphemeralMessage(token, channelId, targetUserId, text))
                .isInstanceOf(SlackBotMessageDispatchException.class)
                .hasMessageContaining("channel_not_found");
    }

    @Test
    void 에페메랄_블록_메시지를_전송한다() {
        // given
        String token = "xoxb-token";
        String channelId = "C123";
        String targetUserId = "U123";
        List<String> blocks = List.of("block1", "block2");
        String text = "fallback text";

        String responseBody = """
                {
                  "ok": true
                }
                """;

        mockServer.expect(requestTo("https://slack.com/api/chat.postEphemeral"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertAll(
                () -> assertDoesNotThrow(() -> notificationApiClient.sendEphemeralBlockMessage(token, channelId, targetUserId, blocks, text)),
                () -> mockServer.verify()
        );
    }

    @Test
    void 에페메랄_블록_메시지를_text_없이_전송한다() {
        // given
        String token = "xoxb-token";
        String channelId = "C123";
        String targetUserId = "U123";
        List<String> blocks = List.of("block1", "block2");

        String responseBody = """
                {
                  "ok": true
                }
                """;

        mockServer.expect(requestTo("https://slack.com/api/chat.postEphemeral"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertAll(
                () -> assertDoesNotThrow(() -> notificationApiClient.sendEphemeralBlockMessage(token, channelId, targetUserId, blocks, null)),
                () -> mockServer.verify()
        );
    }

    @Test
    void 메시지를_전송한다() {
        // given
        String token = "xoxb-token";
        String channelId = "C123";
        String text = "hello world";

        String requestBody = """
                {
                  "channel": "C123",
                  "text": "hello world"
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

        // when & then
        assertAll(
                () -> assertDoesNotThrow(() -> notificationApiClient.sendMessage(token, channelId, text)),
                () -> mockServer.verify()
        );
    }

    @Test
    void 메시지_전송_실패_시_예외를_던진다() {
        // given
        String token = "xoxb-token";
        String channelId = "C123";
        String text = "hello world";

        String responseBody = """
                {
                  "ok": false,
                  "error": "channel_not_found"
                }
                """;

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> notificationApiClient.sendMessage(token, channelId, text))
                .isInstanceOf(SlackBotMessageDispatchException.class)
                .hasMessageContaining("channel_not_found");
    }

    @Test
    void 메시지_전송_실패시_response_metadata_messages를_예외_메시지에_포함한다() {
        // given
        String token = "xoxb-token";
        String channelId = "C123";
        String text = "hello world";

        String responseBody = """
                {
                  "ok": false,
                  "error": "invalid_arguments",
                  "response_metadata": {
                    "messages": [
                      "first detail",
                      "second detail"
                    ]
                  }
                }
                """;

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> notificationApiClient.sendMessage(token, channelId, text))
                .isInstanceOf(SlackBotMessageDispatchException.class)
                .hasMessageContaining("invalid_arguments")
                .hasMessageContaining("messages=first detail, second detail");
    }

    @Test
    void 메시지_전송_실패시_response_metadata_messages가_빈_배열이면_details를_포함하지_않는다() {
        // given
        String token = "xoxb-token";
        String channelId = "C123";
        String text = "hello world";

        String responseBody = """
                {
                  "ok": false,
                  "error": "invalid_arguments",
                  "response_metadata": {
                    "messages": []
                  }
                }
                """;

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> notificationApiClient.sendMessage(token, channelId, text))
                .isInstanceOf(SlackBotMessageDispatchException.class)
                .hasMessageContaining("invalid_arguments")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("messages="));
    }

    @Test
    void 블록_메시지를_전송한다() {
        // given
        String token = "xoxb-token";
        String channelId = "C123";
        List<String> blocks = List.of("block1", "block2");
        String text = "fallback text";

        String responseBody = """
                {
                  "ok": true
                }
                """;

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertAll(
                () -> assertDoesNotThrow(() -> notificationApiClient.sendBlockMessage(token, channelId, blocks, text)),
                () -> mockServer.verify()
        );
    }

    @Test
    void 블록_메시지를_text_없이_전송한다() {
        // given
        String token = "xoxb-token";
        String channelId = "C123";
        List<String> blocks = List.of("block1", "block2");

        String responseBody = """
                {
                  "ok": true
                }
                """;

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertAll(
                () -> assertDoesNotThrow(() -> notificationApiClient.sendBlockMessage(token, channelId, blocks, null)),
                () -> mockServer.verify()
        );
    }

    @Test
    void DM_채널을_연다() {
        // given
        String token = "xoxb-token";
        String userId = "U123";

        String requestBody = """
                {
                  "users": "U123"
                }
                """;
        String responseBody = """
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
                  .andExpect(content().json(requestBody))
                  .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when
        String actualChannelId = notificationApiClient.openDirectMessageChannel(token, userId);

        // then
        assertAll(
                () -> assertThat(actualChannelId).isEqualTo("D234"),
                () -> mockServer.verify()
        );
    }

    @Test
    void DM_채널_열기_실패시_예외를_던진다() {
        // given
        String token = "xoxb-token";
        String userId = "U123";

        String responseBody = """
                {
                  "ok": false,
                  "error": "user_not_found"
                }
                """;

        mockServer.expect(requestTo("https://slack.com/api/conversations.open"))
                  .andExpect(method(POST))
                  .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> notificationApiClient.openDirectMessageChannel(token, userId))
                .isInstanceOf(SlackBotMessageDispatchException.class)
                .hasMessageContaining("user_not_found");
    }

    @Test
    void DM_채널_ID가_없으면_예외를_던진다() {
        // given
        String token = "xoxb-token";
        String userId = "U123";

        String responseBody = """
                {
                  "ok": true,
                  "channel": {}
                }
                """;

        mockServer.expect(requestTo("https://slack.com/api/conversations.open"))
                  .andExpect(method(POST))
                  .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> notificationApiClient.openDirectMessageChannel(token, userId))
                .isInstanceOf(SlackBotMessageDispatchException.class)
                .hasMessageContaining("채널 ID가 없습니다");
    }

    @Test
    void DM_채널_ID가_빈_문자열이면_예외를_던진다() {
        // given
        String token = "xoxb-token";
        String userId = "U123";

        String responseBody = """
                {
                  "ok": true,
                  "channel": {
                    "id": ""
                  }
                }
                """;

        mockServer.expect(requestTo("https://slack.com/api/conversations.open"))
                  .andExpect(method(POST))
                  .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> notificationApiClient.openDirectMessageChannel(token, userId))
                .isInstanceOf(SlackBotMessageDispatchException.class)
                .hasMessageContaining("채널 ID가 없습니다");
    }

    @Test
    void 모달을_연다() {
        // given
        String token = "xoxb-token";
        String triggerId = "TRIGGER_ID";
        java.util.Map<String, Object> view = java.util.Map.of("type", "modal");

        String requestBody = """
                {
                  "trigger_id": "TRIGGER_ID",
                  "view": {
                    "type": "modal"
                  }
                }
                """;
        String responseBody = """
                {
                  "ok": true
                }
                """;

        mockServer.expect(requestTo("https://slack.com/api/views.open"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andExpect(content().json(requestBody))
                  .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertAll(
                () -> assertDoesNotThrow(() -> notificationApiClient.openModal(token, triggerId, view)),
                () -> mockServer.verify()
        );
    }

    @Test
    void 모달_열기_실패_시_예외를_던진다() {
        // given
        String token = "xoxb-token";
        String triggerId = "TRIGGER_ID";
        java.util.Map<String, Object> view = java.util.Map.of("type", "modal");

        String responseBody = """
                {
                  "ok": false,
                  "error": "invalid_trigger_id"
                }
                """;

        mockServer.expect(requestTo("https://slack.com/api/views.open"))
                  .andExpect(method(POST))
                  .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> notificationApiClient.openModal(token, triggerId, view))
                .isInstanceOf(SlackBotMessageDispatchException.class)
                .hasMessageContaining("invalid_trigger_id");
    }

    @Test
    void 슬랙_View_객체로_모달을_열때_snake_case로_직렬화한다() {
        // given
        String token = "xoxb-token";
        String triggerId = "TRIGGER_ID";
        Object view = Views.view(v -> v
                .type("modal")
                .callbackId("review_time_submit")
                .privateMetadata("{\"team_id\":\"T1\"}")
                .title(Views.viewTitle(t -> t.type("plain_text").text("리뷰 시간 설정")))
                .submit(Views.viewSubmit(s -> s.type("plain_text").text("확인")))
                .close(Views.viewClose(c -> c.type("plain_text").text("취소")))
        );

        String requestBody = """
                {
                  "trigger_id": "TRIGGER_ID",
                  "view": {
                    "type": "modal",
                    "callback_id": "review_time_submit",
                    "private_metadata": "{\\"team_id\\":\\"T1\\"}"
                  }
                }
                """;
        String responseBody = """
                {
                  "ok": true
                }
                """;

        mockServer.expect(requestTo("https://slack.com/api/views.open"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andExpect(content().json(requestBody, JsonCompareMode.LENIENT))
                  .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertAll(
                () -> assertDoesNotThrow(() -> notificationApiClient.openModal(token, triggerId, view)),
                () -> mockServer.verify()
        );
    }

    @Test
    void JsonNode_View_객체로_모달을_열때_그대로_전달한다() throws Exception {
        // given
        String token = "xoxb-token";
        String triggerId = "TRIGGER_ID";
        ObjectMapper mapper = new ObjectMapper();
        Object view = mapper.readTree("""
                {
                  "type": "modal",
                  "callback_id": "review_time_submit"
                }
                """);

        String requestBody = """
                {
                  "trigger_id": "TRIGGER_ID",
                  "view": {
                    "type": "modal",
                    "callback_id": "review_time_submit"
                  }
                }
                """;
        String responseBody = """
                {
                  "ok": true
                }
                """;

        mockServer.expect(requestTo("https://slack.com/api/views.open"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andExpect(content().json(requestBody, JsonCompareMode.LENIENT))
                  .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        // when & then
        assertAll(
                () -> assertDoesNotThrow(() -> notificationApiClient.openModal(token, triggerId, view)),
                () -> mockServer.verify()
        );
    }

    @Test
    void 모달_view_직렬화_실패시_원인_예외를_포함해_던진다() {
        // given
        String token = "xoxb-token";
        String triggerId = "TRIGGER_ID";
        Object view = Views.view(v -> v
                .type("modal")
                .callbackId("review_time_submit")
        );
        ObjectMapper failingObjectMapper = new ObjectMapper() {
            @Override
            public com.fasterxml.jackson.databind.JsonNode readTree(String content) throws JsonProcessingException {
                throw new JsonProcessingException("readTree failed") {
                };
            }
        };
        NotificationApiClient failingClient = new NotificationApiClient(
                RestClient.builder().baseUrl("https://slack.com/api/").build(),
                failingObjectMapper
        );

        // when & then
        assertThatThrownBy(() -> failingClient.openModal(token, triggerId, view))
                .isInstanceOf(SlackBotMessageDispatchException.class)
                .hasMessageContaining("모달 view 직렬화 실패")
                .hasCauseInstanceOf(JsonProcessingException.class);
    }

    @Test
    void 응답이_비어_있으면_예외를_던진다() {
        // given
        String token = "xoxb-token";
        String channelId = "C123";
        String text = "hello";

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> notificationApiClient.sendMessage(token, channelId, text))
                .isInstanceOf(SlackBotMessageDispatchException.class)
                .hasMessageContaining("응답이 비어 있습니다");
    }

    @Test
    void HTTP_에러_응답시_예외를_던진다() {
        // given
        String token = "xoxb-token";
        String channelId = "C123";
        String text = "hello";

        String errorBody = """
                {
                  "error": "rate_limited"
                }
                """;

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                          .body(errorBody)
                          .contentType(MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> notificationApiClient.sendMessage(token, channelId, text))
                .isInstanceOf(SlackBotMessageDispatchException.class)
                .hasMessageContaining("Slack API 요청 실패")
                .hasMessageContaining("429");
    }

    @Test
    void HTTP_에러_응답에_body가_500자_이상이면_잘라서_반환한다() {
        // given
        String token = "xoxb-token";
        String channelId = "C123";
        String text = "hello";

        String longErrorBody = "a".repeat(600);

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                          .body(longErrorBody)
                          .contentType(MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> notificationApiClient.sendMessage(token, channelId, text))
                .isInstanceOf(SlackBotMessageDispatchException.class)
                .hasMessageContaining("(truncated)");
    }

    @Test
    void HTTP_에러_응답에_body가_500자_이하면_전체를_반환한다() {
        // given
        String token = "xoxb-token";
        String channelId = "C123";
        String text = "hello";

        String shortErrorBody = "short error message";

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                          .body(shortErrorBody)
                          .contentType(MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> notificationApiClient.sendMessage(token, channelId, text))
                .isInstanceOf(SlackBotMessageDispatchException.class)
                .hasMessageContaining("short error message")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("(truncated)"));
    }

    @Test
    void HTTP_에러_응답에_body가_없어도_예외를_던진다() {
        // given
        String token = "xoxb-token";
        String channelId = "C123";
        String text = "hello";

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                          .contentType(MediaType.APPLICATION_JSON));

        // when & then
        assertThatThrownBy(() -> notificationApiClient.sendMessage(token, channelId, text))
                .isInstanceOf(SlackBotMessageDispatchException.class)
                .hasMessageContaining("Slack API 요청 실패")
                .hasMessageContaining("500");
    }

    @Test
    void HTTP_에러_응답_body_읽기_실패시_빈문자열로_처리한다() {
        // given
        String token = "xoxb-token";
        String channelId = "C123";
        String text = "hello";

        mockServer.expect(requestTo("https://slack.com/api/chat.postMessage"))
                  .andExpect(method(POST))
                  .andExpect(header("Authorization", "Bearer " + token))
                  .andRespond(request -> withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                          .body(new InputStreamResource(new InputStream() {
                              @Override
                              public int read() throws IOException {
                                  throw new IOException("Stream read failed");
                              }
                          }))
                          .createResponse(request));

        // when & then
        assertThatThrownBy(() -> notificationApiClient.sendMessage(token, channelId, text))
                .isInstanceOf(SlackBotMessageDispatchException.class)
                .hasMessageContaining("Slack API 요청 실패")
                .hasMessageContaining("500");
    }
}
