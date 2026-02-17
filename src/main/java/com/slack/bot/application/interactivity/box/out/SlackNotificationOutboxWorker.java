package com.slack.bot.application.interactivity.box.out;

import com.slack.bot.global.config.properties.InteractivityWorkerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackNotificationOutboxWorker {

    private static final int DEFAULT_BATCH_SIZE = 50;

    private final SlackNotificationOutboxProcessor slackNotificationOutboxProcessor;
    private final InteractivityWorkerProperties interactivityWorkerProperties;

    @Scheduled(fixedDelayString = "${app.interactivity.outbox.poll-delay-ms:200}")
    public void processPendingOutbox() {
        if (!interactivityWorkerProperties.outbox().workerEnabled()) {
            return;
        }

        try {
            slackNotificationOutboxProcessor.processPending(DEFAULT_BATCH_SIZE);
        } catch (Exception e) {
            log.error("슬랙 알림 outbox worker 실행에 실패했습니다.", e);
        }
    }
}

