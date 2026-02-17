package com.slack.bot.application.interactivity.box.aop.aspect;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interactivity.box.aop.EnqueueViewSubmissionInInbox;
import com.slack.bot.application.interactivity.box.in.SlackInteractionInboxProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ViewSubmissionInboxEnqueueAspectTest {

    @Mock
    SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    ViewSubmissionInboxEnqueueAspect viewSubmissionInboxEnqueueAspect;
    ViewSubmissionAopTestTarget proxyTarget;
    JsonNode payload;

    @BeforeEach
    void setUp() {
        viewSubmissionInboxEnqueueAspect = new ViewSubmissionInboxEnqueueAspect(slackInteractionInboxProcessor);
        proxyTarget = createProxyTarget(new ViewSubmissionAopTestTarget());
        payload = new ObjectMapper().createObjectNode().put("type", "view_submission");
    }

    @Test
    void 반환타입이_규약과_다르면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> proxyTarget.invalidReturn(payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("view_submission enqueue AOP 대상 메서드는 ViewSubmissionSyncResultDto를 반환해야 합니다.");

        verify(slackInteractionInboxProcessor, never()).enqueueViewSubmission(payload.toString());
    }

    private ViewSubmissionAopTestTarget createProxyTarget(ViewSubmissionAopTestTarget target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(viewSubmissionInboxEnqueueAspect);
        return factory.getProxy();
    }

    static class ViewSubmissionAopTestTarget {

        @EnqueueViewSubmissionInInbox
        public String invalidReturn(JsonNode payload) {
            return "invalid-return";
        }
    }
}
