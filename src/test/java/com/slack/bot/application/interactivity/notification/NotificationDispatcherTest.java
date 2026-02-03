package com.slack.bot.application.interactivity.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.slack.bot.application.interactivity.client.NotificationApiClient;
import com.slack.bot.domain.setting.DeliverySpace;
import com.slack.bot.domain.setting.NotificationSettings;
import com.slack.bot.domain.setting.repository.NotificationSettingsRepository;
import com.slack.bot.domain.setting.vo.OptionalNotifications;
import com.slack.bot.domain.setting.vo.ReservationConfirmed;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    NotificationApiClient notificationApiClient;

    @Mock
    NotificationSettingsRepository notificationSettingsRepository;

    NotificationDispatcher notificationDispatcher;

    @BeforeEach
    void setUp() {
        notificationDispatcher = new NotificationDispatcher(notificationApiClient, notificationSettingsRepository);
    }

    @Test
    void DM이_활성화된_사용자에게_블록_메시지를_에페메랄로_보낸다() {
        // given
        String token = "xoxb-test-token";
        String channelId = "C1";
        String userId = "U1";
        List<String> blocks = List.of("block1", "block2");
        String fallback = "fallback text";
        NotificationSettings settings = NotificationSettings.create(
                1L,
                ReservationConfirmed.defaults(),
                OptionalNotifications.defaults()
        );

        given(notificationSettingsRepository.findBySlackUser("T1", userId))
                .willReturn(Optional.of(settings));

        // when
        notificationDispatcher.sendBlock("T1", token, channelId, userId, blocks, fallback);

        // then
        verify(notificationApiClient).sendEphemeralBlockMessage(token, channelId, userId, blocks, fallback);
        verify(notificationApiClient, never()).openDirectMessageChannel(anyString(), anyString());
    }

    @Test
    void DM이_비활성화된_사용자에게_블록_메시지를_DM으로_보낸다() {
        // given
        String token = "xoxb-test-token";
        String channelId = "C1";
        String userId = "U2";
        List<String> blocks = List.of("block1", "block2");
        String fallback = "fallback text";
        ReservationConfirmed reservationConfirmed = ReservationConfirmed.defaults()
                .changeSpace(DeliverySpace.TRIGGER_CHANNEL);
        NotificationSettings settings = NotificationSettings.create(
                2L,
                reservationConfirmed,
                OptionalNotifications.defaults()
        );

        given(notificationSettingsRepository.findBySlackUser("T1", userId))
                .willReturn(Optional.of(settings));
        given(notificationApiClient.openDirectMessageChannel(token, userId))
                .willReturn("DM-CHANNEL-ID");

        // when
        notificationDispatcher.sendBlock("T1", token, channelId, userId, blocks, fallback);

        // then
        verify(notificationApiClient).openDirectMessageChannel(token, userId);
        verify(notificationApiClient).sendBlockMessage(token, "DM-CHANNEL-ID", blocks, fallback);
        verify(notificationApiClient, never()).sendEphemeralBlockMessage(anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void 알림_설정이_없는_사용자에게_블록_메시지를_DM으로_보낸다() {
        // given
        String token = "xoxb-test-token";
        String channelId = "C1";
        String userId = "U99";
        List<String> blocks = List.of("block1", "block2");
        String fallback = "fallback text";

        given(notificationSettingsRepository.findBySlackUser("T1", userId))
                .willReturn(Optional.empty());
        given(notificationApiClient.openDirectMessageChannel(token, userId))
                .willReturn("DM-CHANNEL-ID");

        // when
        notificationDispatcher.sendBlock("T1", token, channelId, userId, blocks, fallback);

        // then
        verify(notificationApiClient).openDirectMessageChannel(token, userId);
        verify(notificationApiClient).sendBlockMessage(token, "DM-CHANNEL-ID", blocks, fallback);
    }

    @Test
    void DM이_활성화된_사용자에게_텍스트_메시지를_DM으로_보낸다() {
        // given
        String token = "xoxb-test-token";
        String channelId = "C1";
        String userId = "U1";
        String text = "test message";
        NotificationSettings settings = NotificationSettings.defaults(1L);

        given(notificationSettingsRepository.findBySlackUser("T1", userId))
                .willReturn(Optional.of(settings));
        given(notificationApiClient.openDirectMessageChannel(token, userId))
                .willReturn("DM-CHANNEL-ID");

        // when
        notificationDispatcher.sendText("T1", token, channelId, userId, text);

        // then
        verify(notificationApiClient).openDirectMessageChannel(token, userId);
        verify(notificationApiClient).sendMessage(token, "DM-CHANNEL-ID", text);
        verify(notificationApiClient, never()).sendEphemeralMessage(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void DM이_비활성화된_사용자에게_텍스트_메시지를_에페메랄로_보낸다() {
        // given
        String token = "xoxb-test-token";
        String channelId = "C1";
        String userId = "U2";
        String text = "test message";
        NotificationSettings settings = NotificationSettings.defaults(2L);
        settings.changeReservationConfirmedSpace(DeliverySpace.TRIGGER_CHANNEL);

        given(notificationSettingsRepository.findBySlackUser("T1", userId))
                .willReturn(Optional.of(settings));

        // when
        notificationDispatcher.sendText("T1", token, channelId, userId, text);

        // then
        verify(notificationApiClient).sendEphemeralMessage(token, channelId, userId, text);
        verify(notificationApiClient, never()).openDirectMessageChannel(anyString(), anyString());
    }

    @Test
    void 에페메랄_메시지를_전송한다() {
        // given
        String token = "xoxb-test-token";
        String channelId = "C1";
        String userId = "U1";
        String text = "ephemeral message";

        // when
        notificationDispatcher.sendEphemeral(token, channelId, userId, text);

        // then
        verify(notificationApiClient).sendEphemeralMessage(token, channelId, userId, text);
    }

    @Test
    void 에페메랄_블록_메시지를_전송한다() {
        // given
        String token = "xoxb-test-token";
        String channelId = "C1";
        String userId = "U1";
        List<String> blocks = List.of("block1", "block2");
        String fallback = "fallback text";

        // when
        notificationDispatcher.sendEphemeralBlocks(token, channelId, userId, blocks, fallback);

        // then
        verify(notificationApiClient).sendEphemeralBlockMessage(token, channelId, userId, blocks, fallback);
    }

    @Test
    void DM이_활성화된_사용자에게는_DM을_보낸다() {
        // given
        String token = "xoxb-test-token";
        String userId = "U1";
        String text = "direct message";
        NotificationSettings settings = NotificationSettings.defaults(1L);

        given(notificationSettingsRepository.findBySlackUser("T1", userId))
                .willReturn(Optional.of(settings));
        given(notificationApiClient.openDirectMessageChannel(token, userId))
                .willReturn("DM-CHANNEL-ID");

        // when
        notificationDispatcher.sendDirectMessageIfEnabled("T1", token, userId, text);

        // then
        verify(notificationApiClient).openDirectMessageChannel(token, userId);
        verify(notificationApiClient).sendMessage(token, "DM-CHANNEL-ID", text);
    }

    @Test
    void DM이_비활성화된_사용자에게는_DM을_보내지_않는다() {
        // given
        String token = "xoxb-test-token";
        String userId = "U2";
        String text = "direct message";
        NotificationSettings settings = NotificationSettings.defaults(2L);
        settings.changeReservationConfirmedSpace(DeliverySpace.TRIGGER_CHANNEL);

        given(notificationSettingsRepository.findBySlackUser("T1", userId))
                .willReturn(Optional.of(settings));

        // when
        notificationDispatcher.sendDirectMessageIfEnabled("T1", token, userId, text);

        // then
        verify(notificationApiClient, never()).openDirectMessageChannel(anyString(), anyString());
        verify(notificationApiClient, never()).sendMessage(anyString(), anyString(), anyString());
    }

    @Test
    void 알림_설정이_없는_사용자에게는_DM을_보내지_않는다() {
        // given
        String token = "xoxb-test-token";
        String userId = "U99";
        String text = "direct message";

        given(notificationSettingsRepository.findBySlackUser("T1", userId))
                .willReturn(Optional.empty());

        // when
        notificationDispatcher.sendDirectMessageIfEnabled("T1", token, userId, text);

        // then
        verify(notificationApiClient, never()).openDirectMessageChannel(anyString(), anyString());
        verify(notificationApiClient, never()).sendMessage(anyString(), anyString(), anyString());
    }

    @Test
    void 알림_설정이_없는_사용자에게_텍스트_메시지를_에페메랄로_보낸다() {
        // given
        String token = "xoxb-test-token";
        String channelId = "C1";
        String userId = "U99";
        String text = "test message";

        given(notificationSettingsRepository.findBySlackUser("T1", userId))
                .willReturn(Optional.empty());

        // when
        notificationDispatcher.sendText("T1", token, channelId, userId, text);

        // then
        verify(notificationApiClient).sendEphemeralMessage(token, channelId, userId, text);
    }
}
