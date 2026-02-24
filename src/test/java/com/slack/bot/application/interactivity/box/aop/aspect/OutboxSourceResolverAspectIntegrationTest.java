package com.slack.bot.application.interactivity.box.aop.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.box.aop.aspect.support.AspectIntegrationProbes.OutboxSourceResolverProbe;
import com.slack.bot.application.interactivity.box.out.OutboxIdempotencySourceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OutboxSourceResolverAspectIntegrationTest {

    @Autowired
    OutboxIdempotencySourceContext outboxIdempotencySourceContext;

    @Autowired
    OutboxSourceResolverProbe outboxSourceResolverProbe;

    @BeforeEach
    void setUp() {
        outboxSourceResolverProbe.reset();
    }

    @Test
    void 대상_메서드_인자가_없으면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> outboxSourceResolverProbe.noArgs())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("대상 인자가 없습니다");

        assertThat(outboxSourceResolverProbe.noArgsProceedCount()).isZero();
    }

    @Test
    void 첫번째_인자가_String이_아니면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> outboxSourceResolverProbe.wrongType(1L, "payload"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("타입이 잘못되었습니다");

        assertThat(outboxSourceResolverProbe.wrongTypeProceedCount()).isZero();
    }

    @Test
    void source_key가_이미_있으면_그값으로_그대로_진행한다() {
        // when
        String actual = outboxSourceResolverProbe.resolve("EXPLICIT:KEY", "payload");

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo("EXPLICIT:KEY|payload"),
                () -> assertThat(outboxSourceResolverProbe.resolveProceedCount()).isEqualTo(1),
                () -> assertThat(outboxSourceResolverProbe.observedSourceKey()).isEqualTo("EXPLICIT:KEY")
        );
    }

    @Test
    void source_key가_null이면_컨텍스트의_출처키를_주입한다() {
        // when
        String actual = outboxIdempotencySourceContext.withBusinessEventSource(
                "EVT-123",
                () -> outboxSourceResolverProbe.resolve(null, "payload")
        );

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo("BUSINESS:EVT-123|payload"),
                () -> assertThat(outboxSourceResolverProbe.resolveProceedCount()).isEqualTo(1),
                () -> assertThat(outboxSourceResolverProbe.observedSourceKey()).isEqualTo("BUSINESS:EVT-123")
        );
    }

    @Test
    void source_key가_null이고_컨텍스트에도_출처키가_없으면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> outboxSourceResolverProbe.resolve(null, "payload"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("source 키가 필요합니다");

        assertThat(outboxSourceResolverProbe.resolveProceedCount()).isZero();
    }
}
