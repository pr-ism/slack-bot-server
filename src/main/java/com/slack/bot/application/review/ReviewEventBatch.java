package com.slack.bot.application.review;

import com.slack.bot.application.review.dto.request.ReviewAssignmentRequest;

public interface ReviewEventBatch {

    void buffer(String apiKey, ReviewAssignmentRequest request);
}
