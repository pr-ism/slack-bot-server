package com.slack.bot.application.interactivity.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStatus;
import com.slack.bot.infrastructure.interaction.box.out.repository.SlackNotificationOutboxRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
        String token = "xoxb-token";
        String channelId = "C1";
        String userId = "U1";
        String text = "hello";

        // when
        slackNotificationOutboxWriter.enqueueEphemeralText(sourceKey, token, channelId, userId, text);

        // then
        List<SlackNotificationOutbox> pendings = slackNotificationOutboxRepository.findPending(10);

        assertThat(pendings).hasSize(1);

        SlackNotificationOutbox actual = pendings.getFirst();

        assertAll(
                () -> assertThat(actual.getMessageType()).isEqualTo(SlackNotificationOutboxMessageType.EPHEMERAL_TEXT),
                () -> assertThat(actual.getToken()).isEqualTo(token),
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
        String token = "xoxb-token";
        String channelId = "C1";
        String userId = "U1";
        Object blocks = List.of();
        String fallbackText = "fallback";

        // when
        slackNotificationOutboxWriter.enqueueEphemeralBlocks(sourceKey, token, channelId, userId, blocks, fallbackText);

        // then
        List<SlackNotificationOutbox> pendings = slackNotificationOutboxRepository.findPending(10);

        assertThat(pendings).hasSize(1);

        SlackNotificationOutbox actual = pendings.getFirst();

        assertAll(
                () -> assertThat(actual.getMessageType()).isEqualTo(SlackNotificationOutboxMessageType.EPHEMERAL_BLOCKS),
                () -> assertThat(actual.getToken()).isEqualTo(token),
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
        String token = "xoxb-token";
        String channelId = "C1";
        String text = "hello channel";

        // when
        slackNotificationOutboxWriter.enqueueChannelText(sourceKey, token, channelId, text);

        // then
        List<SlackNotificationOutbox> pendings = slackNotificationOutboxRepository.findPending(10);

        assertThat(pendings).hasSize(1);

        SlackNotificationOutbox actual = pendings.getFirst();

        assertAll(
                () -> assertThat(actual.getMessageType()).isEqualTo(SlackNotificationOutboxMessageType.CHANNEL_TEXT),
                () -> assertThat(actual.getToken()).isEqualTo(token),
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
        String token = "xoxb-token";
        String channelId = "C1";
        Object blocks = List.of();
        String fallbackText = "fallback";

        // when
        slackNotificationOutboxWriter.enqueueChannelBlocks(sourceKey, token, channelId, blocks, fallbackText);

        // then
        List<SlackNotificationOutbox> pendings = slackNotificationOutboxRepository.findPending(10);

        assertThat(pendings).hasSize(1);

        SlackNotificationOutbox actual = pendings.getFirst();

        assertAll(
                () -> assertThat(actual.getMessageType()).isEqualTo(SlackNotificationOutboxMessageType.CHANNEL_BLOCKS),
                () -> assertThat(actual.getToken()).isEqualTo(token),
                () -> assertThat(actual.getChannelId()).isEqualTo(channelId),
                () -> assertThat(actual.getBlocksJson()).isEqualTo("[]"),
                () -> assertThat(actual.getFallbackText()).isEqualTo(fallbackText),
                () -> assertThat(actual.getUserId()).isNull(),
                () -> assertThat(actual.getStatus()).isEqualTo(SlackNotificationOutboxStatus.PENDING)
        );
    }

    @Test
    void 동일한_요청은_멱등성이_보장되어_중복_enqueue되지_않는다() {
        // given
        String sourceKey = "EVENT-IDEMPOTENT";
        String token = "xoxb-token";
        String channelId = "C1";
        String text = "hello";

        // when
        slackNotificationOutboxWriter.enqueueChannelText(sourceKey, token, channelId, text);
        slackNotificationOutboxWriter.enqueueChannelText(sourceKey, token, channelId, text);

        // then
        List<SlackNotificationOutbox> pendings = slackNotificationOutboxRepository.findPending(10);
        assertThat(pendings).hasSize(1);
    }
}
