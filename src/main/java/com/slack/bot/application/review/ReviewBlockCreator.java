package com.slack.bot.application.review;

import com.slack.bot.application.review.dto.ReviewMessageDto;
import com.slack.bot.application.review.dto.request.ReviewAssignmentRequest;

public interface ReviewBlockCreator {

    ReviewMessageDto create(String teamId, ReviewAssignmentRequest event, String actionMetaJson);
}
