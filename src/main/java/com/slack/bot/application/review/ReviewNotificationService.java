package com.slack.bot.application.review;

import com.slack.bot.application.review.channel.ReviewSlackChannelResolver;
import com.slack.bot.application.review.channel.dto.SlackChannelDto;
import com.slack.bot.application.review.client.ReviewSlackApiClient;
import com.slack.bot.application.review.dto.ReviewMessageDto;
import com.slack.bot.application.review.dto.request.ReviewAssignmentRequest;
import com.slack.bot.application.review.meta.ReviewActionMetaBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReviewNotificationService {

    private final ReviewBlockCreator blockFactory;
    private final ReviewSlackApiClient slackApiClient;
    private final ReviewActionMetaBuilder actionMetaBuilder;
    private final ReviewSlackChannelResolver channelResolver;

    public void sendSimpleNotification(String apiKey, ReviewAssignmentRequest request) {
        SlackChannelDto channel = channelResolver.resolve(apiKey);
        String actionMeta = actionMetaBuilder.build(
                channel.teamId(),
                channel.channelId(),
                apiKey,
                request
        );
        ReviewMessageDto message = blockFactory.create(channel.teamId(), request, actionMeta);

        slackApiClient.sendBlockMessage(
                channel.accessToken(),
                channel.channelId(),
                message.blocks(),
                message.attachments(),
                message.fallbackText()
        );
    }
}
