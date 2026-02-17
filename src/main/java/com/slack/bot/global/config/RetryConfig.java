package com.slack.bot.global.config;

import com.slack.bot.application.interactivity.box.retry.InteractivityRetryExceptionClassifier;
import com.slack.bot.global.config.properties.InteractivityRetryProperties;
import com.slack.bot.global.config.properties.InteractivityWorkerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
@EnableConfigurationProperties({InteractivityRetryProperties.class, InteractivityWorkerProperties.class})
public class RetryConfig {

    @Bean
    public InteractivityRetryExceptionClassifier interactivityRetryExceptionClassifier() {
        return InteractivityRetryExceptionClassifier.create();
    }

    @Bean
    public RetryTemplate slackInteractionInboxRetryTemplate(
            InteractivityRetryProperties interactivityRetryProperties,
            InteractivityRetryExceptionClassifier retryExceptionClassifier
    ) {
        InteractivityRetryProperties.Retry retry = interactivityRetryProperties.inbox();
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(
                retry.maxAttempts(),
                retryExceptionClassifier.getRetryableExceptions(),
                true,
                false
        ));

        ExponentialRandomBackOffPolicy backOffPolicy = new ExponentialRandomBackOffPolicy();
        backOffPolicy.setInitialInterval(retry.initialDelayMs());
        backOffPolicy.setMultiplier(retry.multiplier());
        backOffPolicy.setMaxInterval(retry.maxDelayMs());
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }

    @Bean
    public RetryTemplate slackNotificationOutboxRetryTemplate(
            InteractivityRetryProperties interactivityRetryProperties,
            InteractivityRetryExceptionClassifier retryExceptionClassifier
    ) {
        InteractivityRetryProperties.Retry retry = interactivityRetryProperties.outbox();
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(
                retry.maxAttempts(),
                retryExceptionClassifier.getRetryableExceptions(),
                true,
                false
        ));

        ExponentialRandomBackOffPolicy backOffPolicy = new ExponentialRandomBackOffPolicy();
        backOffPolicy.setInitialInterval(retry.initialDelayMs());
        backOffPolicy.setMultiplier(retry.multiplier());
        backOffPolicy.setMaxInterval(retry.maxDelayMs());
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }
}
