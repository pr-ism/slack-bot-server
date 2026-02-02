package com.slack.bot.infrastructure.review.batch;

import com.slack.bot.application.review.ReviewBlockCreator;
import com.slack.bot.application.review.ReviewNotificationService;
import com.slack.bot.application.review.channel.ReviewSlackChannelResolver;
import com.slack.bot.application.review.client.ReviewSlackApiClient;
import com.slack.bot.application.review.dto.request.ReviewAssignmentRequest;
import com.slack.bot.application.review.meta.ReviewActionMetaBuilder;
import java.util.concurrent.atomic.AtomicInteger;

public class SpyReviewNotificationService extends ReviewNotificationService {

    private final AtomicInteger sendCount = new AtomicInteger(0);

    public SpyReviewNotificationService(
            ReviewBlockCreator blockFactory,
            ReviewSlackApiClient slackApiClient,
            ReviewActionMetaBuilder actionMetaBuilder,
            ReviewSlackChannelResolver channelResolver
    ) {
        super(blockFactory, slackApiClient, actionMetaBuilder, channelResolver);
    }

    @Override
    public void sendSimpleNotification(String apiKey, ReviewAssignmentRequest request) {
        super.sendSimpleNotification(apiKey, request);
        sendCount.incrementAndGet();
    }

    public int getSendCount() {
        return sendCount.get();
    }

    public void resetCount() {
        sendCount.set(0);
    }
}
