package com.slack.bot.domain.round;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RoundReviewerTest {

    @Test
    void 라운드_리뷰어를_초기화한다() {
        // when
        RoundReviewer reviewer = assertDoesNotThrow(() -> RoundReviewer.requested(1L, "github-reviewer"));

        // then
        assertAll(
                () -> assertThat(reviewer.getPullRequestRoundId()).isEqualTo(1L),
                () -> assertThat(reviewer.getReviewerGithubId()).isEqualTo("github-reviewer"),
                () -> assertThat(reviewer.isRequested()).isTrue()
        );
    }

    @Test
    void pullRequestRoundId가_null이면_초기화할_수_없다() {
        // when & then
        assertThatThrownBy(() -> RoundReviewer.requested(null, "github-reviewer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pullRequestRoundId는 비어 있을 수 없습니다.");
    }

    @Test
    void pullRequestRoundId가_1_미만이면_초기화할_수_없다() {
        // when & then
        assertThatThrownBy(() -> RoundReviewer.requested(0L, "github-reviewer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pullRequestRoundId는 비어 있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void reviewerGithubId가_비어_있으면_초기화할_수_없다(String reviewerGithubId) {
        // when & then
        assertThatThrownBy(() -> RoundReviewer.requested(1L, reviewerGithubId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reviewerGithubId는 비어 있을 수 없습니다.");
    }

    @Test
    void 상태를_변경한다() {
        // given
        RoundReviewer reviewer = RoundReviewer.requested(1L, "github-reviewer");

        // when
        reviewer.markReviewed();
        boolean isRequestedAfterReviewed = reviewer.isRequested();
        reviewer.markRequested();
        boolean isRequestedAfterMarkRequested = reviewer.isRequested();

        // then
        assertAll(
                () -> assertThat(isRequestedAfterReviewed).isFalse(),
                () -> assertThat(isRequestedAfterMarkRequested).isTrue()
        );
    }
}
