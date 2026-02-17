package com.slack.bot.application.interactivity.box.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.BlockActionInteractionService;
import com.slack.bot.application.interactivity.box.InteractivityImmediateProcessor;
import com.slack.bot.application.interactivity.box.SlackInteractionIdempotencyKeyGenerator;
import com.slack.bot.application.interactivity.box.SlackInteractionIdempotencyScope;
import com.slack.bot.application.interactivity.box.aop.exception.BlockActionAopProceedException;
import com.slack.bot.application.interactivity.box.in.SlackInteractionInboxEntryProcessor;
import com.slack.bot.application.interactivity.box.in.SlackInteractionInboxProcessor;
import com.slack.bot.application.interactivity.box.out.OutboxIdempotencySourceContext;
import com.slack.bot.application.interactivity.box.out.SlackNotificationOutboxWriter;
import com.slack.bot.application.interactivity.view.ViewSubmissionInteractionService;
import com.slack.bot.application.interactivity.view.dto.ViewSubmissionSyncResultDto;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import com.slack.bot.infrastructure.interaction.persistence.box.in.JpaSlackInteractionInboxRepository;
import com.slack.bot.infrastructure.interaction.persistence.box.out.JpaSlackNotificationOutboxRepository;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;

@IntegrationTest
@Import(InteractivityAopIntegrationTest.AopFailureProbeConfig.class)
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InteractivityAopIntegrationTest {

    @Autowired
    BlockActionInteractionService blockActionInteractionService;

    @Autowired
    ViewSubmissionInteractionService viewSubmissionInteractionService;

    @Autowired
    SlackInteractionInboxProcessor slackInteractionInboxProcessor;

    @Autowired
    SlackInteractionInboxEntryProcessor slackInteractionInboxEntryProcessor;

    @Autowired
    InteractivityImmediateProcessor interactivityImmediateProcessor;

    @Autowired
    OutboxIdempotencySourceContext outboxIdempotencySourceContext;

    @Autowired
    SlackNotificationOutboxWriter slackNotificationOutboxWriter;

    @Autowired
    SlackInteractionInboxRepository slackInteractionInboxRepository;

    @Autowired
    JpaSlackInteractionInboxRepository jpaSlackInteractionInboxRepository;

    @Autowired
    JpaSlackNotificationOutboxRepository jpaSlackNotificationOutboxRepository;

    @Autowired
    SlackInteractionIdempotencyKeyGenerator slackInteractionIdempotencyKeyGenerator;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    BlockActionAopFailureProbe blockActionAopFailureProbe;

    @Autowired
    ViewSubmissionAopFailureProbe viewSubmissionAopFailureProbe;

    @Test
    void block_action_핸들_호출시_AOP가_inbox_enqueue를_위임한다() {
        // given
        JsonNode payload = objectMapper.createObjectNode();

        // when
        blockActionInteractionService.handle(payload);

        // then
        verify(slackInteractionInboxProcessor, atLeastOnce()).enqueueBlockAction(payload.toString());
    }

    @Test
    void view_submission_동기_결과가_enqueue면_AOP가_inbox_enqueue를_수행한다() {
        // given
        JsonNode payload = viewSubmissionPayload("now");

        // when
        ViewSubmissionSyncResultDto actual = viewSubmissionInteractionService.handle(payload);

        // then
        assertAll(
                () -> assertThat(actual.shouldEnqueue()).isTrue(),
                () -> verify(slackInteractionInboxProcessor, atLeastOnce()).enqueueViewSubmission(payload.toString())
        );
    }

    @Test
    void view_submission_동기_결과가_no_enqueue면_AOP가_inbox_enqueue를_건너뛴다() {
        // given
        JsonNode payload = viewSubmissionPayload("wrong-value");

        // when
        ViewSubmissionSyncResultDto actual = viewSubmissionInteractionService.handle(payload);

        // then
        assertAll(
                () -> assertThat(actual.shouldEnqueue()).isFalse(),
                () -> assertThat(jpaSlackInteractionInboxRepository.findAll()).isEmpty()
        );
    }

    @Test
    void enqueue_block_action은_최초_적재_성공시에만_즉시처리_AOP를_트리거한다() {
        // given
        String payloadJson = "{}";

        // when
        boolean first = slackInteractionInboxProcessor.enqueueBlockAction(payloadJson);
        boolean second = slackInteractionInboxProcessor.enqueueBlockAction(payloadJson);

        // then
        assertAll(
                () -> assertThat(first).isTrue(),
                () -> assertThat(second).isFalse(),
                () -> verify(interactivityImmediateProcessor, atLeastOnce()).triggerBlockActionInbox()
        );
    }

    @Test
    void enqueue_view_submission은_최초_적재_성공시에만_즉시처리_AOP를_트리거한다() {
        // given
        String payloadJson = "{}";

        // when
        boolean first = slackInteractionInboxProcessor.enqueueViewSubmission(payloadJson);
        boolean second = slackInteractionInboxProcessor.enqueueViewSubmission(payloadJson);

        // then
        assertAll(
                () -> assertThat(first).isTrue(),
                () -> assertThat(second).isFalse(),
                () -> verify(interactivityImmediateProcessor, atLeastOnce()).triggerViewSubmissionInbox()
        );
    }

    @Test
    void outbox_writer_enqueue시_즉시처리_AOP가_트리거된다() {
        // when
        slackNotificationOutboxWriter.enqueueChannelText("SRC-OUTBOX", "xoxb-token", "C1", "hello");

        // then
        verify(interactivityImmediateProcessor, atLeastOnce()).triggerOutbox();
    }

    @Test
    void enqueue_ephemeral_text_호출시_resolve_outbox_source_AOP와_즉시처리_AOP가_동작한다() {
        // given
        String sourceKey = "SRC-EPHEMERAL-TEXT";
        String token = "xoxb-token";
        String channelId = "C1";
        String userId = "U1";
        String text = "hello-ephemeral";

        // when
        slackNotificationOutboxWriter.enqueueEphemeralText(sourceKey, token, channelId, userId, text);

        // then
        List<SlackNotificationOutbox> outboxes = jpaSlackNotificationOutboxRepository.findAll();
        assertThat(outboxes).hasSize(1);
        SlackNotificationOutbox actual = outboxes.getFirst();

        assertAll(
                () -> assertThat(actual.getMessageType()).isEqualTo(SlackNotificationOutboxMessageType.EPHEMERAL_TEXT),
                () -> assertThat(actual.getToken()).isEqualTo(token),
                () -> assertThat(actual.getChannelId()).isEqualTo(channelId),
                () -> assertThat(actual.getUserId()).isEqualTo(userId),
                () -> assertThat(actual.getText()).isEqualTo(text),
                () -> assertThat(actual.getIdempotencyKey()).isEqualTo(
                        outboxIdempotencyKey(
                                sourceKey,
                                SlackNotificationOutboxMessageType.EPHEMERAL_TEXT,
                                token,
                                channelId,
                                userId
                        )
                ),
                () -> verify(interactivityImmediateProcessor, atLeastOnce()).triggerOutbox()
        );
    }

    @Test
    void enqueue_ephemeral_blocks_호출시_resolve_outbox_source_AOP와_즉시처리_AOP가_동작한다() {
        // given
        String sourceKey = "SRC-EPHEMERAL-BLOCKS";
        String token = "xoxb-token";
        String channelId = "C1";
        String userId = "U1";
        Object blocks = List.of();
        String fallbackText = "fallback-ephemeral";

        // when
        slackNotificationOutboxWriter.enqueueEphemeralBlocks(
                sourceKey,
                token,
                channelId,
                userId,
                blocks,
                fallbackText
        );

        // then
        List<SlackNotificationOutbox> outboxes = jpaSlackNotificationOutboxRepository.findAll();
        assertThat(outboxes).hasSize(1);
        SlackNotificationOutbox actual = outboxes.getFirst();

        assertAll(
                () -> assertThat(actual.getMessageType()).isEqualTo(SlackNotificationOutboxMessageType.EPHEMERAL_BLOCKS),
                () -> assertThat(actual.getToken()).isEqualTo(token),
                () -> assertThat(actual.getChannelId()).isEqualTo(channelId),
                () -> assertThat(actual.getUserId()).isEqualTo(userId),
                () -> assertThat(actual.getBlocksJson()).isEqualTo("[]"),
                () -> assertThat(actual.getFallbackText()).isEqualTo(fallbackText),
                () -> assertThat(actual.getIdempotencyKey()).isEqualTo(
                        outboxIdempotencyKey(
                                sourceKey,
                                SlackNotificationOutboxMessageType.EPHEMERAL_BLOCKS,
                                token,
                                channelId,
                                userId
                        )
                ),
                () -> verify(interactivityImmediateProcessor, atLeastOnce()).triggerOutbox()
        );
    }

    @Test
    void enqueue_channel_blocks_호출시_resolve_outbox_source_AOP와_즉시처리_AOP가_동작한다() {
        // given
        String sourceKey = "SRC-CHANNEL-BLOCKS";
        String token = "xoxb-token";
        String channelId = "C1";
        Object blocks = List.of();
        String fallbackText = "fallback-channel";

        // when
        slackNotificationOutboxWriter.enqueueChannelBlocks(
                sourceKey,
                token,
                channelId,
                blocks,
                fallbackText
        );

        // then
        List<SlackNotificationOutbox> outboxes = jpaSlackNotificationOutboxRepository.findAll();
        assertThat(outboxes).hasSize(1);
        SlackNotificationOutbox actual = outboxes.getFirst();

        assertAll(
                () -> assertThat(actual.getMessageType()).isEqualTo(SlackNotificationOutboxMessageType.CHANNEL_BLOCKS),
                () -> assertThat(actual.getToken()).isEqualTo(token),
                () -> assertThat(actual.getChannelId()).isEqualTo(channelId),
                () -> assertThat(actual.getUserId()).isNull(),
                () -> assertThat(actual.getBlocksJson()).isEqualTo("[]"),
                () -> assertThat(actual.getFallbackText()).isEqualTo(fallbackText),
                () -> assertThat(actual.getIdempotencyKey()).isEqualTo(
                        outboxIdempotencyKey(
                                sourceKey,
                                SlackNotificationOutboxMessageType.CHANNEL_BLOCKS,
                                token,
                                channelId,
                                null
                        )
                ),
                () -> verify(interactivityImmediateProcessor, atLeastOnce()).triggerOutbox()
        );
    }

    @Test
    void resolve_outbox_source_AOP는_명시적_source를_그대로_사용한다() {
        // given
        String sourceKey = "SRC-EXPLICIT";
        String token = "xoxb-token";
        String channelId = "C1";
        String text = "hello";

        // when
        slackNotificationOutboxWriter.enqueueChannelText(sourceKey, token, channelId, text);

        // then
        List<SlackNotificationOutbox> outboxes = jpaSlackNotificationOutboxRepository.findAll();

        assertThat(outboxes).hasSize(1);
        SlackNotificationOutbox actual = outboxes.getFirst();

        assertThat(actual.getIdempotencyKey()).isEqualTo(
                outboxIdempotencyKey(sourceKey, SlackNotificationOutboxMessageType.CHANNEL_TEXT, token, channelId, null)
        );
    }

    @Test
    void resolve_outbox_source_AOP는_null_source에_context_source를_주입한다() {
        // given
        String token = "xoxb-token";
        String channelId = "C1";
        String text = "hello";

        // when
        outboxIdempotencySourceContext.withBusinessEventSource("EVT-1", () -> {
            slackNotificationOutboxWriter.enqueueChannelText(null, token, channelId, text);
            return null;
        });

        // then
        List<SlackNotificationOutbox> outboxes = jpaSlackNotificationOutboxRepository.findAll();

        assertThat(outboxes).hasSize(1);
        SlackNotificationOutbox actual = outboxes.getFirst();

        assertThat(actual.getIdempotencyKey()).isEqualTo(
                outboxIdempotencyKey(
                        "BUSINESS:EVT-1",
                        SlackNotificationOutboxMessageType.CHANNEL_TEXT,
                        token,
                        channelId,
                        null
                )
        );
    }

    @Test
    void resolve_outbox_source_AOP는_source가_없으면_예외를_던진다() {
        // given
        String token = "xoxb-token";
        String channelId = "C1";
        String text = "hello";

        // when & then
        assertThatThrownBy(() -> slackNotificationOutboxWriter.enqueueChannelText(null, token, channelId, text))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("아웃박스 멱등성 source 키가 필요합니다.");

        assertThat(jpaSlackNotificationOutboxRepository.findAll()).isEmpty();
    }

    @Test
    void block_action_enqueue_AOP는_인박스_컨텍스트_진행중_체크예외를_custom_exception으로_래핑한다() {
        // given
        JsonNode payload = objectMapper.createObjectNode();

        // when & then
        assertThatThrownBy(() -> outboxIdempotencySourceContext.withInboxSource(1L, () -> {
            blockActionAopFailureProbe.throwChecked(payload);
        }))
                .isInstanceOf(BlockActionAopProceedException.class)
                .hasMessageContaining("stage=INBOX_SOURCE_BOUND")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void block_action_enqueue_AOP는_인박스_컨텍스트_진행중_런타임예외를_그대로_전파한다() {
        // given
        JsonNode payload = objectMapper.createObjectNode();

        // when & then
        assertThatThrownBy(() -> outboxIdempotencySourceContext.withInboxSource(1L, () -> {
            blockActionAopFailureProbe.throwRuntime(payload);
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("runtime-failure");
    }

    @Test
    void view_submission_enqueue_AOP는_반환타입이_규약과_다르면_예외를_던진다() {
        // given
        JsonNode payload = objectMapper.createObjectNode();

        // when & then
        assertThatThrownBy(() -> viewSubmissionAopFailureProbe.invalidReturn(payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("view_submission enqueue AOP 대상 메서드는 ViewSubmissionSyncResultDto를 반환해야 합니다.");
    }

    @Test
    void bind_inbox_source_AOP는_block_actions_처리시에_inbox_source를_바인딩한다() {
        // given
        SlackInteractionInbox inbox = savePendingInbox(SlackInteractionInboxType.BLOCK_ACTIONS, "{}");

        // when
        slackInteractionInboxEntryProcessor.processBlockAction(inbox);

        // then
        verify(outboxIdempotencySourceContext, atLeastOnce()).withInboxSource(eq(inbox.getId()), any(Runnable.class));
    }

    @Test
    void bind_inbox_source_AOP는_view_submission_처리시에_inbox_source를_바인딩한다() {
        // given
        SlackInteractionInbox inbox = savePendingInbox(SlackInteractionInboxType.VIEW_SUBMISSION, "{}");

        // when
        slackInteractionInboxEntryProcessor.processViewSubmission(inbox);

        // then
        verify(outboxIdempotencySourceContext, atLeastOnce()).withInboxSource(eq(inbox.getId()), any(Runnable.class));
    }

    private SlackInteractionInbox savePendingInbox(SlackInteractionInboxType interactionType, String payloadJson) {
        String idempotencyKey = interactionType + "-aop-" + System.nanoTime();
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(interactionType, idempotencyKey, payloadJson);

        return slackInteractionInboxRepository.save(inbox);
    }

    private JsonNode viewSubmissionPayload(String selectedOption) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "view_submission");
        payload.set("team", objectMapper.createObjectNode().put("id", "T1"));
        payload.set("user", objectMapper.createObjectNode().put("id", "U1"));

        ObjectNode view = objectMapper.createObjectNode();
        view.put("callback_id", "review_time_submit");
        view.put("private_metadata", "meta-json");

        ObjectNode state = objectMapper.createObjectNode();
        ObjectNode values = objectMapper.createObjectNode();
        ObjectNode timeBlock = objectMapper.createObjectNode();
        ObjectNode timeAction = objectMapper.createObjectNode();
        ObjectNode selected = objectMapper.createObjectNode();
        selected.put("value", selectedOption);
        timeAction.set("selected_option", selected);
        timeBlock.set("time_action", timeAction);
        values.set("time_block", timeBlock);
        state.set("values", values);
        view.set("state", state);

        payload.set("view", view);
        return payload;
    }

    private String outboxIdempotencyKey(
            String sourceKey,
            SlackNotificationOutboxMessageType messageType,
            String token,
            String channelId,
            String userId
    ) {
        ObjectNode source = objectMapper.createObjectNode();

        source.put("source", sourceKey);
        source.put("messageType", messageType.name());
        source.put("target", nullToEmpty(token) + ":" + nullToEmpty(channelId) + ":" + nullToEmpty(userId));

        return slackInteractionIdempotencyKeyGenerator.generate(
                SlackInteractionIdempotencyScope.SLACK_NOTIFICATION_OUTBOX,
                source.toString()
        );
    }

    private String nullToEmpty(String value) {
        if (value == null) {
            return "";
        }

        return value;
    }

    @TestConfiguration
    static class AopFailureProbeConfig {

        @Bean
        BlockActionAopFailureProbe blockActionAopFailureProbe() {
            return new BlockActionAopFailureProbe();
        }

        @Bean
        ViewSubmissionAopFailureProbe viewSubmissionAopFailureProbe() {
            return new ViewSubmissionAopFailureProbe();
        }
    }

    static class BlockActionAopFailureProbe {

        @EnqueueBlockActionInInbox
        public void throwChecked(JsonNode payload) {
            throwCheckedIOException();
        }

        @EnqueueBlockActionInInbox
        public void throwRuntime(JsonNode payload) {
            throw new IllegalStateException("runtime-failure");
        }

        private void throwCheckedIOException() {
            sneakyThrow(new IOException("checked-failure"));
        }

        @SuppressWarnings("unchecked")
        private static <E extends Throwable> void sneakyThrow(Throwable throwable) throws E {
            throw (E) throwable;
        }
    }

    static class ViewSubmissionAopFailureProbe {

        @EnqueueViewSubmissionInInbox
        public String invalidReturn(JsonNode payload) {
            return "invalid-return";
        }
    }
}
