package com.slack.bot.application.review;

import com.slack.bot.application.review.box.aop.BindReviewNotificationSourceKey;
import com.slack.bot.application.review.box.ReviewNotificationSourceContext;
import com.slack.bot.application.review.box.out.ReviewNotificationOutboxEnqueuer;
import com.slack.bot.application.review.channel.ReviewSlackChannelResolver;
import com.slack.bot.application.review.channel.dto.SlackChannelDto;
import com.slack.bot.application.review.dto.ReviewNotificationPayload;
import com.slack.bot.application.review.dto.ReviewMessageDto;
import com.slack.bot.application.review.meta.ReviewActionMetaBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReviewNotificationService {

    private final ReviewBlockCreator blockFactory;
    private final ReviewNotificationOutboxEnqueuer reviewNotificationOutboxEnqueuer;
    private final ReviewActionMetaBuilder actionMetaBuilder;
    private final ReviewSlackChannelResolver channelResolver;
    private final ReviewNotificationSourceContext reviewNotificationSourceContext;

    @BindReviewNotificationSourceKey
    public void sendSimpleNotification(String apiKey, ReviewNotificationPayload request) {
        SlackChannelDto channel = channelResolver.resolve(apiKey);
        String sourceKey = reviewNotificationSourceContext.requireSourceKey();
        String actionMeta = actionMetaBuilder.build(
                channel.teamId(),
                channel.channelId(),
                apiKey,
                request
        );
        ReviewMessageDto message = blockFactory.create(channel.teamId(), request, actionMeta);

        reviewNotificationOutboxEnqueuer.enqueueChannelBlocks(
                sourceKey,
                channel.teamId(),
                channel.channelId(),
                message.blocks(),
                message.attachments(),
                message.fallbackText()
        );
    }
}
