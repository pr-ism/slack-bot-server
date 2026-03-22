package com.slack.bot.global.config;

import com.slack.bot.application.interaction.box.in.SlackBlockActionInboxWorker;
import com.slack.bot.application.interaction.box.in.SlackBlockActionInboxTimeoutRecoveryWorker;
import com.slack.bot.application.interaction.box.in.SlackInteractionInboxProcessor;
import com.slack.bot.application.interaction.box.in.SlackViewSubmissionInboxWorker;
import com.slack.bot.application.interaction.box.in.SlackViewSubmissionInboxTimeoutRecoveryWorker;
import com.slack.bot.application.review.box.in.ReviewRequestInboxProcessor;
import com.slack.bot.application.review.box.in.ReviewRequestInboxWorker;
import com.slack.bot.application.review.box.in.ReviewRequestInboxTimeoutRecoveryWorker;
import com.slack.bot.global.config.properties.ReviewWorkerProperties;
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
            prefix = "app.interaction.inbox.block-actions",
            name = "worker-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public SlackBlockActionInboxTimeoutRecoveryWorker slackBlockActionInboxTimeoutRecoveryWorker(
            SlackInteractionInboxProcessor slackInteractionInboxProcessor
    ) {
        return new SlackBlockActionInboxTimeoutRecoveryWorker(slackInteractionInboxProcessor);
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
            prefix = "app.interaction.inbox.view-submission",
            name = "worker-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public SlackViewSubmissionInboxTimeoutRecoveryWorker slackViewSubmissionInboxTimeoutRecoveryWorker(
            SlackInteractionInboxProcessor slackInteractionInboxProcessor
    ) {
        return new SlackViewSubmissionInboxTimeoutRecoveryWorker(slackInteractionInboxProcessor);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "review.notification.inbox",
            name = "worker-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public ReviewRequestInboxWorker reviewRequestInboxWorker(
            ReviewRequestInboxProcessor reviewRequestInboxProcessor,
            ReviewWorkerProperties reviewWorkerProperties
    ) {
        return new ReviewRequestInboxWorker(
                reviewRequestInboxProcessor,
                reviewWorkerProperties.inbox().batchSize()
        );
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "review.notification.inbox",
            name = "worker-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public ReviewRequestInboxTimeoutRecoveryWorker reviewRequestInboxTimeoutRecoveryWorker(
            ReviewRequestInboxProcessor reviewRequestInboxProcessor,
            ReviewWorkerProperties reviewWorkerProperties
    ) {
        return new ReviewRequestInboxTimeoutRecoveryWorker(
                reviewRequestInboxProcessor,
                reviewWorkerProperties.inbox().processingTimeoutMs()
        );
    }
}
