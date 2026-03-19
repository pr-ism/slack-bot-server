package com.slack.bot.infrastructure.review.batch;

import com.slack.bot.application.review.ReviewBlockCreator;
import com.slack.bot.application.review.ReviewNotificationService;
import com.slack.bot.application.review.box.aop.BindReviewNotificationSourceKey;
import com.slack.bot.application.review.box.ReviewNotificationSourceContext;
import com.slack.bot.application.review.box.out.ReviewNotificationOutboxEnqueuer;
import com.slack.bot.application.review.channel.ReviewSlackChannelResolver;
import com.slack.bot.application.review.dto.ReviewNotificationPayload;
import com.slack.bot.application.review.meta.ReviewActionMetaBuilder;
import java.util.concurrent.atomic.AtomicInteger;

public class SpyReviewNotificationService extends ReviewNotificationService {

    private final AtomicInteger sendCount = new AtomicInteger(0);

    public SpyReviewNotificationService(
            ReviewBlockCreator blockFactory,
            ReviewNotificationOutboxEnqueuer reviewNotificationOutboxEnqueuer,
            ReviewActionMetaBuilder actionMetaBuilder,
            ReviewSlackChannelResolver channelResolver,
            ReviewNotificationSourceContext reviewNotificationSourceContext
    ) {
        super(
                blockFactory,
                reviewNotificationOutboxEnqueuer,
                actionMetaBuilder,
                channelResolver,
                reviewNotificationSourceContext
        );
    }

    @Override
    @BindReviewNotificationSourceKey
    public void sendSimpleNotification(String apiKey, ReviewNotificationPayload request) {
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
