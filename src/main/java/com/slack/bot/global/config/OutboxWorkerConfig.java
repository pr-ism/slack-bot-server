package com.slack.bot.global.config;

import com.slack.bot.application.interaction.box.out.SlackNotificationOutboxProcessor;
import com.slack.bot.application.interaction.box.out.SlackNotificationOutboxTimeoutRecoveryWorker;
import com.slack.bot.application.interaction.box.out.SlackNotificationOutboxWorker;
import com.slack.bot.application.review.box.out.ReviewNotificationOutboxProcessor;
import com.slack.bot.application.review.box.out.ReviewNotificationOutboxTimeoutRecoveryWorker;
import com.slack.bot.application.review.box.out.ReviewNotificationOutboxWorker;
import com.slack.bot.global.config.properties.ReviewWorkerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxWorkerConfig {

    @Bean
    @ConditionalOnProperty(
            prefix = "app.interaction.outbox",
            name = "worker-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public SlackNotificationOutboxWorker slackNotificationOutboxWorker(
            SlackNotificationOutboxProcessor slackNotificationOutboxProcessor
    ) {
        return new SlackNotificationOutboxWorker(slackNotificationOutboxProcessor);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.interaction.outbox",
            name = "worker-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public SlackNotificationOutboxTimeoutRecoveryWorker slackNotificationOutboxTimeoutRecoveryWorker(
            SlackNotificationOutboxProcessor slackNotificationOutboxProcessor
    ) {
        return new SlackNotificationOutboxTimeoutRecoveryWorker(slackNotificationOutboxProcessor);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "review.notification.outbox",
            name = "worker-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public ReviewNotificationOutboxWorker reviewNotificationOutboxWorker(
            ReviewNotificationOutboxProcessor reviewNotificationOutboxProcessor,
            ReviewWorkerProperties reviewWorkerProperties
    ) {
        return new ReviewNotificationOutboxWorker(
                reviewNotificationOutboxProcessor,
                reviewWorkerProperties.outbox().batchSize()
        );
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "review.notification.outbox",
            name = "worker-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
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
