package com.slack.bot.application.interactivity.box.aop.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interactivity.box.aop.EnqueueViewSubmissionInInbox;
import com.slack.bot.application.interactivity.box.aop.exception.ViewSubmissionAopProceedException;
import com.slack.bot.application.interactivity.box.ProcessingSourceContext;
import com.slack.bot.application.interactivity.box.in.SlackInteractionInboxProcessor;
import com.slack.bot.application.interactivity.reply.dto.response.SlackActionResponse;
import com.slack.bot.application.interactivity.view.dto.ViewSubmissionSyncResultDto;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
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

    ProcessingSourceContext processingSourceContext;
    ViewSubmissionInboxEnqueueAspect viewSubmissionInboxEnqueueAspect;
    ViewSubmissionAopTestTarget proxyTarget;
    JsonNode payload;

    @BeforeEach
    void setUp() {
        processingSourceContext = new ProcessingSourceContext();
        viewSubmissionInboxEnqueueAspect = new ViewSubmissionInboxEnqueueAspect(
                slackInteractionInboxProcessor,
                processingSourceContext
        );
        proxyTarget = createProxyTarget(new ViewSubmissionAopTestTarget());
        payload = new ObjectMapper().createObjectNode().put("type", "view_submission");
    }

    @Test
    void ViewSubmissionSyncResultDto를_정상_반환하면_enqueueViewSubmission이_호출된다() {
        // when
        ViewSubmissionSyncResultDto actual = proxyTarget.happyPath(payload);

        // then
        assertThat(actual.shouldEnqueue()).isTrue();
        verify(slackInteractionInboxProcessor).enqueueViewSubmission(payload.toString());
    }

    @Test
    void enqueue_중_runtime_예외가_발생해도_sync_response는_반환된다() {
        // given
        given(slackInteractionInboxProcessor.enqueueViewSubmission(payload.toString()))
                .willThrow(new IllegalStateException("enqueue-failure"));

        // when
        ViewSubmissionSyncResultDto actual = proxyTarget.happyPath(payload);

        // then
        assertThat(actual.shouldEnqueue()).isTrue();
        verify(slackInteractionInboxProcessor).enqueueViewSubmission(payload.toString());
    }

    @Test
    void 인박스_컨텍스트에서는_원본을_수행하고_enqueue하지_않는다() {
        // given
        AtomicReference<ViewSubmissionSyncResultDto> resultRef = new AtomicReference<>();

        // when
        processingSourceContext.withInboxProcessing(() -> resultRef.set(proxyTarget.happyPath(payload)));

        // then
        ViewSubmissionSyncResultDto actual = resultRef.get();
        assertThat(actual.shouldEnqueue()).isTrue();
        verify(slackInteractionInboxProcessor, never()).enqueueViewSubmission(anyString());
    }

    @Test
    void checked_예외는_custom_exception으로_래핑된다() {
        // when & then
        assertThatThrownBy(() -> proxyTarget.throwChecked(payload))
                .isInstanceOf(ViewSubmissionAopProceedException.class)
                .hasMessage("view submission AOP proceed 실패")
                .hasCauseInstanceOf(IOException.class);

        verify(slackInteractionInboxProcessor, never()).enqueueViewSubmission(anyString());
    }

    @Test
    void runtime_예외는_그대로_전파된다() {
        // when & then
        assertThatThrownBy(() -> proxyTarget.throwRuntime(payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("runtime-failure");

        verify(slackInteractionInboxProcessor, never()).enqueueViewSubmission(anyString());
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
        public ViewSubmissionSyncResultDto happyPath(JsonNode payload) {
            return ViewSubmissionSyncResultDto.enqueue(SlackActionResponse.clear());
        }

        @EnqueueViewSubmissionInInbox
        public ViewSubmissionSyncResultDto throwChecked(JsonNode payload) {
            throwCheckedIOException();
            return ViewSubmissionSyncResultDto.noEnqueue(SlackActionResponse.empty());
        }

        @EnqueueViewSubmissionInInbox
        public ViewSubmissionSyncResultDto throwRuntime(JsonNode payload) {
            throw new IllegalStateException("runtime-failure");
        }

        @EnqueueViewSubmissionInInbox
        public String invalidReturn(JsonNode payload) {
            return "invalid-return";
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
