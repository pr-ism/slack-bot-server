package com.slack.bot.application.round;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.slack.bot.application.review.dto.request.ReviewAssignmentRequest;
import com.slack.bot.application.round.dto.ReviewRoundRegistrationResultDto;
import com.slack.bot.domain.round.PullRequestRound;
import com.slack.bot.domain.round.RoundReviewer;
import com.slack.bot.domain.round.repository.PullRequestRoundRepository;
import com.slack.bot.domain.round.repository.RoundReviewerRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewRequestRoundCoordinatorUnitTest {

    @Mock
    PullRequestRoundRepository pullRequestRoundRepository;

    @Mock
    RoundReviewerRepository roundReviewerRepository;

    @Test
    void apiKey가_비어있으면_register는_예외를_던진다() {
        // given
        ReviewRequestRoundCoordinator coordinator = new ReviewRequestRoundCoordinator(
                pullRequestRoundRepository,
                roundReviewerRepository
        );

        // when & then
        assertThatThrownBy(() -> coordinator.register(" ", validRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("apiKey는 비어 있을 수 없습니다.");
    }

    @Test
    void request가_null이면_register는_예외를_던진다() {
        // given
        ReviewRequestRoundCoordinator coordinator = new ReviewRequestRoundCoordinator(
                pullRequestRoundRepository,
                roundReviewerRepository
        );

        // when & then
        assertThatThrownBy(() -> coordinator.register("api-key", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("request는 비어 있을 수 없습니다.");
    }

    @Test
    void githubPullRequestId가_유효하지_않으면_register는_예외를_던진다() {
        // given
        ReviewRequestRoundCoordinator coordinator = new ReviewRequestRoundCoordinator(
                pullRequestRoundRepository,
                roundReviewerRepository
        );

        ReviewAssignmentRequest invalid = new ReviewAssignmentRequest(
                "repo",
                0L,
                1,
                "title",
                "url",
                "author",
                "hash-1",
                List.of("reviewer"),
                List.of()
        );

        // when & then
        assertThatThrownBy(() -> coordinator.register("api-key", invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("githubPullRequestId는 비어 있을 수 없습니다.");
    }

    @Test
    void startCommitHash가_유효하지_않으면_register는_예외를_던진다() {
        // given
        ReviewRequestRoundCoordinator coordinator = new ReviewRequestRoundCoordinator(
                pullRequestRoundRepository,
                roundReviewerRepository
        );

        ReviewAssignmentRequest invalid = new ReviewAssignmentRequest(
                "repo",
                1L,
                1,
                "title",
                "url",
                "author",
                " ",
                List.of("reviewer"),
                List.of()
        );

        // when & then
        assertThatThrownBy(() -> coordinator.register("api-key", invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("startCommitHash는 비어 있을 수 없습니다.");
    }

    @Test
    void 라운드_생성시_중복키_예외가_나면_기존_라운드를_조회해_복구한다() {
        // given
        ReviewRequestRoundCoordinator coordinator = new ReviewRequestRoundCoordinator(
                pullRequestRoundRepository,
                roundReviewerRepository
        );

        ReviewAssignmentRequest request = new ReviewAssignmentRequest(
                "repo",
                11L,
                1,
                "title",
                "url",
                "author",
                "hash-1",
                List.of(),
                List.of()
        );
        PullRequestRound existingRound = PullRequestRound.create("api-key", 11L, 1, "hash-1");

        when(pullRequestRoundRepository.findLatestRound("api-key", 11L))
                .thenReturn(Optional.empty());
        doThrow(new DataIntegrityViolationException("duplicate"))
                .when(pullRequestRoundRepository)
                .save(any(PullRequestRound.class));
        when(pullRequestRoundRepository.findRoundByStartCommitHash("api-key", 11L, "hash-1"))
                .thenReturn(Optional.of(existingRound));

        // when
        ReviewRoundRegistrationResultDto result = coordinator.register("api-key", request);

        // then
        assertAll(
                () -> assertThat(result.batchKey()).isEqualTo("api-key:11:1"),
                () -> assertThat(result.roundNumber()).isEqualTo(1),
                () -> assertThat(result.shouldNotify()).isFalse()
        );
        verify(pullRequestRoundRepository).findRoundByStartCommitHash(
                "api-key",
                11L,
                "hash-1"
        );
    }

    @Test
    void 라운드_복구조회도_없으면_중복키_예외를_그대로_전파한다() {
        // given
        ReviewRequestRoundCoordinator coordinator = new ReviewRequestRoundCoordinator(
                pullRequestRoundRepository,
                roundReviewerRepository
        );

        ReviewAssignmentRequest request = new ReviewAssignmentRequest(
                "repo",
                22L,
                1,
                "title",
                "url",
                "author",
                "hash-2",
                List.of(),
                List.of()
        );
        DataIntegrityViolationException duplicateKeyException = new DataIntegrityViolationException("duplicate");

        when(pullRequestRoundRepository.findLatestRound("api-key", 22L))
                .thenReturn(Optional.empty());
        doThrow(duplicateKeyException)
                .when(pullRequestRoundRepository)
                .save(any(PullRequestRound.class));
        when(pullRequestRoundRepository.findRoundByStartCommitHash("api-key", 22L, "hash-2"))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> coordinator.register("api-key", request))
                .isSameAs(duplicateKeyException);
    }

    @Test
    void 리뷰어_복구시_REVIEWED_기존_리뷰어는_REQUESTED로_전환된다() {
        // given
        ReviewRequestRoundCoordinator coordinator = new ReviewRequestRoundCoordinator(
                pullRequestRoundRepository,
                roundReviewerRepository
        );

        PullRequestRound currentRound = PullRequestRound.create("api-key", 33L, 1, "hash-3");
        ReviewAssignmentRequest request = new ReviewAssignmentRequest(
                "repo",
                33L,
                1,
                "title",
                "url",
                "author",
                "hash-3",
                List.of("reviewer-1"),
                List.of()
        );
        RoundReviewer reviewedReviewer = RoundReviewer.requested(1L, "reviewer-1");
        reviewedReviewer.markReviewed();

        when(pullRequestRoundRepository.findLatestRound("api-key", 33L))
                .thenReturn(Optional.of(currentRound));
        when(roundReviewerRepository.findReviewerInRound(null, "reviewer-1"))
                .thenReturn(Optional.of(reviewedReviewer));

        // when
        ReviewRoundRegistrationResultDto result = coordinator.register("api-key", request);

        // then
        assertAll(
                () -> assertThat(result.reviewersToMention()).containsExactly("reviewer-1"),
                () -> verify(roundReviewerRepository).save(reviewedReviewer)
        );
    }

    @Test
    void pendingReviewers의_null_공백은_제외되고_trim된_아이디만_처리된다() {
        // given
        ReviewRequestRoundCoordinator coordinator = new ReviewRequestRoundCoordinator(
                pullRequestRoundRepository,
                roundReviewerRepository
        );

        PullRequestRound currentRound = PullRequestRound.create("api-key", 44L, 1, "hash-4");
        ReviewAssignmentRequest request = new ReviewAssignmentRequest(
                "repo",
                44L,
                1,
                "title",
                "url",
                "author",
                "hash-4",
                Arrays.asList(null, " ", " reviewer-1 ", "reviewer-1"),
                List.of()
        );

        when(pullRequestRoundRepository.findLatestRound("api-key", 44L))
                .thenReturn(Optional.of(currentRound));
        RoundReviewer requestedReviewer = RoundReviewer.requested(1L, "reviewer-1");
        when(roundReviewerRepository.findReviewerInRound(null, "reviewer-1"))
                .thenReturn(Optional.of(requestedReviewer));

        // when
        ReviewRoundRegistrationResultDto result = coordinator.register("api-key", request);

        // then
        assertThat(result.reviewersToMention()).isEmpty();
    }

    private ReviewAssignmentRequest validRequest() {
        return new ReviewAssignmentRequest(
                "repo",
                1L,
                1,
                "title",
                "url",
                "author",
                "hash-1",
                List.of("reviewer"),
                List.of()
        );
    }
}
