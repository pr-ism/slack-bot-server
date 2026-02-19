package com.slack.bot.application.interactivity.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.box.out.exception.SlackBlocksSerializationException;
import com.slack.bot.infrastructure.interaction.box.SlackInteractivityFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStatus;
import com.slack.bot.infrastructure.interaction.box.out.repository.SlackNotificationOutboxRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackNotificationOutboxWriterTest {

    @Autowired
    SlackNotificationOutboxWriter slackNotificationOutboxWriter;

    @Autowired
    SlackNotificationOutboxRepository slackNotificationOutboxRepository;

    @Autowired
    ObjectMapper objectMapper;

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
        List<SlackNotificationOutbox> pendings = slackNotificationOutboxRepository.findClaimable(10);

        assertThat(pendings).hasSize(1);

        SlackNotificationOutbox actual = pendings.getFirst();

        assertAll(
                () -> assertThat(actual.getMessageType()).isEqualTo(SlackNotificationOutboxMessageType.EPHEMERAL_TEXT),
                () -> assertThat(actual.getTeamId()).isEqualTo(teamId),
                () -> assertThat(actual.getChannelId()).isEqualTo(channelId),
                () -> assertThat(actual.getUserId()).isEqualTo(userId),
                () -> assertThat(actual.getText()).isEqualTo(text),
                () -> assertThat(actual.getStatus()).isEqualTo(SlackNotificationOutboxStatus.PENDING)
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
        List<SlackNotificationOutbox> pendings = slackNotificationOutboxRepository.findClaimable(10);

        assertThat(pendings).hasSize(1);

        SlackNotificationOutbox actual = pendings.getFirst();

        assertAll(
                () -> assertThat(actual.getMessageType()).isEqualTo(SlackNotificationOutboxMessageType.EPHEMERAL_BLOCKS),
                () -> assertThat(actual.getTeamId()).isEqualTo(teamId),
                () -> assertThat(actual.getChannelId()).isEqualTo(channelId),
                () -> assertThat(actual.getUserId()).isEqualTo(userId),
                () -> assertThat(actual.getBlocksJson()).isEqualTo("[]"),
                () -> assertThat(actual.getFallbackText()).isEqualTo(fallbackText),
                () -> assertThat(actual.getStatus()).isEqualTo(SlackNotificationOutboxStatus.PENDING)
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
        List<SlackNotificationOutbox> pendings = slackNotificationOutboxRepository.findClaimable(10);

        assertThat(pendings).hasSize(1);

        SlackNotificationOutbox actual = pendings.getFirst();

        assertAll(
                () -> assertThat(actual.getMessageType()).isEqualTo(SlackNotificationOutboxMessageType.CHANNEL_TEXT),
                () -> assertThat(actual.getTeamId()).isEqualTo(teamId),
                () -> assertThat(actual.getChannelId()).isEqualTo(channelId),
                () -> assertThat(actual.getText()).isEqualTo(text),
                () -> assertThat(actual.getUserId()).isNull(),
                () -> assertThat(actual.getStatus()).isEqualTo(SlackNotificationOutboxStatus.PENDING)
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
        List<SlackNotificationOutbox> pendings = slackNotificationOutboxRepository.findClaimable(10);

        assertThat(pendings).hasSize(1);

        SlackNotificationOutbox actual = pendings.getFirst();

        assertAll(
                () -> assertThat(actual.getMessageType()).isEqualTo(SlackNotificationOutboxMessageType.CHANNEL_BLOCKS),
                () -> assertThat(actual.getTeamId()).isEqualTo(teamId),
                () -> assertThat(actual.getChannelId()).isEqualTo(channelId),
                () -> assertThat(actual.getBlocksJson()).isEqualTo("[]"),
                () -> assertThat(actual.getFallbackText()).isEqualTo(fallbackText),
                () -> assertThat(actual.getUserId()).isNull(),
                () -> assertThat(actual.getStatus()).isEqualTo(SlackNotificationOutboxStatus.PENDING)
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
        List<SlackNotificationOutbox> pendings = slackNotificationOutboxRepository.findClaimable(10);

        assertThat(pendings).hasSize(1);

        SlackNotificationOutbox actual = pendings.getFirst();

        assertAll(
                () -> assertThat(actual.getMessageType()).isEqualTo(SlackNotificationOutboxMessageType.CHANNEL_BLOCKS),
                () -> assertThat(actual.getBlocksJson()).isEqualTo("[]"),
                () -> assertThat(actual.getFallbackText()).isEqualTo(fallbackText)
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
        List<SlackNotificationOutbox> pendings = slackNotificationOutboxRepository.findClaimable(10);

        assertThat(pendings).hasSize(1);

        SlackNotificationOutbox actual = pendings.getFirst();

        assertAll(
                () -> assertThat(actual.getMessageType()).isEqualTo(SlackNotificationOutboxMessageType.EPHEMERAL_BLOCKS),
                () -> assertThat(actual.getUserId()).isEqualTo(userId),
                () -> assertThat(actual.getBlocksJson()).isEqualTo("[]"),
                () -> assertThat(actual.getFallbackText()).isEqualTo(fallbackText)
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
        assertAll(
                () -> assertThatThrownBy(() -> targetWriter().enqueueChannelBlocks(
                        sourceKey,
                        teamId,
                        channelId,
                        invalidBlocks,
                        "fallback"
                ))
                        .isInstanceOf(SlackBlocksSerializationException.class)
                        .hasMessage("blocks JSON 직렬화에 실패했습니다."),
                () -> assertThat(slackNotificationOutboxRepository.findClaimable(10)).isEmpty()
        );
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
        assertAll(
                () -> assertThatThrownBy(() -> targetWriter().enqueueEphemeralBlocks(
                        sourceKey,
                        teamId,
                        channelId,
                        userId,
                        invalidBlocks,
                        "fallback"
                ))
                        .isInstanceOf(SlackBlocksSerializationException.class)
                        .hasMessage("blocks JSON 직렬화에 실패했습니다."),
                () -> assertThat(slackNotificationOutboxRepository.findClaimable(10)).isEmpty()
        );
    }

    @Test
    void 에페메랄_블록_메시지의_blocks가_null이면_예외를_던지고_enqueue하지않는다() {
        // given
        String sourceKey = "EVENT-NULL-EPHEMERAL-BLOCKS";
        String teamId = "T1";
        String channelId = "C1";
        String userId = "U1";

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> targetWriter().enqueueEphemeralBlocks(
                        sourceKey,
                        teamId,
                        channelId,
                        userId,
                        (JsonNode) null,
                        "fallback"
                ))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("blocks는 null일 수 없습니다."),
                () -> assertThat(slackNotificationOutboxRepository.findClaimable(10)).isEmpty()
        );
    }

    @Test
    void 채널_블록_메시지의_blocks가_null이면_예외를_던지고_enqueue하지않는다() {
        // given
        String sourceKey = "EVENT-NULL-CHANNEL-BLOCKS";
        String teamId = "T1";
        String channelId = "C1";

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> targetWriter().enqueueChannelBlocks(
                        sourceKey,
                        teamId,
                        channelId,
                        (JsonNode) null,
                        "fallback"
                ))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("blocks는 null일 수 없습니다."),
                () -> assertThat(slackNotificationOutboxRepository.findClaimable(10)).isEmpty()
        );
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

        assertThat(slackNotificationOutboxRepository.findClaimable(10)).isEmpty();
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

        assertThat(slackNotificationOutboxRepository.findClaimable(10)).isEmpty();
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
        List<SlackNotificationOutbox> pendings = slackNotificationOutboxRepository.findClaimable(10);

        assertThat(pendings).hasSize(1);
    }

    @Test
    void 기존_레코드가_SENT_상태여도_동일한_요청은_중복_enqueue되지_않는다() {
        // given
        String sourceKey = "EVENT-IDEMPOTENT-SENT";
        String teamId = "T1";
        String channelId = "C1";
        String text = "hello";

        targetWriter().enqueueChannelText(sourceKey, teamId, channelId, text);

        SlackNotificationOutbox existing = slackNotificationOutboxRepository.findClaimable(10).getFirst();
        existing.markProcessing(Instant.parse("2026-02-18T00:00:00Z"));
        existing.markSent(Instant.parse("2026-02-18T00:00:01Z"));
        slackNotificationOutboxRepository.save(existing);

        // when
        targetWriter().enqueueChannelText(sourceKey, teamId, channelId, text);

        // then
        assertAll(
                () -> assertThat(slackNotificationOutboxRepository.findClaimable(10)).isEmpty(),
                () -> assertThat(slackNotificationOutboxRepository.findById(existing.getId()))
                        .get()
                        .extracting(slackNotificationOutbox -> slackNotificationOutbox.getStatus())
                        .isEqualTo(SlackNotificationOutboxStatus.SENT)
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

        SlackNotificationOutbox existing = slackNotificationOutboxRepository.findClaimable(10).getFirst();
        existing.markProcessing(Instant.parse("2026-02-18T00:00:00Z"));
        slackNotificationOutboxRepository.save(existing);

        // when
        targetWriter().enqueueChannelText(sourceKey, teamId, channelId, text);

        // then
        assertAll(
                () -> assertThat(slackNotificationOutboxRepository.findClaimable(10)).isEmpty(),
                () -> assertThat(slackNotificationOutboxRepository.findById(existing.getId()))
                        .get()
                        .extracting(slackNotificationOutbox -> slackNotificationOutbox.getStatus())
                        .isEqualTo(SlackNotificationOutboxStatus.PROCESSING)
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

        SlackNotificationOutbox existing = slackNotificationOutboxRepository.findClaimable(10).getFirst();
        existing.markProcessing(Instant.parse("2026-02-18T00:00:00Z"));
        existing.markFailed(
                Instant.parse("2026-02-18T00:00:01Z"),
                "failure",
                SlackInteractivityFailureType.RETRY_EXHAUSTED
        );
        slackNotificationOutboxRepository.save(existing);

        // when
        targetWriter().enqueueChannelText(sourceKey, teamId, channelId, text);

        // then
        assertAll(
                () -> assertThat(slackNotificationOutboxRepository.findClaimable(10)).isEmpty(),
                () -> assertThat(slackNotificationOutboxRepository.findById(existing.getId()))
                        .get()
                        .extracting(slackNotificationOutbox -> slackNotificationOutbox.getStatus())
                        .isEqualTo(SlackNotificationOutboxStatus.FAILED)
        );
    }

    private SlackNotificationOutboxWriter targetWriter() {
        return AopTestUtils.getTargetObject(slackNotificationOutboxWriter);
    }
}
