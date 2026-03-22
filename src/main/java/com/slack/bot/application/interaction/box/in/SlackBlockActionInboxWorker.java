package com.slack.bot.application.interaction.box.in;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@RequiredArgsConstructor
public class SlackBlockActionInboxWorker {
    private final SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    @Scheduled(fixedDelayString = "${app.interaction.inbox.block-actions.poll-delay-ms:1000}")
    public void processBlockActionInbox() {
        try {
            slackInteractionInboxProcessor.processPendingBlockActions(30);
        } catch (Exception e) {
            log.error("block_actions inbox worker 실행에 실패했습니다.", e);
        }
    }
}
