package com.slack.bot.application.review.box.out;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.review.ReviewBlockCreator;
import com.slack.bot.application.review.dto.ReviewMessageDto;
import com.slack.bot.application.review.dto.ReviewNotificationPayload;
import com.slack.bot.application.review.meta.ReviewActionMetaBuilder;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewNotificationMessageRenderer {

    private final ObjectMapper objectMapper;
    private final ReviewBlockCreator reviewBlockCreator;
    private final ReviewActionMetaBuilder reviewActionMetaBuilder;

    public ReviewMessageDto render(ReviewNotificationOutbox outbox) throws JsonProcessingException {
        ReviewNotificationPayload payload = objectMapper.readValue(
                outbox.getPayloadJson(),
                ReviewNotificationPayload.class
        );
        String actionMeta = reviewActionMetaBuilder.build(
                outbox.getTeamId(),
                outbox.getChannelId(),
                outbox.getProjectId(),
                payload
        );

        return reviewBlockCreator.create(outbox.getTeamId(), payload, actionMeta);
    }
}
