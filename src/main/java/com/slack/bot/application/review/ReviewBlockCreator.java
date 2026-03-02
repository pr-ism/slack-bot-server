package com.slack.bot.application.review;

import com.slack.bot.application.review.dto.ReviewNotificationPayload;
import com.slack.bot.application.review.dto.ReviewMessageDto;

public interface ReviewBlockCreator {

    ReviewMessageDto create(String teamId, ReviewNotificationPayload event, String actionMetaJson);
}
