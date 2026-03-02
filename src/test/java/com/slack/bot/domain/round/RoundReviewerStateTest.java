package com.slack.bot.domain.round;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RoundReviewerStateTest {

    @Test
    void REQUESTED_상태_여부를_확인한다() {
        // given
        RoundReviewerState requested = RoundReviewerState.REQUESTED;

        // when
        boolean actual = requested.isRequested();

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void REVIEWED_상태_여부를_확인한다() {
        // given
        RoundReviewerState reviewed = RoundReviewerState.REVIEWED;

        // when
        boolean actual = reviewed.isReviewed();

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void REVIEWED_상태를_request하면_REQUESTED가_된다() {
        // given
        RoundReviewerState reviewed = RoundReviewerState.REVIEWED;

        // when
        RoundReviewerState actual = reviewed.request();

        // then
        assertThat(actual).isEqualTo(RoundReviewerState.REQUESTED);
    }

    @Test
    void REQUESTED_상태를_review하면_REVIEWED가_된다() {
        // given
        RoundReviewerState requested = RoundReviewerState.REQUESTED;

        // when
        RoundReviewerState actual = requested.review();

        // then
        assertThat(actual).isEqualTo(RoundReviewerState.REVIEWED);
    }
}
