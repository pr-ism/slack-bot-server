package com.slack.bot.application.interaction.box.out;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@RequiredArgsConstructor
public class SlackNotificationOutboxTimeoutRecoveryWorker {

    private final SlackNotificationOutboxProcessor slackNotificationOutboxProcessor;

    @Scheduled(fixedDelayString = "${app.interaction.outbox.poll-delay-ms:1000}")
    public void recoverTimeoutOutbox() {
        try {
            slackNotificationOutboxProcessor.recoverTimeoutProcessing();
        } catch (Exception e) {
            log.error("슬랙 알림 outbox timeout recovery worker 실행에 실패했습니다.", e);
        }
    }
}
