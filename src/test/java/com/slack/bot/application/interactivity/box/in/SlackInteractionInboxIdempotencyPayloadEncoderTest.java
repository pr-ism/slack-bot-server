package com.slack.bot.application.interactivity.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.support.jackson.FailingObjectMapper;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackInteractionInboxIdempotencyPayloadEncoderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SlackInteractionInboxIdempotencyPayloadEncoder encoder =
            new SlackInteractionInboxIdempotencyPayloadEncoder(objectMapper);

    @Test
    void block_action_페이로드에서_멱등성_소스가_정상_생성된다() throws Exception {
        // given
        ObjectNode payload = payloadForBlockAction("T1", "C1", "U1", "cancel_review_reservation", "100", "1700000.1111");

        // when
        String source = encoder.encodeBlockAction(payload.toString());

        // then
        JsonNode sourceNode = objectMapper.readTree(source);

        assertAll(
                () -> assertThat(sourceNode.path("teamId").asText()).isEqualTo("T1"),
                () -> assertThat(sourceNode.path("channelId").asText()).isEqualTo("C1"),
                () -> assertThat(sourceNode.path("userId").asText()).isEqualTo("U1"),
                () -> assertThat(sourceNode.path("actionId").asText()).isEqualTo("cancel_review_reservation"),
                () -> assertThat(sourceNode.path("actionValue").asText()).isEqualTo("100"),
                () -> assertThat(sourceNode.path("actionTimestamp").asText()).isEqualTo("1700000.1111"),
                () -> assertThat(sourceNode.path("viewId").asText()).isEmpty()
        );
    }

    @Test
    void block_action_팀ID가_최상위_team_id에서도_읽힌다() throws Exception {
        // given
        ObjectNode payload = payloadForBlockAction("T2", "C9", "U9", "approve", "42", "1700000.2222");
        payload.remove("team");
        payload.put("team_id", "T2_ALT");

        // when
        String source = encoder.encodeBlockAction(payload.toString());

        // then
        JsonNode sourceNode = objectMapper.readTree(source);
        assertAll(
                () -> assertThat(sourceNode.path("teamId").asText()).isEqualTo("T2_ALT")
        );
    }

    @Test
    void block_action_team과_team_id가_없으면_user_team_id를_사용한다() throws Exception {
        // given
        ObjectNode payload = payloadForBlockAction("IGNORED", "C1", "U1", "action", "val", "123");
        payload.remove("team");
        ((ObjectNode) payload.path("user")).put("team_id", "T_FROM_USER");

        // when
        String source = encoder.encodeBlockAction(payload.toString());

        // then
        JsonNode sourceNode = objectMapper.readTree(source);
        assertThat(sourceNode.path("teamId").asText()).isEqualTo("T_FROM_USER");
    }

    @Test
    void block_action_최상위_action_ts가_없으면_action_내부_action_ts를_사용한다() throws Exception {
        // given
        ObjectNode payload = payloadForBlockAction("T1", "C1", "U1", "action", "val", "");
        payload.remove("action_ts");
        ((ObjectNode) ((ArrayNode) payload.path("actions")).get(0)).put("action_ts", "action_level_ts");

        // when
        String source = encoder.encodeBlockAction(payload.toString());

        // then
        JsonNode sourceNode = objectMapper.readTree(source);
        assertThat(sourceNode.path("actionTimestamp").asText()).isEqualTo("action_level_ts");
    }

    @Test
    void block_action_actions가_비어있으면_action_필드를_fallback으로_사용한다() throws Exception {
        // given
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("team", objectMapper.createObjectNode().put("id", "T1"));
        payload.set("channel", objectMapper.createObjectNode().put("id", "C1"));
        payload.set("user", objectMapper.createObjectNode().put("id", "U1"));
        payload.set("actions", objectMapper.createArrayNode());
        payload.set("action", objectMapper.createObjectNode()
                .put("action_id", "fallback-action")
                .put("value", "fallback-value")
                .put("action_ts", "fallback-ts"));

        // when
        String source = encoder.encodeBlockAction(payload.toString());

        // then
        JsonNode sourceNode = objectMapper.readTree(source);
        assertAll(
                () -> assertThat(sourceNode.path("actionId").asText()).isEqualTo("fallback-action"),
                () -> assertThat(sourceNode.path("actionValue").asText()).isEqualTo("fallback-value"),
                () -> assertThat(sourceNode.path("actionTimestamp").asText()).isEqualTo("fallback-ts")
        );
    }

    @Test
    void block_action_actions가_비어있고_action도_없으면_빈값으로_인코딩된다() throws Exception {
        // given
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("team", objectMapper.createObjectNode().put("id", "T1"));
        payload.set("channel", objectMapper.createObjectNode().put("id", "C1"));
        payload.set("user", objectMapper.createObjectNode().put("id", "U1"));
        payload.set("actions", objectMapper.createArrayNode());

        // when
        String source = encoder.encodeBlockAction(payload.toString());

        // then
        JsonNode sourceNode = objectMapper.readTree(source);
        assertAll(
                () -> assertThat(sourceNode.path("teamId").asText()).isEqualTo("T1"),
                () -> assertThat(sourceNode.path("channelId").asText()).isEqualTo("C1"),
                () -> assertThat(sourceNode.path("userId").asText()).isEqualTo("U1"),
                () -> assertThat(sourceNode.path("actionId").asText()).isEmpty(),
                () -> assertThat(sourceNode.path("actionValue").asText()).isEmpty(),
                () -> assertThat(sourceNode.path("actionTimestamp").asText()).isEmpty(),
                () -> assertThat(sourceNode.path("viewId").asText()).isEmpty()
        );
    }

    @Test
    void view_submission_페이로드에서_멱등성_소스가_정상_생성된다() throws Exception {
        // given
        ObjectNode payload = payloadForViewSubmission("T1", "U1", "V1", "review_time_submit", "{\"project_id\":123}");

        // when
        String source = encoder.encodeViewSubmission(payload.toString());

        // then
        JsonNode sourceNode = objectMapper.readTree(source);
        assertAll(
                () -> assertThat(sourceNode.path("teamId").asText()).isEqualTo("T1"),
                () -> assertThat(sourceNode.path("userId").asText()).isEqualTo("U1"),
                () -> assertThat(sourceNode.path("viewId").asText()).isEqualTo("V1"),
                () -> assertThat(sourceNode.path("callbackId").asText()).isEqualTo("review_time_submit"),
                () -> assertThat(sourceNode.path("privateMetadata").asText()).isEqualTo("{\"project_id\":123}")
        );
    }

    @Test
    void null_입력_시_원본을_반환한다() {
        assertAll(
                () -> assertThat(encoder.encodeBlockAction(null)).isNull(),
                () -> assertThat(encoder.encodeViewSubmission(null)).isNull()
        );
    }

    @Test
    void blank_입력_시_원본을_반환한다() {
        assertAll(
                () -> assertThat(encoder.encodeBlockAction("  ")).isEqualTo("  "),
                () -> assertThat(encoder.encodeViewSubmission("  ")).isEqualTo("  ")
        );
    }

    @Test
    void 파싱_실패_시_원본_payload가_fallback_된다() {
        // given
        String invalidPayload = "{invalid-json";

        // when
        String actualBlockAction = encoder.encodeBlockAction(invalidPayload);
        String actualViewSubmission = encoder.encodeViewSubmission(invalidPayload);

        // then
        assertAll(
                () -> assertThat(actualBlockAction).isEqualTo(invalidPayload),
                () -> assertThat(actualViewSubmission).isEqualTo(invalidPayload)
        );
    }

    @Test
    void block_action_encode_중_직렬화_실패_시_원본_payload가_fallback_된다() {
        // given
        FailingObjectMapper failingObjectMapper = new FailingObjectMapper();
        SlackInteractionInboxIdempotencyPayloadEncoder failureEncoder =
                new SlackInteractionInboxIdempotencyPayloadEncoder(failingObjectMapper);
        String payloadJson = payloadForBlockAction("T1", "C1", "U1", "cancel_review_reservation", "100", "1700000.1111")
                .toString();

        // when
        String actual = failureEncoder.encodeBlockAction(payloadJson);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(payloadJson)
        );
    }

    @Test
    void view_submission_encode_중_직렬화_실패_시_원본_payload가_fallback_된다() {
        // given
        FailingObjectMapper failingObjectMapper = new FailingObjectMapper();
        SlackInteractionInboxIdempotencyPayloadEncoder failureEncoder =
                new SlackInteractionInboxIdempotencyPayloadEncoder(failingObjectMapper);
        String payloadJson = payloadForViewSubmission("T1", "U1", "V1", "review_time_submit", "{\"project_id\":123}")
                .toString();

        // when
        String actual = failureEncoder.encodeViewSubmission(payloadJson);

        // then
        assertThat(actual).isEqualTo(payloadJson);
    }

    private ObjectNode payloadForBlockAction(
            String teamId,
            String channelId,
            String userId,
            String actionId,
            String actionValue,
            String actionTimestamp
    ) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("team", objectMapper.createObjectNode().put("id", teamId));
        payload.set("channel", objectMapper.createObjectNode().put("id", channelId));
        payload.set("user", objectMapper.createObjectNode().put("id", userId));
        payload.put("action_ts", actionTimestamp);

        ArrayNode actions = objectMapper.createArrayNode();
        actions.add(objectMapper.createObjectNode()
                              .put("action_id", actionId)
                              .put("value", actionValue));
        payload.set("actions", actions);

        return payload;
    }

    private ObjectNode payloadForViewSubmission(
            String teamId,
            String userId,
            String viewId,
            String callbackId,
            String privateMetadata
    ) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("team", objectMapper.createObjectNode().put("id", teamId));
        payload.set("user", objectMapper.createObjectNode().put("id", userId));

        ObjectNode view = objectMapper.createObjectNode();
        view.put("id", viewId);
        view.put("callback_id", callbackId);
        view.put("private_metadata", privateMetadata);
        payload.set("view", view);

        return payload;
    }
}
