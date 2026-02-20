package com.slack.bot.application.interactivity.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.interactivity.client.NotificationApiClient;
import com.slack.bot.domain.setting.repository.NotificationSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationApiClient notificationApiClient;
    private final NotificationSettingsRepository notificationSettingsRepository;

    public void sendEphemeral(String token, String channelId, String userId, String text) {
        notificationApiClient.sendEphemeralMessage(token, channelId, userId, text);
    }

    public void sendEphemeralBlocks(String token, String channelId, String userId, JsonNode blocks, String fallback) {
        notificationApiClient.sendEphemeralBlockMessage(token, channelId, userId, blocks, fallback);
    }

    public void sendBlockToDmAndEphemeral(
            String token,
            String channelId,
            String userId,
            JsonNode blocks,
            String fallback
    ) {
        sendEphemeralBlocks(token, channelId, userId, blocks, fallback);

        try {
            sendDirectMessageBlocks(token, userId, blocks, fallback);
        } catch (RuntimeException e) {
            log.warn("DM 블록 메시지 전송 실패. userId={}", userId, e);
        }
    }

    public void sendReservationBlockBySettingOrDefault(
            String token,
            String teamId,
            String channelId,
            String userId,
            JsonNode blocks,
            String fallback,
            String ephemeralText
    ) {
        boolean sendChannelEphemeral = notificationSettingsRepository.findBySlackUser(teamId, userId)
                                                                     .map(settings -> settings.isReservationChannelEphemeralEnabled())
                                                                     .orElse(true);

        if (sendChannelEphemeral) {
            sendEphemeral(token, channelId, userId, ephemeralText);
        }

        try {
            sendDirectMessageBlocks(token, userId, blocks, fallback);
        } catch (RuntimeException e) {
            log.warn("DM 블록 메시지 전송 실패. userId={}", userId, e);
        }
    }

    public void sendBlockToDirectMessageOnly(
            String token,
            String userId,
            JsonNode blocks,
            String fallback
    ) {
        try {
            sendDirectMessageBlocks(token, userId, blocks, fallback);
        } catch (RuntimeException e) {
            log.warn("DM 블록 메시지 전송 실패. userId={}", userId, e);
        }
    }

    public void sendBlock(
            String teamId,
            String token,
            String channelId,
            String userId,
            JsonNode blocks,
            String fallback
    ) {
        notificationSettingsRepository.findBySlackUser(teamId, userId)
                                      .ifPresentOrElse(
                                              settings -> {
                                                  if (settings.isDirectMessageEnabled()) {
                                                      sendEphemeralBlocks(token, channelId, userId, blocks, fallback);
                                                      return;
                                                  }
                                                  sendDirectMessageBlocks(token, userId, blocks, fallback);
                                              },
                                              () -> sendDirectMessageBlocks(token, userId, blocks, fallback)
                                      );
    }

    public void sendText(String teamId,
            String token,
            String channelId,
            String userId,
            String text) {

        notificationSettingsRepository.findBySlackUser(teamId, userId)
                                      .ifPresentOrElse(
                                              settings -> {
                                                  if (settings.isDirectMessageEnabled()) {
                                                      sendDmText(token, userId, text);
                                                      return;
                                                  }
                                                  sendEphemeral(token, channelId, userId, text);
                                              },
                                              () -> sendEphemeral(token, channelId, userId, text)
                                      );
    }

    public void sendDirectMessageIfEnabled(String teamId, String token, String userId, String text) {
        notificationSettingsRepository.findBySlackUser(teamId, userId)
                                      .ifPresent(
                                              settings -> {
                                                  if (settings.isDirectMessageEnabled()) {
                                                      sendDmText(token, userId, text);
                                                  }
                                              }
                                      );
    }

    public void sendDirectMessageBySettingOrDefault(
            String teamId,
            String token,
            String userId,
            String text
    ) {
        notificationSettingsRepository.findBySlackUser(teamId, userId)
                                      .ifPresentOrElse(
                                              settings -> {
                                                  if (settings.isDirectMessageEnabled()) {
                                                      sendDmText(token, userId, text);
                                                  }
                                              },
                                              () -> sendDmText(token, userId, text)
                                      );
    }

    private void sendDmText(String token, String userId, String text) {
        String dmChannelId = notificationApiClient.openDirectMessageChannel(token, userId);

        notificationApiClient.sendMessage(token, dmChannelId, text);
    }

    private void sendDirectMessageBlocks(String token, String userId, JsonNode blocks, String fallback) {
        String dmChannelId = notificationApiClient.openDirectMessageChannel(token, userId);

        notificationApiClient.sendBlockMessage(token, dmChannelId, blocks, fallback);
    }
}
