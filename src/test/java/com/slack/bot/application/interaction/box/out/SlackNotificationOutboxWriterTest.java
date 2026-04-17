package com.slack.bot.application.interaction.box.out;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interaction.box.SlackInteractionIdempotencyKeyGenerator;
import com.slack.bot.application.interaction.box.SlackInteractionIdempotencyScope;
import com.slack.bot.application.interaction.box.out.exception.SlackBlocksSerializationException;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStatus;
import com.slack.bot.infrastructure.interaction.box.out.repository.SlackNotificationOutboxRepository;
import com.slack.bot.infrastructure.interaction.box.persistence.out.SlackNotificationOutboxMybatisMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackNotificationOutboxWriterTest {

    @Autowired
    SlackNotificationOutboxWriter slackNotificationOutboxWriter;

    @Autowired
    SlackNotificationOutboxRepository actualSlackNotificationOutboxRepository;

    @Autowired
    SlackNotificationOutboxMybatisMapper slackNotificationOutboxMybatisMapper;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    OutboxIdempotencyPayloadEncoder outboxIdempotencyPayloadEncoder;

    @Autowired
    SlackInteractionIdempotencyKeyGenerator slackInteractionIdempotencyKeyGenerator;

    @Test
    void 에페메랄_텍스트_메시지를_enqueue한다() {
        // given
        String sourceKey = "EVENT-1";
        String teamId = "T1";
        String channelId = "C1";
        String userId = "U1";
        String text = "hello";

        // when
        targetWriter().enqueueEphemeralText(sourceKey, teamId, channelId, userId, text);

        // then
        SlackNotificationOutbox actual = awaitSingleOutbox(
                sourceKey,
                SlackNotificationOutboxMessageType.EPHEMERAL_TEXT,
                teamId,
                channelId,
                userId
        );

        assertAll(
                () -> assertThat(actual.getMessageType()).isEqualTo(SlackNotificationOutboxMessageType.EPHEMERAL_TEXT),
                () -> assertThat(actual.getTeamId()).isEqualTo(teamId),
                () -> assertThat(actual.getChannelId()).isEqualTo(channelId),
                () -> assertThat(actual.getUserId().value()).isEqualTo(userId),
                () -> assertThat(actual.getText().value()).isEqualTo(text)
        );
    }

    @Test
    void 에페메랄_블록_메시지를_enqueue한다() {
        // given
        String sourceKey = "EVENT-2";
        String teamId = "T1";
        String channelId = "C1";
        String userId = "U1";
        JsonNode blocks = objectMapper.createArrayNode();
        String fallbackText = "fallback";

        // when
        targetWriter().enqueueEphemeralBlocks(sourceKey, teamId, channelId, userId, blocks, fallbackText);

        // then
        SlackNotificationOutbox actual = awaitSingleOutbox(
                sourceKey,
                SlackNotificationOutboxMessageType.EPHEMERAL_BLOCKS,
                teamId,
                channelId,
                userId
        );

        assertAll(
                () -> assertThat(actual.getMessageType()).isEqualTo(SlackNotificationOutboxMessageType.EPHEMERAL_BLOCKS),
                () -> assertThat(actual.getTeamId()).isEqualTo(teamId),
                () -> assertThat(actual.getChannelId()).isEqualTo(channelId),
                () -> assertThat(actual.getUserId().value()).isEqualTo(userId),
                () -> assertThat(actual.getBlocksJson().value()).isEqualTo("[]"),
                () -> assertThat(actual.getFallbackText().value()).isEqualTo(fallbackText)
        );
    }

    @Test
    void 채널_텍스트_메시지를_enqueue한다() {
        // given
        String sourceKey = "EVENT-3";
        String teamId = "T1";
        String channelId = "C1";
        String text = "hello channel";

        // when
        targetWriter().enqueueChannelText(sourceKey, teamId, channelId, text);

        // then
        SlackNotificationOutbox actual = awaitSingleOutbox(
                sourceKey,
                SlackNotificationOutboxMessageType.CHANNEL_TEXT,
                teamId,
                channelId,
                ""
        );

        assertAll(
                () -> assertThat(actual.getMessageType()).isEqualTo(SlackNotificationOutboxMessageType.CHANNEL_TEXT),
                () -> assertThat(actual.getTeamId()).isEqualTo(teamId),
                () -> assertThat(actual.getChannelId()).isEqualTo(channelId),
                () -> assertThat(actual.getText().value()).isEqualTo(text),
                () -> assertThat(actual.getUserId().isPresent()).isFalse()
        );
    }

    @Test
    void 채널_블록_메시지를_enqueue한다() {
        // given
        String sourceKey = "EVENT-4";
        String teamId = "T1";
        String channelId = "C1";
        JsonNode blocks = objectMapper.createArrayNode();
        String fallbackText = "fallback";

        // when
        targetWriter().enqueueChannelBlocks(sourceKey, teamId, channelId, blocks, fallbackText);

        // then
        SlackNotificationOutbox actual = awaitSingleOutbox(
                sourceKey,
                SlackNotificationOutboxMessageType.CHANNEL_BLOCKS,
                teamId,
                channelId,
                ""
        );

        assertAll(
                () -> assertThat(actual.getMessageType()).isEqualTo(SlackNotificationOutboxMessageType.CHANNEL_BLOCKS),
                () -> assertThat(actual.getTeamId()).isEqualTo(teamId),
                () -> assertThat(actual.getChannelId()).isEqualTo(channelId),
                () -> assertThat(actual.getBlocksJson().value()).isEqualTo("[]"),
                () -> assertThat(actual.getFallbackText().value()).isEqualTo(fallbackText),
                () -> assertThat(actual.getUserId().isPresent()).isFalse()
        );
    }

    @Test
    void 블록_문자열_JSON을_전달하면_이중_직렬화하지않고_그대로_enqueue한다() {
        // given
        String sourceKey = "EVENT-STRING-BLOCKS";
        String teamId = "T1";
        String channelId = "C1";
        String blocks = "[]";
        String fallbackText = "fallback";

        // when
        targetWriter().enqueueChannelBlocks(sourceKey, teamId, channelId, blocks, fallbackText);

        // then
        SlackNotificationOutbox actual = awaitSingleOutbox(
                sourceKey,
                SlackNotificationOutboxMessageType.CHANNEL_BLOCKS,
                teamId,
                channelId,
                ""
        );

        assertAll(
                () -> assertThat(actual.getMessageType()).isEqualTo(SlackNotificationOutboxMessageType.CHANNEL_BLOCKS),
                () -> assertThat(actual.getBlocksJson().value()).isEqualTo("[]"),
                () -> assertThat(actual.getFallbackText().value()).isEqualTo(fallbackText)
        );
    }

    @Test
    void 에페메랄_블록_문자열_JSON을_전달하면_이중_직렬화하지않고_그대로_enqueue한다() {
        // given
        String sourceKey = "EVENT-EPHEMERAL-STRING-BLOCKS";
        String teamId = "T1";
        String channelId = "C1";
        String userId = "U1";
        String blocks = "[]";
        String fallbackText = "fallback";

        // when
        targetWriter().enqueueEphemeralBlocks(sourceKey, teamId, channelId, userId, blocks, fallbackText);

        // then
        SlackNotificationOutbox actual = awaitSingleOutbox(
                sourceKey,
                SlackNotificationOutboxMessageType.EPHEMERAL_BLOCKS,
                teamId,
                channelId,
                userId
        );

        assertAll(
                () -> assertThat(actual.getMessageType()).isEqualTo(SlackNotificationOutboxMessageType.EPHEMERAL_BLOCKS),
                () -> assertThat(actual.getUserId().value()).isEqualTo(userId),
                () -> assertThat(actual.getBlocksJson().value()).isEqualTo("[]"),
                () -> assertThat(actual.getFallbackText().value()).isEqualTo(fallbackText)
        );
    }

    @Test
    void 블록_문자열이_유효한_JSON이_아니면_custom_exception을_던지고_enqueue하지않는다() {
        // given
        String sourceKey = "EVENT-INVALID-BLOCKS";
        String teamId = "T1";
        String channelId = "C1";
        String invalidBlocks = "not-json";

        // when & then
        assertThatThrownBy(() -> targetWriter().enqueueChannelBlocks(
                        sourceKey,
                        teamId,
                        channelId,
                        invalidBlocks,
                        "fallback"
                ))
                        .isInstanceOf(SlackBlocksSerializationException.class)
                        .hasMessage("blocks JSON 직렬화에 실패했습니다.");
        List<SlackNotificationOutbox> actual = outboxesOf(
                sourceKey,
                SlackNotificationOutboxMessageType.CHANNEL_BLOCKS,
                teamId,
                channelId,
                ""
        );
        assertThat(actual).isEmpty();
    }

    @Test
    void 에페메랄_블록_문자열이_유효한_JSON이_아니면_custom_exception을_던지고_enqueue하지않는다() {
        // given
        String sourceKey = "EVENT-INVALID-EPHEMERAL-BLOCKS";
        String teamId = "T1";
        String channelId = "C1";
        String userId = "U1";
        String invalidBlocks = "not-json";

        // when & then
        assertThatThrownBy(() -> targetWriter().enqueueEphemeralBlocks(
                        sourceKey,
                        teamId,
                        channelId,
                        userId,
                        invalidBlocks,
                        "fallback"
                ))
                        .isInstanceOf(SlackBlocksSerializationException.class)
                        .hasMessage("blocks JSON 직렬화에 실패했습니다.");
        List<SlackNotificationOutbox> actual = outboxesOf(
                sourceKey,
                SlackNotificationOutboxMessageType.EPHEMERAL_BLOCKS,
                teamId,
                channelId,
                userId
        );
        assertThat(actual).isEmpty();
    }

    @Test
    void 에페메랄_블록_메시지의_blocks가_null이면_예외를_던지고_enqueue하지않는다() {
        // given
        String sourceKey = "EVENT-NULL-EPHEMERAL-BLOCKS";
        String teamId = "T1";
        String channelId = "C1";
        String userId = "U1";

        // when & then
        assertThatThrownBy(() -> targetWriter().enqueueEphemeralBlocks(
                        sourceKey,
                        teamId,
                        channelId,
                        userId,
                        (JsonNode) null,
                        "fallback"
                ))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("blocks는 null일 수 없습니다.");
        List<SlackNotificationOutbox> actual = outboxesOf(
                sourceKey,
                SlackNotificationOutboxMessageType.EPHEMERAL_BLOCKS,
                teamId,
                channelId,
                userId
        );
        assertThat(actual).isEmpty();
    }

    @Test
    void 채널_블록_메시지의_blocks가_null이면_예외를_던지고_enqueue하지않는다() {
        // given
        String sourceKey = "EVENT-NULL-CHANNEL-BLOCKS";
        String teamId = "T1";
        String channelId = "C1";

        // when & then
        assertThatThrownBy(() -> targetWriter().enqueueChannelBlocks(
                        sourceKey,
                        teamId,
                        channelId,
                        (JsonNode) null,
                        "fallback"
                ))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("blocks는 null일 수 없습니다.");
        List<SlackNotificationOutbox> actual = outboxesOf(
                sourceKey,
                SlackNotificationOutboxMessageType.CHANNEL_BLOCKS,
                teamId,
                channelId,
                ""
        );
        assertThat(actual).isEmpty();
    }

    @Test
    void 에페메랄_텍스트_메시지의_text가_null이면_예외를_던지고_enqueue하지않는다() {
        // given
        String sourceKey = "EVENT-NULL-EPHEMERAL-TEXT";
        String teamId = "T1";
        String channelId = "C1";
        String userId = "U1";

        // when & then
        assertThatThrownBy(() -> targetWriter().enqueueEphemeralText(
                sourceKey,
                teamId,
                channelId,
                userId,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("text는 null일 수 없습니다.");
        List<SlackNotificationOutbox> actual = outboxesOf(
                sourceKey,
                SlackNotificationOutboxMessageType.EPHEMERAL_TEXT,
                teamId,
                channelId,
                userId
        );
        assertThat(actual).isEmpty();
    }

    @Test
    void 채널_텍스트_메시지의_text가_null이면_예외를_던지고_enqueue하지않는다() {
        // given
        String sourceKey = "EVENT-NULL-CHANNEL-TEXT";
        String teamId = "T1";
        String channelId = "C1";

        // when & then
        assertThatThrownBy(() -> targetWriter().enqueueChannelText(
                sourceKey,
                teamId,
                channelId,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("text는 null일 수 없습니다.");
        List<SlackNotificationOutbox> actual = outboxesOf(
                sourceKey,
                SlackNotificationOutboxMessageType.CHANNEL_TEXT,
                teamId,
                channelId,
                ""
        );
        assertThat(actual).isEmpty();
    }

    @Test
    void 동일한_요청은_멱등성이_보장되어_중복_enqueue되지_않는다() {
        // given
        String sourceKey = "EVENT-IDEMPOTENT";
        String teamId = "T1";
        String channelId = "C1";
        String text = "hello";

        // when
        targetWriter().enqueueChannelText(sourceKey, teamId, channelId, text);
        targetWriter().enqueueChannelText(sourceKey, teamId, channelId, text);

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(outboxesOf(
                        sourceKey,
                        SlackNotificationOutboxMessageType.CHANNEL_TEXT,
                        teamId,
                        channelId,
                        ""
                )).hasSize(1)
        );
    }

    @Test
    void 기존_레코드가_SENT_상태여도_동일한_요청은_중복_enqueue되지_않는다() {
        // given
        String sourceKey = "EVENT-IDEMPOTENT-SENT";
        String teamId = "T1";
        String channelId = "C1";
        String text = "hello";

        targetWriter().enqueueChannelText(sourceKey, teamId, channelId, text);

        SlackNotificationOutbox existing = awaitSingleOutbox(
                sourceKey,
                SlackNotificationOutboxMessageType.CHANNEL_TEXT,
                teamId,
                channelId,
                ""
        );
        setProcessingState(existing, Instant.parse("2026-02-18T00:00:00Z"), 1);
        existing.markSent(Instant.parse("2026-02-18T00:00:01Z"));
        actualSlackNotificationOutboxRepository.save(existing);

        // when
        targetWriter().enqueueChannelText(sourceKey, teamId, channelId, text);

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(outboxesOf(
                        sourceKey,
                        SlackNotificationOutboxMessageType.CHANNEL_TEXT,
                        teamId,
                        channelId,
                        ""
                )).hasSize(1)
        );
        SlackNotificationOutboxStatus actualStatus = actualSlackNotificationOutboxRepository.findById(existing.getId())
                                                                                            .orElseThrow()
                                                                                            .getStatus();
        assertAll(
                () -> assertThat(actualStatus).isEqualTo(SlackNotificationOutboxStatus.SENT)
        );
    }

    @Test
    void 기존_레코드가_PROCESSING_상태여도_동일한_요청은_중복_enqueue되지_않는다() {
        // given
        String sourceKey = "EVENT-IDEMPOTENT-PROCESSING";
        String teamId = "T1";
        String channelId = "C1";
        String text = "hello";

        targetWriter().enqueueChannelText(sourceKey, teamId, channelId, text);

        SlackNotificationOutbox existing = awaitSingleOutbox(
                sourceKey,
                SlackNotificationOutboxMessageType.CHANNEL_TEXT,
                teamId,
                channelId,
                ""
        );
        setProcessingState(existing, Instant.parse("2026-02-18T00:00:00Z"), 1);
        actualSlackNotificationOutboxRepository.save(existing);

        // when
        targetWriter().enqueueChannelText(sourceKey, teamId, channelId, text);

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(outboxesOf(
                        sourceKey,
                        SlackNotificationOutboxMessageType.CHANNEL_TEXT,
                        teamId,
                        channelId,
                        ""
                )).hasSize(1)
        );
        SlackNotificationOutboxStatus actualStatus = actualSlackNotificationOutboxRepository.findById(existing.getId())
                                                                                            .orElseThrow()
                                                                                            .getStatus();
        assertAll(
                () -> assertThat(actualStatus).isEqualTo(SlackNotificationOutboxStatus.PROCESSING)
        );
    }

    @Test
    void 기존_레코드가_FAILED_상태여도_동일한_요청은_중복_enqueue되지_않는다() {
        // given
        String sourceKey = "EVENT-IDEMPOTENT-FAILED";
        String teamId = "T1";
        String channelId = "C1";
        String text = "hello";

        targetWriter().enqueueChannelText(sourceKey, teamId, channelId, text);

        SlackNotificationOutbox existing = awaitSingleOutbox(
                sourceKey,
                SlackNotificationOutboxMessageType.CHANNEL_TEXT,
                teamId,
                channelId,
                ""
        );
        setProcessingState(existing, Instant.parse("2026-02-18T00:00:00Z"), 1);
        existing.markFailed(
                Instant.parse("2026-02-18T00:00:01Z"),
                "failure",
                SlackInteractionFailureType.RETRY_EXHAUSTED
        );
        actualSlackNotificationOutboxRepository.save(existing);

        // when
        targetWriter().enqueueChannelText(sourceKey, teamId, channelId, text);

        // then
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(outboxesOf(
                        sourceKey,
                        SlackNotificationOutboxMessageType.CHANNEL_TEXT,
                        teamId,
                        channelId,
                        ""
                )).hasSize(1)
        );
        SlackNotificationOutboxStatus actualStatus = actualSlackNotificationOutboxRepository.findById(existing.getId())
                                                                                            .orElseThrow()
                                                                                            .getStatus();
        assertAll(
                () -> assertThat(actualStatus).isEqualTo(SlackNotificationOutboxStatus.FAILED)
        );
    }

    private SlackNotificationOutbox awaitSingleOutbox(
            String sourceKey,
            SlackNotificationOutboxMessageType messageType,
            String teamId,
            String channelId,
            String userId
    ) {
        AtomicReference<SlackNotificationOutbox> actual = new AtomicReference<>();

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            List<SlackNotificationOutbox> outboxes = outboxesOf(sourceKey, messageType, teamId, channelId, userId);
            assertThat(outboxes).hasSize(1);
            actual.set(outboxes.getFirst());
        });

        return actual.get();
    }

    private SlackNotificationOutboxWriter targetWriter() {
        return AopTestUtils.getTargetObject(slackNotificationOutboxWriter);
    }

    private void setProcessingState(
            SlackNotificationOutbox outbox,
            Instant processingStartedAt,
            int processingAttempt
    ) {
        outbox.claim(processingStartedAt);
        ReflectionTestUtils.setField(outbox, "processingAttempt", processingAttempt);
    }

    private List<SlackNotificationOutbox> allOutboxes() {
        return slackNotificationOutboxMybatisMapper.findAllDomains();
    }

    private List<SlackNotificationOutbox> outboxesOf(
            String sourceKey,
            SlackNotificationOutboxMessageType messageType,
            String teamId,
            String channelId,
            String userId
    ) {
        String idempotencyKey = outboxIdempotencyKey(sourceKey, messageType, teamId, channelId, userId);

        return allOutboxes().stream()
                            .filter(outbox -> idempotencyKey.equals(outbox.getIdempotencyKey()))
                            .toList();
    }

    private String outboxIdempotencyKey(
            String sourceKey,
            SlackNotificationOutboxMessageType messageType,
            String teamId,
            String channelId,
            String userId
    ) {
        String sourcePayload = outboxIdempotencyPayloadEncoder.encode(
                sourceKey,
                messageType,
                teamId,
                channelId,
                userId
        );

        return slackInteractionIdempotencyKeyGenerator.generate(
                SlackInteractionIdempotencyScope.SLACK_NOTIFICATION_OUTBOX,
                sourcePayload
        );
    }
}
