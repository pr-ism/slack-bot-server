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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InboxWorkerConfig {

    @Bean
    public SlackBlockActionInboxWorker slackBlockActionInboxWorker(
            SlackInteractionInboxProcessor slackInteractionInboxProcessor,
            InteractionWorkerProperties interactionWorkerProperties,
            @Value("${app.adaptive-polling.auto-start:true}") boolean adaptivePollingAutoStart
    ) {
        return new SlackBlockActionInboxWorker(
                slackInteractionInboxProcessor,
                interactionWorkerProperties.inbox().blockActions().pollDelayMs(),
                interactionWorkerProperties.inbox().blockActions().pollCapMs(),
                adaptivePollingAutoStart
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
            InteractionWorkerProperties interactionWorkerProperties,
            @Value("${app.adaptive-polling.auto-start:true}") boolean adaptivePollingAutoStart
    ) {
        return new SlackViewSubmissionInboxWorker(
                slackInteractionInboxProcessor,
                interactionWorkerProperties.inbox().viewSubmission().pollDelayMs(),
                interactionWorkerProperties.inbox().viewSubmission().pollCapMs(),
                adaptivePollingAutoStart
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
            ReviewWorkerProperties reviewWorkerProperties,
            @Value("${app.adaptive-polling.auto-start:true}") boolean adaptivePollingAutoStart
    ) {
        return new ReviewRequestInboxWorker(
                reviewRequestInboxProcessor,
                reviewWorkerProperties.inbox().batchSize(),
                reviewWorkerProperties.inbox().pollDelayMs(),
                reviewWorkerProperties.inbox().pollCapMs(),
                adaptivePollingAutoStart
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
