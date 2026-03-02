package com.slack.bot.application.round;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.review.dto.request.ReviewAssignmentRequest;
import com.slack.bot.application.round.dto.ReviewRoundRegistrationResultDto;
import com.slack.bot.domain.round.PullRequestRound;
import com.slack.bot.domain.round.RoundReviewer;
import com.slack.bot.domain.round.RoundReviewerState;
import com.slack.bot.domain.round.repository.PullRequestRoundRepository;
import com.slack.bot.domain.round.repository.RoundReviewerRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewRequestRoundCoordinatorTest {

    @Autowired
    ReviewRequestRoundCoordinator coordinator;

    @Autowired
    PullRequestRoundRepository pullRequestRoundRepository;

    @Autowired
    RoundReviewerRepository roundReviewerRepository;

    @Test
    void 같은_라운드에서_중복_리뷰요청은_알림대상이_아니다() {
        // given
        ReviewAssignmentRequest request = request(1000L, "commit-hash-1", List.of("reviewer-gh-1"));

        // when
        ReviewRoundRegistrationResultDto first = coordinator.register("api-key", request);
        ReviewRoundRegistrationResultDto second = coordinator.register("api-key", request);

        PullRequestRound round = pullRequestRoundRepository
                .findLatestRound("api-key", 1000L)
                .orElseThrow();
        List<RoundReviewer> reviewers = roundReviewerRepository.findAll();

        // then
        assertAll(
                () -> assertThat(first.shouldNotify()).isTrue(),
                () -> assertThat(first.reviewersToMention()).containsExactly("reviewer-gh-1"),
                () -> assertThat(second.shouldNotify()).isFalse(),
                () -> assertThat(second.reviewersToMention()).isEmpty(),
                () -> assertThat(round.getRoundNumber()).isEqualTo(1),
                () -> assertThat(reviewers).hasSize(1)
        );
    }

    @Test
    void startCommitHash가_달라져도_이전_라운드에서_이미_pending인_리뷰어는_재멘션하지_않는다() {
        // given
        ReviewAssignmentRequest roundOne = request(2000L, "commit-hash-1", List.of("reviewer-gh-1"));
        ReviewAssignmentRequest roundTwo = request(2000L, "commit-hash-2", List.of("reviewer-gh-1"));

        // when
        ReviewRoundRegistrationResultDto first = coordinator.register("api-key", roundOne);
        ReviewRoundRegistrationResultDto second = coordinator.register("api-key", roundTwo);

        List<PullRequestRound> rounds = pullRequestRoundRepository.findAll();
        List<RoundReviewer> reviewers = roundReviewerRepository.findAll();

        // then
        assertAll(
                () -> assertThat(first.shouldNotify()).isTrue(),
                () -> assertThat(first.reviewersToMention()).containsExactly("reviewer-gh-1"),
                () -> assertThat(second.shouldNotify()).isFalse(),
                () -> assertThat(second.reviewersToMention()).isEmpty(),
                () -> assertThat(rounds).hasSize(2),
                () -> assertThat(reviewers).hasSize(2)
        );
    }

    @Test
    void 같은_라운드에서_리뷰어가_완료되었다가_다시_요청되면_REQUESTED로_복구되어_알림대상이_된다() {
        // given
        ReviewAssignmentRequest initial = request(3000L, "commit-hash-1", List.of("reviewer-gh-1"));
        ReviewAssignmentRequest reviewed = reviewed(3000L, "commit-hash-1", List.of("reviewer-gh-1"));
        ReviewAssignmentRequest requestedAgain = request(3000L, "commit-hash-1", List.of("reviewer-gh-1"));

        // when
        coordinator.register("api-key", initial);
        coordinator.register("api-key", reviewed);
        ReviewRoundRegistrationResultDto result = coordinator.register("api-key", requestedAgain);
        RoundReviewer reviewer = roundReviewerRepository.findAll().getFirst();

        // then
        assertAll(
                () -> assertThat(result.shouldNotify()).isTrue(),
                () -> assertThat(result.reviewersToMention()).containsExactly("reviewer-gh-1"),
                () -> assertThat(reviewer.getState()).isEqualTo(RoundReviewerState.REQUESTED)
        );
    }

    @Test
    void 동일_startCommitHash가_여러_라운드에_있어도_최신_라운드를_조회한다() {
        // given
        coordinator.register("api-key", request(4000L, "commit-hash-1", List.of("reviewer-gh-1")));
        coordinator.register("api-key", request(4000L, "commit-hash-2", List.of("reviewer-gh-1")));
        coordinator.register("api-key", request(4000L, "commit-hash-1", List.of("reviewer-gh-1")));

        // when
        PullRequestRound round = pullRequestRoundRepository
                .findRoundByStartCommitHash("api-key", 4000L, "commit-hash-1")
                .orElseThrow();

        // then
        assertThat(round.getRoundNumber()).isEqualTo(3);
    }

    private ReviewAssignmentRequest request(
            Long githubPullRequestId,
            String startCommitHash,
            List<String> pendingReviewers
    ) {
        return new ReviewAssignmentRequest(
                "repo",
                githubPullRequestId,
                1,
                "title",
                "https://github.com/org/repo/pull/1",
                "author",
                startCommitHash,
                pendingReviewers,
                List.of()
        );
    }

    private ReviewAssignmentRequest reviewed(
            Long githubPullRequestId,
            String startCommitHash,
            List<String> reviewedReviewers
    ) {
        return new ReviewAssignmentRequest(
                "repo",
                githubPullRequestId,
                1,
                "title",
                "https://github.com/org/repo/pull/1",
                "author",
                startCommitHash,
                reviewedReviewers,
                reviewedReviewers
        );
    }
}
