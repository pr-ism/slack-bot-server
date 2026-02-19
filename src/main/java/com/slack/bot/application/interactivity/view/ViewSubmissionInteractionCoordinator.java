package com.slack.bot.application.interactivity.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.interactivity.view.dto.ViewSubmissionImmediateDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ViewSubmissionInteractionCoordinator {

    private final ViewSubmissionRouter viewSubmissionRouter;
    private final ViewSubmissionImmediateResolver viewSubmissionImmediateResolver;

    public ViewSubmissionImmediateDto handle(JsonNode payload) {
        return viewSubmissionImmediateResolver.resolve(payload);
    }

    public void handleEnqueued(JsonNode payload) {
        viewSubmissionRouter.handle(payload);
    }
}
