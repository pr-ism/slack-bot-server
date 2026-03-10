package com.slack.bot.application.round;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.review.dto.ReviewNotificationPayload;
import com.slack.bot.application.review.dto.request.ReviewAssignmentRequest;
import com.slack.bot.application.review.participant.ReviewParticipantFormatter;
import com.slack.bot.application.review.participant.dto.ReviewParticipantsDto;
import com.slack.bot.application.round.dto.ReviewRoundRegistrationResultDto;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewRoundMentionFlowIntegrationTest {

    @Autowired
    ReviewRequestRoundCoordinator coordinator;

    @Autowired
    ReviewParticipantFormatter reviewParticipantFormatter;

    @Test
    @Sql(scripts = "classpath:sql/fixtures/review/project_member_t1_mapped_two_reviewers.sql")
    void 처음에는_A_B_모두_멘션되고_A_rc_후_재요청에서는_A만_멘션되고_B는_display_name_평문이다() {
        // given
        String apiKey = "test-api-key";
        String startCommitHash = "commit-hash-1";

        ReviewAssignmentRequest firstReviewRequested = request(
                startCommitHash,
                List.of("reviewer-gh-1", "reviewer-gh-2"),
                List.of()
        );
        ReviewAssignmentRequest reviewerAReviewed = request(
                startCommitHash,
                List.of("reviewer-gh-2"),
                List.of("reviewer-gh-1")
        );
        ReviewAssignmentRequest reviewerARequestedAgain = request(
                startCommitHash,
                List.of("reviewer-gh-1", "reviewer-gh-2"),
                List.of()
        );

        // when
        ReviewRoundRegistrationResultDto firstResult = coordinator.register(apiKey, firstReviewRequested);
        ReviewParticipantsDto firstParticipants = reviewParticipantFormatter.format(
                "T1",
                ReviewNotificationPayload.of(firstReviewRequested, firstResult.reviewersToMention())
        );

        ReviewRoundRegistrationResultDto reviewedResult = coordinator.register(apiKey, reviewerAReviewed);

        ReviewRoundRegistrationResultDto secondResult = coordinator.register(apiKey, reviewerARequestedAgain);
        ReviewParticipantsDto secondParticipants = reviewParticipantFormatter.format(
                "T1",
                ReviewNotificationPayload.of(reviewerARequestedAgain, secondResult.reviewersToMention())
        );

        // then
        assertAll(
                () -> assertThat(firstResult.shouldNotify()).isTrue(),
                () -> assertThat(firstResult.reviewersToMention())
                        .containsExactly("reviewer-gh-1", "reviewer-gh-2"),
                () -> assertThat(firstParticipants.pendingReviewersText()).isEqualTo("<@U2>, <@U3>"),
                () -> assertThat(reviewedResult.shouldNotify()).isFalse(),
                () -> assertThat(secondResult.shouldNotify()).isTrue(),
                () -> assertThat(secondResult.reviewersToMention()).containsExactly("reviewer-gh-1"),
                () -> assertThat(secondParticipants.pendingReviewersText()).isEqualTo("<@U2>, 리뷰어2")
        );
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/review/project_member_t1_mapped_two_reviewers.sql")
    void 커밋이_바뀌어_새_라운드가_열려도_기존_pending_B는_재멘션되지_않고_A만_멘션된다() {
        // given
        String apiKey = "test-api-key";

        ReviewAssignmentRequest firstReviewRequested = request(
                "commit-hash-1",
                List.of("reviewer-gh-1", "reviewer-gh-2"),
                List.of()
        );
        ReviewAssignmentRequest reviewerAReviewed = request(
                "commit-hash-1",
                List.of("reviewer-gh-2"),
                List.of("reviewer-gh-1")
        );
        ReviewAssignmentRequest reviewerARequestedAgainOnNewCommit = request(
                "commit-hash-2",
                List.of("reviewer-gh-1", "reviewer-gh-2"),
                List.of()
        );

        // when
        ReviewRoundRegistrationResultDto firstResult = coordinator.register(apiKey, firstReviewRequested);
        ReviewRoundRegistrationResultDto reviewedResult = coordinator.register(apiKey, reviewerAReviewed);
        ReviewRoundRegistrationResultDto secondResult = coordinator.register(
                apiKey,
                reviewerARequestedAgainOnNewCommit
        );
        ReviewParticipantsDto secondParticipants = reviewParticipantFormatter.format(
                "T1",
                ReviewNotificationPayload.of(
                        reviewerARequestedAgainOnNewCommit,
                        secondResult.reviewersToMention()
                )
        );

        // then
        assertAll(
                () -> assertThat(firstResult.shouldNotify()).isTrue(),
                () -> assertThat(firstResult.reviewersToMention())
                        .containsExactly("reviewer-gh-1", "reviewer-gh-2"),
                () -> assertThat(reviewedResult.shouldNotify()).isFalse(),
                () -> assertThat(secondResult.shouldNotify()).isTrue(),
                () -> assertThat(secondResult.reviewersToMention()).containsExactly("reviewer-gh-1"),
                () -> assertThat(secondParticipants.pendingReviewersText()).isEqualTo("<@U2>, 리뷰어2")
        );
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/review/project_member_t1_mapped_two_reviewers.sql")
    void A_RC_이후_커밋추가와_재요청이_한번에_들어와도_A만_멘션되고_B는_평문으로_표시된다() {
        // given
        String apiKey = "test-api-key";

        ReviewAssignmentRequest firstReviewRequested = request(
                "commit-hash-1",
                List.of("reviewer-gh-1", "reviewer-gh-2"),
                List.of()
        );
        ReviewAssignmentRequest requestedAfterNewCommit = request(
                "commit-hash-2",
                List.of("reviewer-gh-1", "reviewer-gh-2"),
                List.of("reviewer-gh-1")
        );

        // when
        ReviewRoundRegistrationResultDto firstResult = coordinator.register(apiKey, firstReviewRequested);
        ReviewRoundRegistrationResultDto secondResult = coordinator.register(apiKey, requestedAfterNewCommit);
        ReviewParticipantsDto secondParticipants = reviewParticipantFormatter.format(
                "T1",
                ReviewNotificationPayload.of(requestedAfterNewCommit, secondResult.reviewersToMention())
        );

        // then
        assertAll(
                () -> assertThat(firstResult.reviewersToMention())
                        .containsExactly("reviewer-gh-1", "reviewer-gh-2"),
                () -> assertThat(secondResult.shouldNotify()).isTrue(),
                () -> assertThat(secondResult.reviewersToMention()).containsExactly("reviewer-gh-1"),
                () -> assertThat(secondParticipants.pendingReviewersText()).isEqualTo("<@U2>, 리뷰어2")
        );
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/review/project_member_t1_mapped_two_reviewers.sql")
    void A_B가_리뷰했어도_RC는_A만이면_새_커밋_재요청에서_A만_멘션된다() {
        // given
        String apiKey = "test-api-key";

        ReviewAssignmentRequest firstReviewRequested = request(
                "commit-hash-1",
                List.of("reviewer-gh-1", "reviewer-gh-2"),
                List.of()
        );
        // B approve는 reviewedReviewers에 포함하지 않는 입력 계약을 가정한다.
        ReviewAssignmentRequest reviewedWithOnlyAChangesRequested = request(
                "commit-hash-1",
                List.of("reviewer-gh-1", "reviewer-gh-2"),
                List.of("reviewer-gh-1")
        );
        ReviewAssignmentRequest requestedAgainOnNewCommit = request(
                "commit-hash-2",
                List.of("reviewer-gh-1", "reviewer-gh-2"),
                List.of()
        );

        // when
        coordinator.register(apiKey, firstReviewRequested);
        ReviewRoundRegistrationResultDto reviewedResult = coordinator.register(
                apiKey,
                reviewedWithOnlyAChangesRequested
        );
        ReviewRoundRegistrationResultDto secondResult = coordinator.register(apiKey, requestedAgainOnNewCommit);
        ReviewParticipantsDto secondParticipants = reviewParticipantFormatter.format(
                "T1",
                ReviewNotificationPayload.of(requestedAgainOnNewCommit, secondResult.reviewersToMention())
        );

        // then
        assertAll(
                () -> assertThat(reviewedResult.shouldNotify()).isFalse(),
                () -> assertThat(secondResult.shouldNotify()).isTrue(),
                () -> assertThat(secondResult.reviewersToMention()).containsExactly("reviewer-gh-1"),
                () -> assertThat(secondParticipants.pendingReviewersText()).isEqualTo("<@U2>, 리뷰어2")
        );
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/review/project_member_t1_mapped_two_reviewers.sql")
    void A_피드백_반영후_A만_멘션되고_이후_B_피드백_반영후에는_B만_멘션된다() {
        // given
        String apiKey = "test-api-key";

        ReviewAssignmentRequest firstReviewRequested = request(
                "commit-hash-1",
                List.of("reviewer-gh-1", "reviewer-gh-2"),
                List.of()
        );
        ReviewAssignmentRequest reviewerAReviewed = request(
                "commit-hash-1",
                List.of("reviewer-gh-2"),
                List.of("reviewer-gh-1")
        );
        ReviewAssignmentRequest reviewerARequestedAgainOnNewCommit = request(
                "commit-hash-2",
                List.of("reviewer-gh-1", "reviewer-gh-2"),
                List.of()
        );
        ReviewAssignmentRequest reviewerBReviewedOnLatest = request(
                "commit-hash-2",
                List.of("reviewer-gh-1"),
                List.of("reviewer-gh-2")
        );
        ReviewAssignmentRequest reviewerBRequestedAgainOnNewCommit = request(
                "commit-hash-3",
                List.of("reviewer-gh-1", "reviewer-gh-2"),
                List.of()
        );

        // when
        ReviewRoundRegistrationResultDto firstResult = coordinator.register(apiKey, firstReviewRequested);
        ReviewRoundRegistrationResultDto aReviewedResult = coordinator.register(apiKey, reviewerAReviewed);
        ReviewRoundRegistrationResultDto secondResult = coordinator.register(
                apiKey,
                reviewerARequestedAgainOnNewCommit
        );
        ReviewRoundRegistrationResultDto bReviewedResult = coordinator.register(apiKey, reviewerBReviewedOnLatest);
        ReviewRoundRegistrationResultDto thirdResult = coordinator.register(
                apiKey,
                reviewerBRequestedAgainOnNewCommit
        );
        ReviewParticipantsDto thirdParticipants = reviewParticipantFormatter.format(
                "T1",
                ReviewNotificationPayload.of(
                        reviewerBRequestedAgainOnNewCommit,
                        thirdResult.reviewersToMention()
                )
        );

        // then
        assertAll(
                () -> assertThat(firstResult.reviewersToMention())
                        .containsExactly("reviewer-gh-1", "reviewer-gh-2"),
                () -> assertThat(aReviewedResult.shouldNotify()).isFalse(),
                () -> assertThat(secondResult.reviewersToMention()).containsExactly("reviewer-gh-1"),
                () -> assertThat(bReviewedResult.shouldNotify()).isFalse(),
                () -> assertThat(thirdResult.reviewersToMention()).containsExactly("reviewer-gh-2"),
                () -> assertThat(thirdParticipants.pendingReviewersText()).isEqualTo("리뷰어1, <@U3>")
        );
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/review/project_member_t1_mapped_two_reviewers.sql")
    void A_B_모두_RC를_남긴_후_새_커밋에서_다시_요청하면_A_B_모두_멘션된다() {
        // given
        String apiKey = "test-api-key";

        ReviewAssignmentRequest firstReviewRequested = request(
                "commit-hash-1",
                List.of("reviewer-gh-1", "reviewer-gh-2"),
                List.of()
        );
        ReviewAssignmentRequest bothReviewedOnSameRound = request(
                "commit-hash-1",
                List.of("reviewer-gh-1", "reviewer-gh-2"),
                List.of("reviewer-gh-1", "reviewer-gh-2")
        );
        ReviewAssignmentRequest requestedAgainOnNewCommit = request(
                "commit-hash-2",
                List.of("reviewer-gh-1", "reviewer-gh-2"),
                List.of()
        );

        // when
        ReviewRoundRegistrationResultDto firstResult = coordinator.register(apiKey, firstReviewRequested);
        ReviewRoundRegistrationResultDto reviewedResult = coordinator.register(apiKey, bothReviewedOnSameRound);
        ReviewRoundRegistrationResultDto secondResult = coordinator.register(apiKey, requestedAgainOnNewCommit);
        ReviewParticipantsDto secondParticipants = reviewParticipantFormatter.format(
                "T1",
                ReviewNotificationPayload.of(requestedAgainOnNewCommit, secondResult.reviewersToMention())
        );

        // then
        assertAll(
                () -> assertThat(firstResult.reviewersToMention())
                        .containsExactly("reviewer-gh-1", "reviewer-gh-2"),
                () -> assertThat(reviewedResult.shouldNotify()).isFalse(),
                () -> assertThat(secondResult.shouldNotify()).isTrue(),
                () -> assertThat(secondResult.reviewersToMention())
                        .containsExactly("reviewer-gh-1", "reviewer-gh-2"),
                () -> assertThat(secondParticipants.pendingReviewersText()).isEqualTo("<@U2>, <@U3>")
        );
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/review/project_member_t1_mapped_three_reviewers.sql")
    void 요청_리뷰어가_아닌_팀장이_리뷰했어도_새_라운드에서는_함께_멘션된다() {
        // given
        String apiKey = "test-api-key";

        ReviewAssignmentRequest firstReviewRequested = request(
                "commit-hash-1",
                List.of("reviewer-gh-1", "reviewer-gh-2"),
                List.of()
        );
        ReviewAssignmentRequest managerReviewed = request(
                "commit-hash-1",
                List.of("reviewer-gh-1", "reviewer-gh-2"),
                List.of("manager-gh")
        );
        ReviewAssignmentRequest requestedAgainOnNewCommit = request(
                "commit-hash-2",
                List.of("reviewer-gh-1", "reviewer-gh-2"),
                List.of()
        );

        // when
        ReviewRoundRegistrationResultDto firstResult = coordinator.register(apiKey, firstReviewRequested);
        ReviewRoundRegistrationResultDto reviewedResult = coordinator.register(apiKey, managerReviewed);
        ReviewRoundRegistrationResultDto secondResult = coordinator.register(apiKey, requestedAgainOnNewCommit);
        ReviewParticipantsDto secondParticipants = reviewParticipantFormatter.format(
                "T1",
                ReviewNotificationPayload.of(requestedAgainOnNewCommit, secondResult.reviewersToMention())
        );

        // then
        assertAll(
                () -> assertThat(firstResult.reviewersToMention())
                        .containsExactly("reviewer-gh-1", "reviewer-gh-2"),
                () -> assertThat(reviewedResult.shouldNotify()).isFalse(),
                () -> assertThat(secondResult.shouldNotify()).isTrue(),
                () -> assertThat(secondResult.reviewersToMention()).containsExactly("manager-gh"),
                () -> assertThat(secondParticipants.pendingReviewersText()).isEqualTo("리뷰어1, 리뷰어2, <@U4>")
        );
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/review/project_member_t1_mapped_two_reviewers.sql")
    void pendingReviewers가_비어있으면_멘션_대상도_비어있다() {
        // given
        String apiKey = "test-api-key";
        ReviewAssignmentRequest emptyPendingRequest = request(
                "commit-hash-1",
                List.of(),
                List.of("reviewer-gh-1")
        );

        // when
        ReviewRoundRegistrationResultDto result = coordinator.register(apiKey, emptyPendingRequest);

        // then
        assertThat(result.shouldNotify()).isFalse();
    }

    private ReviewAssignmentRequest request(
            String startCommitHash,
            List<String> pendingReviewers,
            List<String> reviewedReviewers
    ) {
        return new ReviewAssignmentRequest(
                "repo",
                1000L,
                1,
                "title",
                "https://github.com/org/repo/pull/1",
                "author-gh",
                startCommitHash,
                pendingReviewers,
                reviewedReviewers
        );
    }
}
