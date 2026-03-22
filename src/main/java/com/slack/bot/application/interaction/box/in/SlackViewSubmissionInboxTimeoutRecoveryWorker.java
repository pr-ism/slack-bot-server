package com.slack.bot.application.interaction.box.in;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@RequiredArgsConstructor
public class SlackViewSubmissionInboxTimeoutRecoveryWorker {

    private final SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    @Scheduled(fixedDelayString = "${app.interaction.inbox.view-submission.poll-delay-ms:1000}")
    public void recoverTimeoutViewSubmissionInbox() {
        try {
            slackInteractionInboxProcessor.recoverViewSubmissionTimeoutProcessing();
        } catch (Exception e) {
            log.error("view_submission inbox timeout recovery worker 실행에 실패했습니다.", e);
        }
    }
}
