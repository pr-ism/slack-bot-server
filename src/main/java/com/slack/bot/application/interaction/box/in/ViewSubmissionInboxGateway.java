package com.slack.bot.application.interaction.box.in;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.interaction.box.aop.EnqueueViewSubmissionInInbox;
import com.slack.bot.application.interaction.view.ViewSubmissionImmediateResolver;
import com.slack.bot.application.interaction.view.dto.ViewSubmissionImmediateDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ViewSubmissionInboxGateway {

    private final ViewSubmissionImmediateResolver viewSubmissionImmediateResolver;

    @EnqueueViewSubmissionInInbox
    public ViewSubmissionImmediateDto handle(JsonNode payload) {
        return viewSubmissionImmediateResolver.resolve(payload);
    }
}
