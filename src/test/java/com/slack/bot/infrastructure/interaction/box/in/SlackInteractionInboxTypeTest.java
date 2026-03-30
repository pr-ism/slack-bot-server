package com.slack.bot.infrastructure.interaction.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackInteractionInboxTypeTest {

    @Test
    void BLOCK_ACTIONS는_block_actions_type으로_판별된다() {
        // when
        SlackInteractionInboxType actual = SlackInteractionInboxType.BLOCK_ACTIONS;

        // then
        assertAll(
                () -> assertThat(actual.isBlockActions()).isTrue(),
                () -> assertThat(actual.isViewSubmission()).isFalse()
        );
    }

    @Test
    void VIEW_SUBMISSION은_view_submission_type으로_판별된다() {
        // when
        SlackInteractionInboxType actual = SlackInteractionInboxType.VIEW_SUBMISSION;

        // then
        assertAll(
                () -> assertThat(actual.isBlockActions()).isFalse(),
                () -> assertThat(actual.isViewSubmission()).isTrue()
        );
    }
}
