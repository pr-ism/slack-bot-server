package com.slack.bot.application.review;

import com.slack.bot.application.review.dto.request.ReviewRequestEventRequest;

public interface ReviewEventBatch {

    void buffer(String apiKey, ReviewRequestEventRequest request);
}
