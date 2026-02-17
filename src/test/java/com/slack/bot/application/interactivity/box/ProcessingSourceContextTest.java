package com.slack.bot.application.interactivity.box;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProcessingSourceContextTest {

    ProcessingSourceContext context = new ProcessingSourceContext();

    @Test
    void withInboxProcessing_내부에서는_inbox_processing으로_인식한다() {
        // when
        context.withInboxProcessing(() -> assertThat(context.isInboxProcessing()).isTrue());

        // then
        assertThat(context.isInboxProcessing()).isFalse();
    }

    @Test
    void withInboxProcessing_supplier는_값을_반환하고_컨텍스트를_복원한다() {
        // when
        String actual = context.withInboxProcessing(() -> context.isInboxProcessing() ? "INBOX" : "NONE");

        // then
        assertThat(actual).isEqualTo("INBOX");
        assertThat(context.isInboxProcessing()).isFalse();
    }
}
