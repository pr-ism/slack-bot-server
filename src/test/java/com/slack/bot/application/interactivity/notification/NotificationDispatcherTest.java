package com.slack.bot.application.interactivity.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interactivity.client.NotificationApiClient;
import com.slack.bot.domain.setting.DeliverySpace;
import com.slack.bot.domain.setting.NotificationSettings;
import com.slack.bot.domain.setting.repository.NotificationSettingsRepository;
import com.slack.bot.domain.setting.vo.OptionalNotifications;
import com.slack.bot.domain.setting.vo.ReservationConfirmed;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        JsonNode blocks = blocks();
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
        JsonNode blocks = blocks();
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
        JsonNode blocks = blocks();
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
        JsonNode blocks = blocks();
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
    void DM이_활성화된_경우_기본_DM_전송_메서드는_DM을_전송한다() {
        // given
        String token = "xoxb-test-token";
        String userId = "U1";
        String text = "test message";
        NotificationSettings settings = NotificationSettings.defaults(1L);

        given(notificationSettingsRepository.findBySlackUser("T1", userId))
                .willReturn(Optional.of(settings));
        given(notificationApiClient.openDirectMessageChannel(token, userId))
                .willReturn("DM-CHANNEL-ID");

        // when
        notificationDispatcher.sendDirectMessageBySettingOrDefault("T1", token, userId, text);

        // then
        verify(notificationApiClient).openDirectMessageChannel(token, userId);
        verify(notificationApiClient).sendMessage(token, "DM-CHANNEL-ID", text);
    }

    @Test
    void 알림_설정이_없는_경우_기본_DM_전송_메서드는_기본값으로_DM을_전송한다() {
        // given
        String token = "xoxb-test-token";
        String userId = "U99";
        String text = "test message";

        given(notificationSettingsRepository.findBySlackUser("T1", userId))
                .willReturn(Optional.empty());
        given(notificationApiClient.openDirectMessageChannel(token, userId))
                .willReturn("DM-CHANNEL-ID");

        // when
        notificationDispatcher.sendDirectMessageBySettingOrDefault("T1", token, userId, text);

        // then
        verify(notificationApiClient).openDirectMessageChannel(token, userId);
        verify(notificationApiClient).sendMessage(token, "DM-CHANNEL-ID", text);
    }

    @Test
    void DM이_비활성화된_경우_기본_DM_전송_메서드는_DM을_전송하지_않는다() {
        // given
        String token = "xoxb-test-token";
        String userId = "U2";
        String text = "test message";
        NotificationSettings settings = NotificationSettings.defaults(2L);
        settings.changeReservationConfirmedSpace(DeliverySpace.TRIGGER_CHANNEL);

        given(notificationSettingsRepository.findBySlackUser("T1", userId))
                .willReturn(Optional.of(settings));

        // when
        notificationDispatcher.sendDirectMessageBySettingOrDefault("T1", token, userId, text);

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

    @Test
    void 블록을_에페메랄과_DM으로_함께_전송한다() {
        // given
        String token = "xoxb-test-token";
        String channelId = "C1";
        String userId = "U1";
        JsonNode blocks = blocks();
        String fallback = "fallback text";

        given(notificationApiClient.openDirectMessageChannel(token, userId))
                .willReturn("DM-CHANNEL-ID");

        // when
        notificationDispatcher.sendBlockToDmAndEphemeral(token, channelId, userId, blocks, fallback);

        // then
        verify(notificationApiClient).sendEphemeralBlockMessage(token, channelId, userId, blocks, fallback);
        verify(notificationApiClient).sendBlockMessage(token, "DM-CHANNEL-ID", blocks, fallback);
    }

    @Test
    void DM_블록_전송_중_예외가_발생해도_에페메랄은_전송한다() {
        // given
        String token = "xoxb-test-token";
        String channelId = "C1";
        String userId = "U1";
        JsonNode blocks = blocks();
        String fallback = "fallback text";

        doThrow(new RuntimeException("dm open failed"))
                .when(notificationApiClient)
                .openDirectMessageChannel(token, userId);

        // when
        notificationDispatcher.sendBlockToDmAndEphemeral(token, channelId, userId, blocks, fallback);

        // then
        verify(notificationApiClient).sendEphemeralBlockMessage(token, channelId, userId, blocks, fallback);
        verify(notificationApiClient).openDirectMessageChannel(token, userId);
    }

    @Test
    void 블록을_DM으로만_전송한다() {
        // given
        String token = "xoxb-test-token";
        String userId = "U1";
        JsonNode blocks = blocks();
        String fallback = "fallback text";

        given(notificationApiClient.openDirectMessageChannel(token, userId))
                .willReturn("DM-CHANNEL-ID");

        // when
        notificationDispatcher.sendBlockToDirectMessageOnly(token, userId, blocks, fallback);

        // then
        verify(notificationApiClient).openDirectMessageChannel(token, userId);
        verify(notificationApiClient).sendBlockMessage(token, "DM-CHANNEL-ID", blocks, fallback);
    }

    @Test
    void DM으로만_블록_전송_중_예외가_발생해도_예외를_전파하지_않는다() {
        // given
        String token = "xoxb-test-token";
        String userId = "U1";
        JsonNode blocks = blocks();
        String fallback = "fallback text";

        doThrow(new RuntimeException("dm open failed"))
                .when(notificationApiClient)
                .openDirectMessageChannel(token, userId);

        // when
        notificationDispatcher.sendBlockToDirectMessageOnly(token, userId, blocks, fallback);

        // then
        verify(notificationApiClient).openDirectMessageChannel(token, userId);
        verify(notificationApiClient, never()).sendBlockMessage(anyString(), anyString(), any(), anyString());
    }

    @Test
    void 예약_채널_에페메랄_설정이_켜져_있으면_에페메랄과_DM을_전송한다() {
        // given
        String token = "xoxb-test-token";
        String teamId = "T1";
        String channelId = "C1";
        String userId = "U1";
        JsonNode blocks = blocks();
        String fallback = "fallback text";
        String ephemeralText = "예약 완료";
        NotificationSettings settings = NotificationSettings.defaults(1L);

        given(notificationSettingsRepository.findBySlackUser(teamId, userId))
                .willReturn(Optional.of(settings));
        given(notificationApiClient.openDirectMessageChannel(token, userId))
                .willReturn("DM-CHANNEL-ID");

        // when
        notificationDispatcher.sendReservationBlockBySettingOrDefault(
                token,
                teamId,
                channelId,
                userId,
                blocks,
                fallback,
                ephemeralText
        );

        // then
        verify(notificationApiClient).sendEphemeralMessage(token, channelId, userId, ephemeralText);
        verify(notificationApiClient).sendBlockMessage(token, "DM-CHANNEL-ID", blocks, fallback);
    }

    @Test
    void 예약_채널_에페메랄_설정이_꺼져_있으면_DM만_전송한다() {
        // given
        String token = "xoxb-test-token";
        String teamId = "T1";
        String channelId = "C1";
        String userId = "U1";
        JsonNode blocks = blocks();
        String fallback = "fallback text";
        String ephemeralText = "예약 완료";
        NotificationSettings settings = NotificationSettings.defaults(1L);
        settings.updateReservationChannelEphemeral(false);

        given(notificationSettingsRepository.findBySlackUser(teamId, userId))
                .willReturn(Optional.of(settings));
        given(notificationApiClient.openDirectMessageChannel(token, userId))
                .willReturn("DM-CHANNEL-ID");

        // when
        notificationDispatcher.sendReservationBlockBySettingOrDefault(
                token,
                teamId,
                channelId,
                userId,
                blocks,
                fallback,
                ephemeralText
        );

        // then
        verify(notificationApiClient, never()).sendEphemeralMessage(anyString(), anyString(), anyString(), anyString());
        verify(notificationApiClient).sendBlockMessage(token, "DM-CHANNEL-ID", blocks, fallback);
    }

    @Test
    void 알림_설정이_없는_경우_예약_블록은_에페메랄과_DM을_모두_전송한다() {
        // given
        String token = "xoxb-test-token";
        String teamId = "T1";
        String channelId = "C1";
        String userId = "U99";
        JsonNode blocks = blocks();
        String fallback = "fallback text";
        String ephemeralText = "예약 완료";

        given(notificationSettingsRepository.findBySlackUser(teamId, userId))
                .willReturn(Optional.empty());
        given(notificationApiClient.openDirectMessageChannel(token, userId))
                .willReturn("DM-CHANNEL-ID");

        // when
        notificationDispatcher.sendReservationBlockBySettingOrDefault(
                token,
                teamId,
                channelId,
                userId,
                blocks,
                fallback,
                ephemeralText
        );

        // then
        verify(notificationApiClient).sendEphemeralMessage(token, channelId, userId, ephemeralText);
        verify(notificationApiClient).sendBlockMessage(token, "DM-CHANNEL-ID", blocks, fallback);
    }

    @Test
    void DM이_비활성화된_경우에도_예약_블록은_에페메랄과_DM을_전송한다() {
        // given
        String token = "xoxb-test-token";
        String teamId = "T1";
        String channelId = "C1";
        String userId = "U2";
        JsonNode blocks = blocks();
        String fallback = "fallback text";
        String ephemeralText = "예약 완료";
        NotificationSettings settings = NotificationSettings.defaults(2L);
        settings.changeReservationConfirmedSpace(DeliverySpace.TRIGGER_CHANNEL);

        given(notificationSettingsRepository.findBySlackUser(teamId, userId))
                .willReturn(Optional.of(settings));
        given(notificationApiClient.openDirectMessageChannel(token, userId))
                .willReturn("DM-CHANNEL-ID");

        // when
        notificationDispatcher.sendReservationBlockBySettingOrDefault(
                token,
                teamId,
                channelId,
                userId,
                blocks,
                fallback,
                ephemeralText
        );

        // then
        verify(notificationApiClient).sendEphemeralMessage(token, channelId, userId, ephemeralText);
        verify(notificationApiClient).sendBlockMessage(token, "DM-CHANNEL-ID", blocks, fallback);
    }

    private JsonNode blocks() {
        return OBJECT_MAPPER.createArrayNode()
                            .add("block1")
                            .add("block2");
    }
}
