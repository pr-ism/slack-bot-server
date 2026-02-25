package com.slack.bot.application.review.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewNotificationSourceContextTest {

    ReviewNotificationSourceContext context = new ReviewNotificationSourceContext();

    @Test
    void withSourceKey를_호출하면_현재_sourceKey를_조회할_수_있다() {
        // when
        String value = context.withSourceKey("SOURCE:1", () -> context.currentSourceKey().orElse(""));

        // then
        assertAll(
                () -> assertThat(value).isEqualTo("SOURCE:1"),
                () -> assertThat(context.currentSourceKey()).isEmpty()
        );
    }

    @Test
    void withSourceKey는_중첩_호출후_이전_sourceKey를_복원한다() {
        // when
        String value = context.withSourceKey("OUTER", () ->
                context.withSourceKey("INNER", () -> context.currentSourceKey().orElse(""))
                        + "|" + context.currentSourceKey().orElse("")
        );

        // then
        assertAll(
                () -> assertThat(value).isEqualTo("INNER|OUTER"),
                () -> assertThat(context.currentSourceKey()).isEmpty()
        );
    }

    @Test
    void withSourceKey는_sourceKey가_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> context.withSourceKey(null, () -> null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceKey는 비어 있을 수 없습니다.");
    }

    @Test
    void withSourceKey는_sourceKey가_공백이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> context.withSourceKey(" ", () -> null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceKey는 비어 있을 수 없습니다.");
    }

    @Test
    void requireSourceKey는_현재_값이_없으면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> context.requireSourceKey())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("review_notification sourceKey가 필요합니다.");
    }

    @Test
    void withSourceKey_Runnable에서도_현재_sourceKey를_조회할_수_있다() {
        // when
        context.withSourceKey("SOURCE:RUNNABLE", () -> {
            assertThat(context.requireSourceKey()).isEqualTo("SOURCE:RUNNABLE");
        });

        // then
        assertThat(context.currentSourceKey()).isEmpty();
    }

    @Test
    void withSourceKey_Supplier가_예외를_던져도_sourceKey가_정리된다() {
        // when & then
        assertThatThrownBy(() -> context.<String>withSourceKey("SOURCE:1", () -> {
            throw new RuntimeException("처리 중 오류");
        }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("처리 중 오류");

        assertThat(context.currentSourceKey()).isEmpty();
    }

    @Test
    void withSourceKey_Runnable이_예외를_던져도_sourceKey가_정리된다() {
        // when & then
        assertThatThrownBy(() -> context.withSourceKey("SOURCE:1", (Runnable) () -> {
            throw new RuntimeException("처리 중 오류");
        }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("처리 중 오류");

        assertThat(context.currentSourceKey()).isEmpty();
    }
}
