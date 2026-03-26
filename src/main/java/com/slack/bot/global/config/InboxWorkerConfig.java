package com.slack.bot.global.config;

import com.slack.bot.application.interaction.box.in.SlackBlockActionInboxWorker;
import com.slack.bot.application.interaction.box.in.SlackBlockActionInboxTimeoutRecoveryWorker;
import com.slack.bot.application.interaction.box.in.SlackInteractionInboxProcessor;
import com.slack.bot.application.interaction.box.in.SlackViewSubmissionInboxWorker;
import com.slack.bot.application.interaction.box.in.SlackViewSubmissionInboxTimeoutRecoveryWorker;
import com.slack.bot.application.review.box.in.ReviewRequestInboxProcessor;
import com.slack.bot.application.review.box.in.ReviewRequestInboxWorker;
import com.slack.bot.application.review.box.in.ReviewRequestInboxTimeoutRecoveryWorker;
import com.slack.bot.global.config.properties.InteractionWorkerProperties;
import com.slack.bot.global.config.properties.ReviewWorkerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InboxWorkerConfig {

    @Bean
    public SlackBlockActionInboxWorker slackBlockActionInboxWorker(
            SlackInteractionInboxProcessor slackInteractionInboxProcessor,
            InteractionWorkerProperties interactionWorkerProperties
    ) {
        return new SlackBlockActionInboxWorker(
                slackInteractionInboxProcessor,
                interactionWorkerProperties.inbox().blockActions().pollDelayMs(),
                interactionWorkerProperties.inbox().blockActions().pollCapMs()
        );
    }

    @Bean
    public SlackBlockActionInboxTimeoutRecoveryWorker slackBlockActionInboxTimeoutRecoveryWorker(
            SlackInteractionInboxProcessor slackInteractionInboxProcessor
    ) {
        return new SlackBlockActionInboxTimeoutRecoveryWorker(slackInteractionInboxProcessor);
    }

    @Bean
    public SlackViewSubmissionInboxWorker slackViewSubmissionInboxWorker(
            SlackInteractionInboxProcessor slackInteractionInboxProcessor,
            InteractionWorkerProperties interactionWorkerProperties
    ) {
        return new SlackViewSubmissionInboxWorker(
                slackInteractionInboxProcessor,
                interactionWorkerProperties.inbox().viewSubmission().pollDelayMs(),
                interactionWorkerProperties.inbox().viewSubmission().pollCapMs()
        );
    }

    @Bean
    public SlackViewSubmissionInboxTimeoutRecoveryWorker slackViewSubmissionInboxTimeoutRecoveryWorker(
            SlackInteractionInboxProcessor slackInteractionInboxProcessor
    ) {
        return new SlackViewSubmissionInboxTimeoutRecoveryWorker(slackInteractionInboxProcessor);
    }

    @Bean
    public ReviewRequestInboxWorker reviewRequestInboxWorker(
            ReviewRequestInboxProcessor reviewRequestInboxProcessor,
            ReviewWorkerProperties reviewWorkerProperties
    ) {
        return new ReviewRequestInboxWorker(
                reviewRequestInboxProcessor,
                reviewWorkerProperties.inbox().batchSize(),
                reviewWorkerProperties.inbox().pollDelayMs(),
                reviewWorkerProperties.inbox().pollCapMs()
        );
    }

    @Bean
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
