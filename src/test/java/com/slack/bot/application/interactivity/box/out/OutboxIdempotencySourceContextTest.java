package com.slack.bot.application.interactivity.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OutboxIdempotencySourceContextTest {

    OutboxIdempotencySourceContext context = new OutboxIdempotencySourceContext();

    @Test
    void source가_없으면_requireSourceKey는_예외를_던진다() {
        assertThatThrownBy(() -> context.requireSourceKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("아웃박스 멱등성 source 키가 필요합니다.");
    }

    @Test
    void withInboxSource는_실행_중_source를_설정하고_종료_후_제거한다() {
        context.withInboxSource(123L, () -> assertThat(context.requireSourceKey()).isEqualTo("INBOX:123"));

        assertThat(context.currentSourceKey()).isEmpty();
    }

    @Test
    void withBusinessEventSource는_source를_설정하고_반환_값을_전달한다() {
        String actual = context.withBusinessEventSource("EVT-1", context::requireSourceKey);

        assertThat(actual).isEqualTo("BUSINESS:EVT-1");
    }

    @Test
    void withBusinessEventSource는_null_source를_빈_문자열로_정규화_한다() {
        String actual = context.withBusinessEventSource(null, context::requireSourceKey);

        assertThat(actual).isEqualTo("BUSINESS:");
    }
}
