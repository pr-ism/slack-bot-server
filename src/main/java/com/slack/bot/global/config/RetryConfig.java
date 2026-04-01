package com.slack.bot.global.config;

import com.slack.bot.application.interaction.box.retry.EqualJitterExponentialBackOffPolicy;
import com.slack.bot.application.interaction.box.retry.InteractionRetryExceptionClassifier;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
import com.slack.bot.global.config.properties.InteractionWorkerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
@EnableConfigurationProperties({InteractionRetryProperties.class, InteractionWorkerProperties.class})
public class RetryConfig {

    @Bean
    public InteractionRetryExceptionClassifier interactionRetryExceptionClassifier() {
        return InteractionRetryExceptionClassifier.create();
    }

    @Bean
    public RetryTemplate slackInteractionInboxRetryTemplate(
            InteractionRetryProperties interactionRetryProperties,
            InteractionRetryExceptionClassifier retryExceptionClassifier
    ) {
        InteractionRetryProperties.InboxRetryProperties retry = interactionRetryProperties.inbox();
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(
                retry.maxAttempts(),
                retryExceptionClassifier.getRetryableExceptions(),
                true,
                false
        ));

        EqualJitterExponentialBackOffPolicy backOffPolicy = new EqualJitterExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(retry.initialDelayMs());
        backOffPolicy.setMultiplier(retry.multiplier());
        backOffPolicy.setMaxInterval(retry.maxDelayMs());
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }

    @Bean
    public RetryTemplate slackNotificationOutboxRetryTemplate(
            InteractionRetryProperties interactionRetryProperties,
            InteractionRetryExceptionClassifier retryExceptionClassifier
    ) {
        InteractionRetryProperties.OutboxRetryProperties retry = interactionRetryProperties.outbox();
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(
                retry.maxAttempts(),
                retryExceptionClassifier.getRetryableExceptions(),
                true,
                false
        ));

        EqualJitterExponentialBackOffPolicy backOffPolicy = new EqualJitterExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(retry.initialDelayMs());
        backOffPolicy.setMultiplier(retry.multiplier());
        backOffPolicy.setMaxInterval(retry.maxDelayMs());
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }
}
