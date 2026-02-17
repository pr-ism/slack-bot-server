package com.slack.bot.application.interactivity.box.aop.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interactivity.box.aop.EnqueueBlockActionInInbox;
import com.slack.bot.application.interactivity.box.aop.exception.BlockActionAopProceedException;
import com.slack.bot.application.interactivity.box.ProcessingSourceContext;
import com.slack.bot.application.interactivity.box.in.SlackInteractionInboxProcessor;
import java.io.IOException;
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
class BlockActionInboxEnqueueAspectTest {

    @Mock
    SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    ProcessingSourceContext processingSourceContext;
    BlockActionInboxEnqueueAspect blockActionInboxEnqueueAspect;
    BlockActionAopTestTarget proxyTarget;
    JsonNode payload;

    @BeforeEach
    void setUp() {
        processingSourceContext = new ProcessingSourceContext();
        blockActionInboxEnqueueAspect = new BlockActionInboxEnqueueAspect(
                slackInteractionInboxProcessor,
                processingSourceContext
        );
        proxyTarget = createProxyTarget(new BlockActionAopTestTarget());
        payload = new ObjectMapper().createObjectNode().put("type", "block_actions");
    }

    @Test
    void 인박스_컨텍스트가_없으면_원본_메서드_본문을_실행하지않고_enqueue한다() {
        // when
        proxyTarget.noProceed(payload);

        // then
        assertThat(proxyTarget.noProceedInvocationCount()).isZero();
        verify(slackInteractionInboxProcessor).enqueueBlockAction(payload.toString());
    }

    @Test
    void 인박스_컨텍스트에서는_원본_메서드가_실행된다() {
        // when
        processingSourceContext.withInboxProcessing(() -> proxyTarget.noProceed(payload));

        // then
        assertThat(proxyTarget.noProceedInvocationCount()).isOne();
        verify(slackInteractionInboxProcessor, never()).enqueueBlockAction(anyString());
    }

    @Test
    void 인박스_컨텍스트에서_checked_예외는_custom_exception으로_래핑된다() {
        // when & then
        assertThatThrownBy(() -> processingSourceContext.withInboxProcessing(() -> proxyTarget.throwChecked(payload)))
                .isInstanceOf(BlockActionAopProceedException.class)
                .hasMessage("block action enqueue AOP proceed 실패.")
                .hasCauseInstanceOf(IOException.class);

        verify(slackInteractionInboxProcessor, never()).enqueueBlockAction(anyString());
    }

    @Test
    void 인박스_컨텍스트에서_runtime_예외는_그대로_전파된다() {
        // when & then
        assertThatThrownBy(() -> processingSourceContext.withInboxProcessing(() -> proxyTarget.throwRuntime(payload)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("runtime-failure");

        verify(slackInteractionInboxProcessor, never()).enqueueBlockAction(anyString());
    }

    private BlockActionAopTestTarget createProxyTarget(BlockActionAopTestTarget target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(blockActionInboxEnqueueAspect);
        return factory.getProxy();
    }

    static class BlockActionAopTestTarget {

        private int noProceedInvocationCount = 0;

        @EnqueueBlockActionInInbox
        public void noProceed(JsonNode payload) {
            noProceedInvocationCount += 1;
        }

        @EnqueueBlockActionInInbox
        public void throwChecked(JsonNode payload) {
            throwCheckedIOException();
        }

        @EnqueueBlockActionInInbox
        public void throwRuntime(JsonNode payload) {
            throw new IllegalStateException("runtime-failure");
        }

        public int noProceedInvocationCount() {
            return noProceedInvocationCount;
        }

        private void throwCheckedIOException() {
            sneakyThrow(new IOException("checked-failure"));
        }

        @SuppressWarnings("unchecked")
        private static <E extends Throwable> void sneakyThrow(Throwable throwable) throws E {
            throw (E) throwable;
        }
    }
}
