package com.slack.bot.global.config;

import com.slack.bot.application.interaction.box.in.SlackBlockActionInboxWorker;
import com.slack.bot.application.interaction.box.in.SlackInteractionInboxProcessor;
import com.slack.bot.application.interaction.box.in.SlackViewSubmissionInboxWorker;
import com.slack.bot.application.review.box.in.ReviewRequestInboxProcessor;
import com.slack.bot.application.review.box.in.ReviewRequestInboxWorker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InboxWorkerConfig {

    @Bean
    @ConditionalOnProperty(
            prefix = "app.interaction.inbox.block-actions",
            name = "worker-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public SlackBlockActionInboxWorker slackBlockActionInboxWorker(
            SlackInteractionInboxProcessor slackInteractionInboxProcessor
    ) {
        return new SlackBlockActionInboxWorker(slackInteractionInboxProcessor);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.interaction.inbox.view-submission",
            name = "worker-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public SlackViewSubmissionInboxWorker slackViewSubmissionInboxWorker(
            SlackInteractionInboxProcessor slackInteractionInboxProcessor
    ) {
        return new SlackViewSubmissionInboxWorker(slackInteractionInboxProcessor);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "review.notification.inbox",
            name = "worker-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public ReviewRequestInboxWorker reviewRequestInboxWorker(
            ReviewRequestInboxProcessor reviewRequestInboxProcessor
    ) {
        return new ReviewRequestInboxWorker(reviewRequestInboxProcessor);
    }
}
