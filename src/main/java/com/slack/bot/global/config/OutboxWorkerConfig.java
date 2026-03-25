package com.slack.bot.global.config;

import com.slack.bot.application.interaction.box.out.SlackNotificationOutboxProcessor;
import com.slack.bot.application.interaction.box.out.SlackNotificationOutboxTimeoutRecoveryWorker;
import com.slack.bot.application.interaction.box.out.SlackNotificationOutboxWorker;
import com.slack.bot.application.review.box.out.ReviewNotificationOutboxProcessor;
import com.slack.bot.application.review.box.out.ReviewNotificationOutboxTimeoutRecoveryWorker;
import com.slack.bot.application.review.box.out.ReviewNotificationOutboxWorker;
import com.slack.bot.global.config.properties.InteractionWorkerProperties;
import com.slack.bot.global.config.properties.ReviewWorkerProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxWorkerConfig {

    @Bean
    public SlackNotificationOutboxWorker slackNotificationOutboxWorker(
            SlackNotificationOutboxProcessor slackNotificationOutboxProcessor,
            InteractionWorkerProperties interactionWorkerProperties,
            @Value("${app.adaptive-polling.auto-start:true}") boolean adaptivePollingAutoStart
    ) {
        return new SlackNotificationOutboxWorker(
                slackNotificationOutboxProcessor,
                interactionWorkerProperties.outbox().pollDelayMs(),
                interactionWorkerProperties.outbox().pollCapMs(),
                adaptivePollingAutoStart
        );
    }

    @Bean
    public SlackNotificationOutboxTimeoutRecoveryWorker slackNotificationOutboxTimeoutRecoveryWorker(
            SlackNotificationOutboxProcessor slackNotificationOutboxProcessor
    ) {
        return new SlackNotificationOutboxTimeoutRecoveryWorker(slackNotificationOutboxProcessor);
    }

    @Bean
    public ReviewNotificationOutboxWorker reviewNotificationOutboxWorker(
            ReviewNotificationOutboxProcessor reviewNotificationOutboxProcessor,
            ReviewWorkerProperties reviewWorkerProperties,
            @Value("${app.adaptive-polling.auto-start:true}") boolean adaptivePollingAutoStart
    ) {
        return new ReviewNotificationOutboxWorker(
                reviewNotificationOutboxProcessor,
                reviewWorkerProperties.outbox().batchSize(),
                reviewWorkerProperties.outbox().pollDelayMs(),
                reviewWorkerProperties.outbox().pollCapMs(),
                adaptivePollingAutoStart
        );
    }

    @Bean
    public ReviewNotificationOutboxTimeoutRecoveryWorker reviewNotificationOutboxTimeoutRecoveryWorker(
            ReviewNotificationOutboxProcessor reviewNotificationOutboxProcessor,
            ReviewWorkerProperties reviewWorkerProperties
    ) {
        return new ReviewNotificationOutboxTimeoutRecoveryWorker(
                reviewNotificationOutboxProcessor,
                reviewWorkerProperties.outbox().processingTimeoutMs()
        );
    }
}
