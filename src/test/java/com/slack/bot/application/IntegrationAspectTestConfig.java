package com.slack.bot.application;

import com.slack.bot.application.interactivity.box.ProcessingSourceContext;
import com.slack.bot.application.interactivity.box.aop.aspect.support.AspectIntegrationProbes;
import com.slack.bot.application.interactivity.box.out.OutboxIdempotencySourceContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class IntegrationAspectTestConfig {

    @Bean
    public AspectIntegrationProbes.BlockActionAspectProbe blockActionAspectProbe() {
        return new AspectIntegrationProbes.BlockActionAspectProbe();
    }

    @Bean
    public AspectIntegrationProbes.ViewSubmissionAspectProbe viewSubmissionAspectProbe() {
        return new AspectIntegrationProbes.ViewSubmissionAspectProbe();
    }

    @Bean
    public AspectIntegrationProbes.InboxToOutboxProbe inboxToOutboxProbe(
            ProcessingSourceContext processingSourceContext,
            OutboxIdempotencySourceContext outboxIdempotencySourceContext
    ) {
        return new AspectIntegrationProbes.InboxToOutboxProbe(
                processingSourceContext,
                outboxIdempotencySourceContext
        );
    }

    @Bean
    public AspectIntegrationProbes.OutboxSourceResolverProbe outboxSourceResolverProbe() {
        return new AspectIntegrationProbes.OutboxSourceResolverProbe();
    }
}
