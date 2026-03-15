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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interaction.block.BlockActionType;
import com.slack.bot.application.interaction.box.ProcessingSourceContext;
import com.slack.bot.application.interaction.box.aop.aspect.exception.BlockActionAopProceedException;
import com.slack.bot.application.interaction.box.aop.aspect.support.AspectIntegrationProbes.BlockActionAspectProbe;
import com.slack.bot.application.interaction.box.aop.aspect.support.AspectIntegrationProbes.BlockActionProbeMode;
import com.slack.bot.application.interaction.box.in.SlackInteractionInboxProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BlockActionInboxEnqueueAspectIntegrationTest {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ProcessingSourceContext processingSourceContext;

    @Autowired
    SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    @Autowired
    BlockActionAspectProbe blockActionAspectProbe;

    @BeforeEach
    void setUp() {
        blockActionAspectProbe.reset();
    }

    @Test
    void inbox_외부_실행이면_대상_메서드를_실행하지_않고_block_action_inbox에_적재한다() {
        // given
        JsonNode payload = blockActionPayload();
        doReturn(true)
                .when(slackInteractionInboxProcessor)
                .enqueueBlockAction(payload.toString());

        // when
        Object actual = blockActionAspectProbe.handle(payload, BlockActionProbeMode.RETURN_VALUE);

        // then
        assertAll(
                () -> assertThat(actual).isNull(),
                () -> assertThat(blockActionAspectProbe.proceedCount()).isZero()
        );
        verify(slackInteractionInboxProcessor).enqueueBlockAction(payload.toString());
    }

    @Test
    void inbox_외부_실행에서_중복으로_적재되지_않아도_정상_반환한다() {
        // given
        JsonNode payload = blockActionPayload();
        doReturn(false)
                .when(slackInteractionInboxProcessor)
                .enqueueBlockAction(payload.toString());

        // when
        Object actual = blockActionAspectProbe.handle(payload, BlockActionProbeMode.RETURN_VALUE);

        // then
        assertAll(
                () -> assertThat(actual).isNull(),
                () -> assertThat(blockActionAspectProbe.proceedCount()).isZero()
        );
        verify(slackInteractionInboxProcessor).enqueueBlockAction(payload.toString());
    }

    @Test
    void inbox_외부_실행에서_적재_중_런타임_예외가_나면_예외를_전파한다() {
        // given
        JsonNode payload = blockActionPayload();
        doThrow(new IllegalStateException("enqueue failed"))
                .when(slackInteractionInboxProcessor)
                .enqueueBlockAction(payload.toString());

        // when & then
        assertThatThrownBy(() -> blockActionAspectProbe.handle(payload, BlockActionProbeMode.RETURN_VALUE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("enqueue failed");

        assertThat(blockActionAspectProbe.proceedCount()).isZero();
    }

    @Test
    void inbox_외부_실행이어도_open_review_scheduler는_동기_처리한다() {
        // given
        JsonNode payload = blockActionPayload(BlockActionType.OPEN_REVIEW_SCHEDULER.value());

        // when
        Object actual = blockActionAspectProbe.handle(payload, BlockActionProbeMode.RETURN_VALUE);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo("PROCEEDED"),
                () -> assertThat(blockActionAspectProbe.proceedCount()).isEqualTo(1)
        );
        verify(slackInteractionInboxProcessor, never()).enqueueBlockAction(anyString());
    }

    @Test
    void inbox_외부_실행이어도_change_review_reservation은_동기_처리한다() {
        // given
        JsonNode payload = blockActionPayload(BlockActionType.CHANGE_REVIEW_RESERVATION.value());

        // when
        Object actual = blockActionAspectProbe.handle(payload, BlockActionProbeMode.RETURN_VALUE);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo("PROCEEDED"),
                () -> assertThat(blockActionAspectProbe.proceedCount()).isEqualTo(1)
        );
        verify(slackInteractionInboxProcessor, never()).enqueueBlockAction(anyString());
    }

    @Test
    void inbox_내부_실행이면_대상_메서드를_바로_실행하고_결과를_반환한다() {
        // given
        JsonNode payload = blockActionPayload();

        // when
        Object actual = processingSourceContext.withInboxProcessing(
                () -> blockActionAspectProbe.handle(payload, BlockActionProbeMode.RETURN_VALUE)
        );

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo("PROCEEDED"),
                () -> assertThat(blockActionAspectProbe.proceedCount()).isEqualTo(1)
        );
        verify(slackInteractionInboxProcessor, never()).enqueueBlockAction(anyString());
    }

    @Test
    void inbox_내부_실행에서_런타임_예외는_그대로_전파한다() {
        // given
        JsonNode payload = blockActionPayload();

        // when & then
        assertThatThrownBy(() -> processingSourceContext.withInboxProcessing(
                () -> blockActionAspectProbe.handle(payload, BlockActionProbeMode.THROW_RUNTIME)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime failure");

        verify(slackInteractionInboxProcessor, never()).enqueueBlockAction(anyString());
    }

    @Test
    void inbox_내부_실행에서_Error는_그대로_전파한다() {
        // given
        JsonNode payload = blockActionPayload();

        // when & then
        assertThatThrownBy(() -> processingSourceContext.withInboxProcessing(
                () -> blockActionAspectProbe.handle(payload, BlockActionProbeMode.THROW_ERROR)
        ))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("error failure");

        verify(slackInteractionInboxProcessor, never()).enqueueBlockAction(anyString());
    }

    @Test
    void inbox_내부_실행에서_체크드_예외는_BlockActionAopProceedException으로_감싼다() {
        // given
        JsonNode payload = blockActionPayload();

        // when & then
        assertThatThrownBy(() -> processingSourceContext.withInboxProcessing(
                () -> blockActionAspectProbe.handle(payload, BlockActionProbeMode.THROW_CHECKED)
        ))
                .isInstanceOf(BlockActionAopProceedException.class)
                .hasCauseInstanceOf(Exception.class)
                .hasRootCauseMessage("checked failure");

        verify(slackInteractionInboxProcessor, never()).enqueueBlockAction(anyString());
    }

    private JsonNode blockActionPayload() {
        return blockActionPayload(BlockActionType.CANCEL_REVIEW_RESERVATION.value());
    }

    private JsonNode blockActionPayload(String actionId) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "block_actions");
        ArrayNode actions = objectMapper.createArrayNode();
        actions.add(objectMapper.createObjectNode().put("action_id", actionId));
        payload.set("actions", actions);
        return payload;
    }
}
