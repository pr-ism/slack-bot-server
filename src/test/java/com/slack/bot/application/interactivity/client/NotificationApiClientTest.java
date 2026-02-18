package com.slack.bot.application.interactivity.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.slack.bot.application.interactivity.box.out.OutboxIdempotencySourceContext;
import com.slack.bot.application.interactivity.box.out.SlackNotificationOutboxWriter;
import com.slack.bot.application.interactivity.box.out.exception.OutboxWorkspaceNotFoundException;
import com.slack.bot.infrastructure.interaction.client.NotificationTransportApiClient;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NotificationApiClientTest {

    NotificationApiClient notificationApiClient;

    SlackNotificationOutboxWriter slackNotificationOutboxWriter;
    NotificationTransportApiClient notificationTransportApiClient;
    OutboxIdempotencySourceContext outboxIdempotencySourceContext;
    WorkspaceAccessTokenTeamIdResolver workspaceAccessTokenTeamIdResolver;

    @BeforeEach
    void setUp() {
        slackNotificationOutboxWriter = mock(SlackNotificationOutboxWriter.class);
        notificationTransportApiClient = mock(NotificationTransportApiClient.class);
        outboxIdempotencySourceContext = mock(OutboxIdempotencySourceContext.class);
        workspaceAccessTokenTeamIdResolver = mock(WorkspaceAccessTokenTeamIdResolver.class);
        given(workspaceAccessTokenTeamIdResolver.resolve("token")).willReturn("T1");
        given(outboxIdempotencySourceContext.currentSourceKey()).willReturn(Optional.of("INBOX:1"));

        notificationApiClient = new NotificationApiClient(
                slackNotificationOutboxWriter,
                notificationTransportApiClient,
                outboxIdempotencySourceContext,
                workspaceAccessTokenTeamIdResolver
        );
    }

    @Test
    void 에페메랄_텍스트_메시지는_outbox에_적재한다() {
        // when
        notificationApiClient.sendEphemeralMessage("token", "C1", "U1", "hello");

        // then
        verify(slackNotificationOutboxWriter).enqueueEphemeralText(
                eq("INBOX:1"),
                eq("T1"),
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
                eq("INBOX:1"),
                eq("T1"),
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
                eq("INBOX:1"),
                eq("T1"),
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
                eq("INBOX:1"),
                eq("T1"),
                eq("C1"),
                eq("[]"),
                eq("fallback")
        );
    }

    @Test
    void source_컨텍스트가_없으면_ad_hoc_source를_생성해_outbox에_적재한다() {
        // given
        given(outboxIdempotencySourceContext.currentSourceKey()).willReturn(Optional.empty());

        // when
        notificationApiClient.sendMessage("token", "C1", "hello");

        // then
        verify(slackNotificationOutboxWriter).enqueueChannelText(
                argThat(sourceKey -> sourceKey != null && sourceKey.startsWith("BUSINESS:ADHOC:")),
                eq("T1"),
                eq("C1"),
                eq("hello")
        );
    }

    @Test
    void 명시적_source를_전달하면_컨텍스트_대신_해당_source를_사용한다() {
        // when
        notificationApiClient.sendMessage("EVENT-1", "token", "C1", "hello");

        // then
        verify(slackNotificationOutboxWriter).enqueueChannelText(
                eq("EVENT-1"),
                eq("T1"),
                eq("C1"),
                eq("hello")
        );
    }

    @Test
    void 토큰에_해당하는_workspace가_없으면_custom_exception을_던진다() {
        // given
        given(workspaceAccessTokenTeamIdResolver.resolve("unknown-token"))
                .willThrow(OutboxWorkspaceNotFoundException.forToken("unknown-token"));

        // when & then
        assertThatThrownBy(() -> notificationApiClient.sendMessage("unknown-token", "C1", "hello"))
                .isInstanceOf(OutboxWorkspaceNotFoundException.class)
                .hasMessageContaining("outbox 적재 대상 워크스페이스를 찾을 수 없습니다.");
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
