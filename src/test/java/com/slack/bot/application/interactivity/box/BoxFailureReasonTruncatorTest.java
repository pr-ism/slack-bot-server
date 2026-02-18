package com.slack.bot.application.interactivity.box;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BoxFailureReasonTruncatorTest {

    private final BoxFailureReasonTruncator truncator = new BoxFailureReasonTruncator();

    @Test
    void null_실패사유는_null을_반환한다() {
        // given
        String reason = null;

        // when
        String actual = truncator.truncate(reason);

        // then
        assertThat(actual).isNull();
    }

    @Test
    void 길이가_500자_이하인_실패사유는_그대로_반환한다() {
        // given
        String reason = "a".repeat(500);

        // when
        String actual = truncator.truncate(reason);

        // then
        assertThat(actual).isEqualTo(reason);
    }

    @Test
    void 길이가_500자를_초과한_실패사유는_500자로_잘라_반환한다() {
        // given
        String reason = "a".repeat(500) + "b";

        // when
        String actual = truncator.truncate(reason);

        // then
        assertThat(actual)
                .hasSize(500)
                .isEqualTo("a".repeat(500));
    }
}
