package com.slack.bot.application.interactivity.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.slack.bot.application.interactivity.box.out.OutboxIdempotencySourceContext;
import com.slack.bot.application.interactivity.box.out.SlackNotificationOutboxWriter;
import com.slack.bot.infrastructure.interaction.client.NotificationTransportApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NotificationApiClientTest {

    NotificationApiClient notificationApiClient;

    SlackNotificationOutboxWriter slackNotificationOutboxWriter;
    NotificationTransportApiClient notificationTransportApiClient;
    OutboxIdempotencySourceContext outboxIdempotencySourceContext;

    @BeforeEach
    void setUp() {
        slackNotificationOutboxWriter = mock(SlackNotificationOutboxWriter.class);
        notificationTransportApiClient = mock(NotificationTransportApiClient.class);
        outboxIdempotencySourceContext = mock(OutboxIdempotencySourceContext.class);
        notificationApiClient = new NotificationApiClient(
                slackNotificationOutboxWriter,
                notificationTransportApiClient,
                outboxIdempotencySourceContext
        );
    }

    @Test
    void 에페메랄_텍스트_메시지는_outbox에_적재한다() {
        // when
        notificationApiClient.sendEphemeralMessage("token", "C1", "U1", "hello");

        // then
        verify(slackNotificationOutboxWriter).enqueueEphemeralText(
                isNull(),
                eq("token"),
                eq("C1"),
                eq("U1"),
                eq("hello")
        );
    }

    @Test
    void 에페메랄_블록_메시지는_outbox에_적재한다() {
        // when
        notificationApiClient.sendEphemeralBlockMessage("token", "C1", "U1", "[]", "fallback");

        // then
        verify(slackNotificationOutboxWriter).enqueueEphemeralBlocks(
                isNull(),
                eq("token"),
                eq("C1"),
                eq("U1"),
                eq("[]"),
                eq("fallback")
        );
    }

    @Test
    void 채널_텍스트_메시지는_outbox에_적재한다() {
        // when
        notificationApiClient.sendMessage("token", "C1", "hello");

        // then
        verify(slackNotificationOutboxWriter).enqueueChannelText(
                isNull(),
                eq("token"),
                eq("C1"),
                eq("hello")
        );
    }

    @Test
    void 채널_블록_메시지는_outbox에_적재한다() {
        // when
        notificationApiClient.sendBlockMessage("token", "C1", "[]", "fallback");

        // then
        verify(slackNotificationOutboxWriter).enqueueChannelBlocks(
                isNull(),
                eq("token"),
                eq("C1"),
                eq("[]"),
                eq("fallback")
        );
    }

    @Test
    void 모달_오픈은_전송_클라이언트에_위임한다() {
        // when
        notificationApiClient.openModal("token", "TRIGGER", "{}");

        // then
        verify(notificationTransportApiClient).openModal("token", "TRIGGER", "{}");
    }

    @Test
    void DM_채널_오픈은_전송_클라이언트에_위임한다() {
        // given
        given(notificationTransportApiClient.openDirectMessageChannel("token", "U1"))
                .willReturn("D123");

        // when
        String channelId = notificationApiClient.openDirectMessageChannel("token", "U1");

        // then
        assertAll(
                () -> assertThat(channelId).isEqualTo("D123"),
                () -> verify(notificationTransportApiClient).openDirectMessageChannel("token", "U1")
        );
    }

    @Test
    void 비즈니스_이벤트_source_컨텍스트_실행을_위임한다() {
        // given
        given(outboxIdempotencySourceContext.withBusinessEventSource(eq("EVENT-1"), any()))
                .willReturn("done");

        // when
        String actual = notificationApiClient.withBusinessEventSource("EVENT-1", () -> "ignored");

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo("done"),
                () -> verify(outboxIdempotencySourceContext).withBusinessEventSource(eq("EVENT-1"), any())
        );
    }
}
