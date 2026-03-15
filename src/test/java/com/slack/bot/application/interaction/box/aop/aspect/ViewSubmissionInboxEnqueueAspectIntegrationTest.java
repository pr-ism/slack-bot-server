package com.slack.bot.application.interaction.box.aop.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interaction.box.ProcessingSourceContext;
import com.slack.bot.application.interaction.box.aop.aspect.exception.ViewSubmissionAopProceedException;
import com.slack.bot.application.interaction.box.aop.aspect.support.AspectIntegrationProbes.ViewSubmissionAspectProbe;
import com.slack.bot.application.interaction.box.aop.aspect.support.AspectIntegrationProbes.ViewSubmissionProbeMode;
import com.slack.bot.application.interaction.box.in.SlackInteractionInboxProcessor;
import com.slack.bot.application.interaction.reply.dto.response.SlackActionResponse;
import com.slack.bot.application.interaction.view.dto.ViewSubmissionImmediateDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ViewSubmissionInboxEnqueueAspectIntegrationTest {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ProcessingSourceContext processingSourceContext;

    @Autowired
    SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    @Autowired
    ViewSubmissionAspectProbe viewSubmissionAspectProbe;

    @BeforeEach
    void setUp() {
        viewSubmissionAspectProbe.reset();
    }

    @Test
    void inbox_외부_실행이고_shouldEnqueue_false면_적재하지_않고_즉시결과를_반환한다() {
        // given
        JsonNode payload = viewSubmissionPayload();

        // when
        ViewSubmissionImmediateDto actual = viewSubmissionAspectProbe.handle(payload, ViewSubmissionProbeMode.RETURN_NO_ENQUEUE);

        // then
        assertAll(
                () -> assertThat(actual.shouldEnqueue()).isFalse(),
                () -> assertThat(actual.response()).isEqualTo(SlackActionResponse.empty()),
                () -> assertThat(viewSubmissionAspectProbe.proceedCount()).isEqualTo(1)
        );
        verify(slackInteractionInboxProcessor, never()).enqueueViewSubmission(anyString());
    }

    @Test
    void inbox_외부_실행이고_shouldEnqueue_true면_view_submission을_inbox에_적재한다() {
        // given
        JsonNode payload = viewSubmissionPayload();
        doReturn(true)
                .when(slackInteractionInboxProcessor)
                .enqueueViewSubmission(payload.toString());

        // when
        ViewSubmissionImmediateDto actual = viewSubmissionAspectProbe.handle(payload, ViewSubmissionProbeMode.RETURN_ENQUEUE);

        // then
        assertAll(
                () -> assertThat(actual.shouldEnqueue()).isTrue(),
                () -> assertThat(viewSubmissionAspectProbe.proceedCount()).isEqualTo(1)
        );
        verify(slackInteractionInboxProcessor).enqueueViewSubmission(payload.toString());
    }

    @Test
    void inbox_외부_실행에서_중복으로_적재되지_않아도_즉시결과를_반환한다() {
        // given
        JsonNode payload = viewSubmissionPayload();
        doReturn(false)
                .when(slackInteractionInboxProcessor)
                .enqueueViewSubmission(payload.toString());

        // when
        ViewSubmissionImmediateDto actual = viewSubmissionAspectProbe.handle(payload, ViewSubmissionProbeMode.RETURN_ENQUEUE);

        // then
        assertAll(
                () -> assertThat(actual.shouldEnqueue()).isTrue(),
                () -> assertThat(viewSubmissionAspectProbe.proceedCount()).isEqualTo(1)
        );
        verify(slackInteractionInboxProcessor).enqueueViewSubmission(payload.toString());
    }

    @Test
    void inbox_외부_실행에서_적재_중_런타임_예외가_나도_예외를_삼키고_즉시결과를_반환한다() {
        // given
        JsonNode payload = viewSubmissionPayload();
        doThrow(new IllegalStateException("enqueue failure"))
                .when(slackInteractionInboxProcessor)
                .enqueueViewSubmission(payload.toString());

        // when
        ViewSubmissionImmediateDto actual = viewSubmissionAspectProbe.handle(payload, ViewSubmissionProbeMode.RETURN_ENQUEUE);

        // then
        assertAll(
                () -> assertThat(actual.shouldEnqueue()).isTrue(),
                () -> assertThat(actual.response()).isEqualTo(SlackActionResponse.empty()),
                () -> assertThat(viewSubmissionAspectProbe.proceedCount()).isEqualTo(1)
        );
        verify(slackInteractionInboxProcessor).enqueueViewSubmission(payload.toString());
    }

    @Test
    void view_submission_aop_대상_메서드가_ViewSubmissionImmediateDto를_반환하지_않으면_예외를_던진다() {
        // given
        JsonNode payload = viewSubmissionPayload();

        // when & then
        assertThatThrownBy(() -> viewSubmissionAspectProbe.invalidReturn(payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ViewSubmissionImmediateDto");

        assertThat(viewSubmissionAspectProbe.invalidProceedCount()).isEqualTo(1);
        verify(slackInteractionInboxProcessor, never()).enqueueViewSubmission(anyString());
    }

    @Test
    void inbox_내부_실행이면_shouldEnqueue_true여도_적재하지_않고_즉시결과를_반환한다() {
        // given
        JsonNode payload = viewSubmissionPayload();

        // when
        ViewSubmissionImmediateDto actual = processingSourceContext.withInboxProcessing(
                () -> viewSubmissionAspectProbe.handle(payload, ViewSubmissionProbeMode.RETURN_ENQUEUE)
        );

        // then
        assertAll(
                () -> assertThat(actual.shouldEnqueue()).isTrue(),
                () -> assertThat(viewSubmissionAspectProbe.proceedCount()).isEqualTo(1)
        );
        verify(slackInteractionInboxProcessor, never()).enqueueViewSubmission(anyString());
    }

    @Test
    void inbox_내부_실행에서_런타임_예외는_그대로_전파한다() {
        // given
        JsonNode payload = viewSubmissionPayload();

        // when & then
        assertThatThrownBy(() -> processingSourceContext.withInboxProcessing(
                () -> viewSubmissionAspectProbe.handle(payload, ViewSubmissionProbeMode.THROW_RUNTIME)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime failure");

        verify(slackInteractionInboxProcessor, never()).enqueueViewSubmission(anyString());
    }

    @Test
    void inbox_내부_실행에서_Error는_그대로_전파한다() {
        // given
        JsonNode payload = viewSubmissionPayload();

        // when & then
        assertThatThrownBy(() -> processingSourceContext.withInboxProcessing(
                () -> viewSubmissionAspectProbe.handle(payload, ViewSubmissionProbeMode.THROW_ERROR)
        ))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("error failure");

        verify(slackInteractionInboxProcessor, never()).enqueueViewSubmission(anyString());
    }

    @Test
    void inbox_내부_실행에서_체크드_예외는_ViewSubmissionAopProceedException으로_감싼다() {
        // given
        JsonNode payload = viewSubmissionPayload();

        // when & then
        assertThatThrownBy(() -> processingSourceContext.withInboxProcessing(
                () -> viewSubmissionAspectProbe.handle(payload, ViewSubmissionProbeMode.THROW_CHECKED)
        ))
                .isInstanceOf(ViewSubmissionAopProceedException.class)
                .hasCauseInstanceOf(Exception.class)
                .hasRootCauseMessage("checked failure");

        verify(slackInteractionInboxProcessor, never()).enqueueViewSubmission(anyString());
    }

    private JsonNode viewSubmissionPayload() {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "view_submission");
        payload.put("callback_id", "review_time_submit");
        return payload;
    }
}
