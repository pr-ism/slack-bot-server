package com.slack.bot.application.interaction.box.aop.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interaction.box.ProcessingSourceContext;
import com.slack.bot.application.interaction.box.aop.aspect.support.AspectIntegrationProbes.InboxToOutboxMode;
import com.slack.bot.application.interaction.box.aop.aspect.support.AspectIntegrationProbes.InboxToOutboxProbe;
import com.slack.bot.application.interaction.box.out.OutboxIdempotencySourceContext;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InboxToOutboxSourceAspectIntegrationTest {

    @Autowired
    ProcessingSourceContext processingSourceContext;

    @Autowired
    OutboxIdempotencySourceContext outboxIdempotencySourceContext;

    @Autowired
    SlackInteractionInboxRepository slackInteractionInboxRepository;

    @Autowired
    InboxToOutboxProbe inboxToOutboxProbe;

    @BeforeEach
    void setUp() {
        inboxToOutboxProbe.reset();
    }

    @Test
    void inbox_source를_바인딩하고_처리_출처를_inbox로_설정한_뒤_원복한다() throws Exception {
        // given
        SlackInteractionInbox inbox = saveInbox();

        // when
        String actual = inboxToOutboxProbe.bind(inbox, InboxToOutboxMode.RETURN_VALUE);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo("INBOX:" + inbox.getId()),
                () -> assertThat(inboxToOutboxProbe.observedInboxProcessing()).isTrue(),
                () -> assertThat(inboxToOutboxProbe.observedSourceKey()).hasValue("INBOX:" + inbox.getId()),
                () -> assertThat(processingSourceContext.isInboxProcessing()).isFalse(),
                () -> assertThat(outboxIdempotencySourceContext.currentSourceKey()).isEmpty()
        );
    }

    @Test
    void inbox_id가_null이면_즉시_예외를_던진다() {
        // given
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key-" + System.nanoTime(),
                "{}"
        );

        // when & then
        assertThatThrownBy(() -> inboxToOutboxProbe.bind(inbox, InboxToOutboxMode.RETURN_VALUE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("인박스 source 바인딩 대상의 id가 없습니다.");

        assertAll(
                () -> assertThat(inboxToOutboxProbe.observedInboxProcessing()).isFalse(),
                () -> assertThat(inboxToOutboxProbe.observedSourceKey()).isEmpty(),
                () -> assertThat(processingSourceContext.isInboxProcessing()).isFalse(),
                () -> assertThat(outboxIdempotencySourceContext.currentSourceKey()).isEmpty()
        );
    }

    @Test
    void 런타임_예외는_그대로_전파하고_컨텍스트를_원복한다() {
        // given
        SlackInteractionInbox inbox = saveInbox();

        // when & then
        assertThatThrownBy(() -> inboxToOutboxProbe.bind(inbox, InboxToOutboxMode.THROW_RUNTIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtime failure");

        assertAll(
                () -> assertThat(processingSourceContext.isInboxProcessing()).isFalse(),
                () -> assertThat(outboxIdempotencySourceContext.currentSourceKey()).isEmpty()
        );
    }

    @Test
    void Error는_그대로_전파하고_컨텍스트를_원복한다() {
        // given
        SlackInteractionInbox inbox = saveInbox();

        // when & then
        assertThatThrownBy(() -> inboxToOutboxProbe.bind(inbox, InboxToOutboxMode.THROW_ERROR))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("error failure");

        assertAll(
                () -> assertThat(processingSourceContext.isInboxProcessing()).isFalse(),
                () -> assertThat(outboxIdempotencySourceContext.currentSourceKey()).isEmpty()
        );
    }

    @Test
    void 체크드_예외는_래핑없이_전파하고_컨텍스트를_원복한다() {
        // given
        SlackInteractionInbox inbox = saveInbox();

        // when & then
        assertThatThrownBy(() -> inboxToOutboxProbe.bind(inbox, InboxToOutboxMode.THROW_CHECKED))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("checked failure");

        assertAll(
                () -> assertThat(processingSourceContext.isInboxProcessing()).isFalse(),
                () -> assertThat(outboxIdempotencySourceContext.currentSourceKey()).isEmpty()
        );
    }

    private SlackInteractionInbox saveInbox() {
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "key-" + System.nanoTime(),
                "{}"
        );
        return slackInteractionInboxRepository.save(inbox);
    }
}
