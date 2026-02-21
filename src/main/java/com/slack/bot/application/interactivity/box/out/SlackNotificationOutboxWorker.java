package com.slack.bot.application.interactivity.box.out;

import com.slack.bot.global.config.properties.InteractionWorkerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackNotificationOutboxWorker {

    private static final int DEFAULT_BATCH_SIZE = 50;

    private final InteractionWorkerProperties interactionWorkerProperties;
    private final SlackNotificationOutboxProcessor slackNotificationOutboxProcessor;

    @Scheduled(fixedDelayString = "${app.interaction.outbox.poll-delay-ms:200}")
    public void processPendingOutbox() {
        if (!interactionWorkerProperties.outbox().workerEnabled()) {
            return;
        }

        try {
            slackNotificationOutboxProcessor.processPending(DEFAULT_BATCH_SIZE);
        } catch (Exception e) {
            log.error("슬랙 알림 outbox worker 실행에 실패했습니다.", e);
        }
    }
}
