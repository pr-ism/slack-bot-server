package com.slack.bot.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.interaction.box.retry.EqualJitterExponentialBackOffPolicy;
import com.slack.bot.application.interaction.box.retry.InteractionRetryExceptionClassifier;
import com.slack.bot.global.config.properties.InteractionRetryProperties;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RetryConfigTest {

    private final RetryConfig retryConfig = new RetryConfig();

    @Test
    void slack_interaction_inbox_retry_template은_equal_jitter_backoff를_사용한다() {
        // given
        InteractionRetryProperties retryProperties = createRetryProperties();
        InteractionRetryExceptionClassifier classifier = InteractionRetryExceptionClassifier.create();

        // when
        RetryTemplate retryTemplate = retryConfig.slackInteractionInboxRetryTemplate(retryProperties, classifier);

        // then
        EqualJitterExponentialBackOffPolicy backOffPolicy = extractBackOffPolicy(retryTemplate);
        assertAll(
                () -> assertThat(backOffPolicy.getInitialInterval()).isEqualTo(100L),
                () -> assertThat(backOffPolicy.getMultiplier()).isEqualTo(2.0d),
                () -> assertThat(backOffPolicy.getMaxInterval()).isEqualTo(1_000L)
        );
    }

    @Test
    void slack_notification_outbox_retry_template은_equal_jitter_backoff를_사용한다() {
        // given
        InteractionRetryProperties retryProperties = createRetryProperties();
        InteractionRetryExceptionClassifier classifier = InteractionRetryExceptionClassifier.create();

        // when
        RetryTemplate retryTemplate = retryConfig.slackNotificationOutboxRetryTemplate(retryProperties, classifier);

        // then
        EqualJitterExponentialBackOffPolicy backOffPolicy = extractBackOffPolicy(retryTemplate);
        assertAll(
                () -> assertThat(backOffPolicy.getInitialInterval()).isEqualTo(200L),
                () -> assertThat(backOffPolicy.getMultiplier()).isEqualTo(3.0d),
                () -> assertThat(backOffPolicy.getMaxInterval()).isEqualTo(2_000L)
        );
    }

    private InteractionRetryProperties createRetryProperties() {
        return new InteractionRetryProperties(
                new InteractionRetryProperties.InboxRetryProperties(3, 100L, 2.0d, 1_000L),
                new InteractionRetryProperties.OutboxRetryProperties(4, 200L, 3.0d, 2_000L)
        );
    }

    private EqualJitterExponentialBackOffPolicy extractBackOffPolicy(RetryTemplate retryTemplate) {
        Object backOffPolicy = ReflectionTestUtils.getField(retryTemplate, "backOffPolicy");
        assertThat(backOffPolicy).isInstanceOf(EqualJitterExponentialBackOffPolicy.class);
        return (EqualJitterExponentialBackOffPolicy) backOffPolicy;
    }
}
