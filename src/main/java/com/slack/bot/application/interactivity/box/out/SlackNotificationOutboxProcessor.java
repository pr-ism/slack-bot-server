package com.slack.bot.application.interactivity.box.out;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interactivity.box.InteractivityFailureReasonTruncator;
import com.slack.bot.application.interactivity.box.retry.InteractivityRetryExceptionClassifier;
import com.slack.bot.application.interactivity.box.out.exception.UnsupportedSlackNotificationOutboxMessageTypeException;
import com.slack.bot.global.config.properties.InteractivityRetryProperties;
import com.slack.bot.infrastructure.interaction.box.SlackInteractivityFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import com.slack.bot.infrastructure.interaction.box.out.repository.SlackNotificationOutboxRepository;
import com.slack.bot.infrastructure.interaction.client.NotificationTransportApiClient;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackNotificationOutboxProcessor {

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final RetryTemplate slackNotificationOutboxRetryTemplate;
    private final NotificationTransportApiClient notificationTransportApiClient;
    private final SlackNotificationOutboxRepository slackNotificationOutboxRepository;
    private final InteractivityRetryProperties interactivityRetryProperties;
    private final InteractivityFailureReasonTruncator failureReasonTruncator;
    private final InteractivityRetryExceptionClassifier retryExceptionClassifier;

    public void processPending(int limit) {
        List<SlackNotificationOutbox> pendings = slackNotificationOutboxRepository.findPending(limit);

        for (SlackNotificationOutbox pending : pendings) {
            processOne(pending);
        }
    }

    private void processOne(SlackNotificationOutbox pending) {
        Optional.ofNullable(pending.getId())
                .filter(id -> slackNotificationOutboxRepository.markProcessingIfPending(id, clock.instant()))
                .flatMap(slackNotificationOutboxRepository::findById)
                .ifPresent(outbox -> {
                    try {
                        slackNotificationOutboxRetryTemplate.execute(context -> {
                            dispatch(outbox);
                            return null;
                        });
                        outbox.markSent(clock.instant());
                        slackNotificationOutboxRepository.save(outbox);
                    } catch (Exception e) {
                        log.error("슬랙 알림 outbox 처리에 실패했습니다. outboxId={}", outbox.getId(), e);
                        markFailureStatus(outbox, e);
                        slackNotificationOutboxRepository.save(outbox);
                    }
                });
    }

    private void dispatch(SlackNotificationOutbox outbox) throws JsonProcessingException {
        SlackNotificationOutboxMessageType messageType = outbox.getMessageType();

        if (messageType == SlackNotificationOutboxMessageType.EPHEMERAL_TEXT) {
            notificationTransportApiClient.sendEphemeralMessage(
                    outbox.getToken(),
                    outbox.getChannelId(),
                    outbox.getUserId(),
                    outbox.getText()
            );
            return;
        }
        if (messageType == SlackNotificationOutboxMessageType.EPHEMERAL_BLOCKS) {
            notificationTransportApiClient.sendEphemeralBlockMessage(
                    outbox.getToken(),
                    outbox.getChannelId(),
                    outbox.getUserId(),
                    readBlocks(outbox.getBlocksJson()),
                    outbox.getFallbackText()
            );
            return;
        }
        if (messageType == SlackNotificationOutboxMessageType.CHANNEL_TEXT) {
            notificationTransportApiClient.sendMessage(outbox.getToken(), outbox.getChannelId(), outbox.getText());
            return;
        }
        if (messageType == SlackNotificationOutboxMessageType.CHANNEL_BLOCKS) {
            notificationTransportApiClient.sendBlockMessage(
                    outbox.getToken(),
                    outbox.getChannelId(),
                    readBlocks(outbox.getBlocksJson()),
                    outbox.getFallbackText()
            );
            return;
        }

        throw new UnsupportedSlackNotificationOutboxMessageTypeException(messageType);
    }

    private JsonNode readBlocks(String blocksJson) throws JsonProcessingException {
        return objectMapper.readTree(blocksJson);
    }

    private void markFailureStatus(SlackNotificationOutbox outbox, Exception exception) {
        String reason = failureReasonTruncator.truncate(exception.getMessage());

        if (!retryExceptionClassifier.isRetryable(exception)) {
            outbox.markFailed(clock.instant(), reason, SlackInteractivityFailureType.BUSINESS_INVARIANT);
            return;
        }

        if (outbox.getProcessingAttempt() < interactivityRetryProperties.outbox().maxAttempts()) {
            outbox.markRetryPending(clock.instant(), reason);
            return;
        }

        outbox.markFailed(clock.instant(), reason, SlackInteractivityFailureType.RETRY_EXHAUSTED);
    }
}
