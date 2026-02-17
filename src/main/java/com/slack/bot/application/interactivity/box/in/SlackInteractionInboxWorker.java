package com.slack.bot.application.interactivity.box.in;

import com.slack.bot.global.config.properties.InteractivityWorkerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackInteractionInboxWorker {

    private static final int DEFAULT_BATCH_SIZE = 30;

    private final SlackInteractionInboxProcessor slackInteractionInboxProcessor;
    private final InteractivityWorkerProperties interactivityWorkerProperties;

    @Scheduled(fixedDelayString = "${app.interactivity.inbox.block-actions.poll-delay-ms:200}")
    public void processBlockActionInbox() {
        if (isBlockActionWorkerDisabled()) {
            return;
        }

        try {
            slackInteractionInboxProcessor.processPendingBlockActions(DEFAULT_BATCH_SIZE);
        } catch (Exception e) {
            log.error("block_actions inbox worker 실행에 실패했습니다.", e);
        }
    }

    @Scheduled(fixedDelayString = "${app.interactivity.inbox.view-submission.poll-delay-ms:200}")
    public void processViewSubmissionInbox() {
        if (isViewSubmissionWorkerDisabled()) {
            return;
        }

        try {
            slackInteractionInboxProcessor.processPendingViewSubmissions(DEFAULT_BATCH_SIZE);
        } catch (Exception e) {
            log.error("view_submission inbox worker 실행에 실패했습니다.", e);
        }
    }

    private boolean isBlockActionWorkerDisabled() {
        return !interactivityWorkerProperties.inbox()
                                             .blockActions()
                                             .workerEnabled();
    }

    private boolean isViewSubmissionWorkerDisabled() {
        return !interactivityWorkerProperties.inbox()
                                             .viewSubmission()
                                             .workerEnabled();
    }
}
