package com.slack.bot.application.interaction.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.interaction.box.ProcessingSourceContext;
import com.slack.bot.application.interaction.box.aop.EnqueueViewSubmissionInInbox;
import com.slack.bot.application.interaction.view.dto.ViewSubmissionImmediateDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ViewSubmissionInteractionCoordinator {

    private final ViewSubmissionRouter viewSubmissionRouter;
    private final ProcessingSourceContext processingSourceContext;
    private final ViewSubmissionImmediateResolver viewSubmissionImmediateResolver;

    @EnqueueViewSubmissionInInbox
    public ViewSubmissionImmediateDto handle(JsonNode payload) {
        return viewSubmissionImmediateResolver.resolve(payload);
    }

    public void handleEnqueued(JsonNode payload) {
        processingSourceContext.withInboxProcessing(() -> viewSubmissionRouter.handle(payload));
    }
}
