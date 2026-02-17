package com.slack.bot.application.interactivity.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.interactivity.box.aop.EnqueueViewSubmissionInInbox;
import com.slack.bot.application.interactivity.view.dto.ViewSubmissionSyncResultDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ViewSubmissionInteractionService {

    private final ViewSubmissionRouter viewSubmissionRouter;
    private final ViewSubmissionSyncResponseResolver viewSubmissionSyncResponseResolver;

    @EnqueueViewSubmissionInInbox
    public ViewSubmissionSyncResultDto handle(JsonNode payload) {
        return viewSubmissionSyncResponseResolver.resolve(payload);
    }

    public void handleEnqueued(JsonNode payload) {
        viewSubmissionRouter.handle(payload);
    }
}
