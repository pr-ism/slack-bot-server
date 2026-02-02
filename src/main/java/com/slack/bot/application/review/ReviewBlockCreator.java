package com.slack.bot.application.review;

import com.slack.bot.application.review.dto.ReviewMessageDto;
import com.slack.bot.application.review.dto.request.ReviewRequestEventRequest;

public interface ReviewBlockCreator {

    ReviewMessageDto create(String teamId, ReviewRequestEventRequest event, String actionMetaJson);
}
