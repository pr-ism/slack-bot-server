package com.slack.bot.application.interactivity.box.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mockingDetails;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.box.InteractivityImmediateProcessor;
import com.slack.bot.application.interactivity.box.in.SlackInteractionInboxProcessor;
import com.slack.bot.application.interactivity.box.out.SlackNotificationOutboxWriter;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InteractivityImmediateProcessingTriggerAspectIntegrationTest {

    @Autowired
    SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    @Autowired
    SlackNotificationOutboxWriter slackNotificationOutboxWriter;

    @Autowired
    InteractivityImmediateProcessor interactivityImmediateProcessor;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void enqueue_block_action은_onlyWhenEnqueued일때_true에서만_즉시처리를_트리거한다() {
        // given
        String payloadJson = objectMapper.createObjectNode()
                                         .put("type", "block_actions")
                                         .put("nonce", "n-" + System.nanoTime())
                                         .toString();
        long before = triggerInvocationCount("triggerBlockActionInbox");

        // when
        boolean first = slackInteractionInboxProcessor.enqueueBlockAction(payloadJson);
        long afterFirst = triggerInvocationCount("triggerBlockActionInbox");
        boolean second = slackInteractionInboxProcessor.enqueueBlockAction(payloadJson);
        long afterSecond = triggerInvocationCount("triggerBlockActionInbox");

        // then
        assertAll(
                () -> assertThat(first).isTrue(),
                () -> assertThat(second).isFalse(),
                () -> assertThat(afterFirst).isEqualTo(before + 1),
                () -> assertThat(afterSecond).isEqualTo(afterFirst)
        );
    }

    @Test
    void outbox_writer_호출시_즉시처리_트리거_AOP가_동작한다() {
        // given
        long before = triggerInvocationCount("triggerOutbox");

        // when
        slackNotificationOutboxWriter.enqueueChannelText(
                "SRC-OUTBOX-" + System.nanoTime(),
                "T1",
                "C1",
                "hello"
        );
        long after = triggerInvocationCount("triggerOutbox");

        // then
        assertThat(after).isEqualTo(before + 1);
    }

    private long triggerInvocationCount(String methodName) {
        return mockingDetails(interactivityImmediateProcessor)
                .getInvocations()
                .stream()
                .filter(invocation -> invocation.getMethod().getName().equals(methodName))
                .count();
    }
}
