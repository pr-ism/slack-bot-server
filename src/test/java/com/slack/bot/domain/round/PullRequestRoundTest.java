package com.slack.bot.domain.round;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PullRequestRoundTest {

    @Test
    void 라운드_생성시_필드와_batch_key가_정상적으로_설정된다() {
        // when
        PullRequestRound round = PullRequestRound.create("api-key", 123L, 2, "commit-hash-2");

        // then
        assertAll(
                () -> assertThat(round.getRoundNumber()).isEqualTo(2),
                () -> assertThat(round.getGithubPullRequestId()).isEqualTo(123L),
                () -> assertThat(round.hasSameStartCommitHash("commit-hash-2")).isTrue(),
                () -> assertThat(round.batchKey()).isEqualTo("api-key:123:2")
        );
    }

    @Test
    void startCommitHash가_비어있으면_생성할_수_없다() {
        assertThatThrownBy(() -> PullRequestRound.create("api-key", 1L, 1, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("startCommitHash는 비어 있을 수 없습니다.");
    }

    @Test
    void apiKey가_비어있으면_생성할_수_없다() {
        assertThatThrownBy(() -> PullRequestRound.create(" ", 1L, 1, "commit-hash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("apiKey는 비어 있을 수 없습니다.");
    }

    @Test
    void githubPullRequestId가_1_미만이면_생성할_수_없다() {
        assertThatThrownBy(() -> PullRequestRound.create("api-key", 0L, 1, "commit-hash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("githubPullRequestId는 비어 있을 수 없습니다.");
    }

    @Test
    void roundNumber가_1_미만이면_생성할_수_없다() {
        assertThatThrownBy(() -> PullRequestRound.create("api-key", 1L, 0, "commit-hash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("roundNumber는 1 이상이어야 합니다.");
    }

    @Test
    void 비교용_startCommitHash가_비어있으면_예외가_발생한다() {
        PullRequestRound round = PullRequestRound.create("api-key", 1L, 1, "commit-hash");

        assertThatThrownBy(() -> round.hasSameStartCommitHash(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("startCommitHash는 비어 있을 수 없습니다.");
    }
}
