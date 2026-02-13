package com.slack.bot.domain.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewReservationInteractionTest {

    @Test
    void 리뷰_예약_슬랙_연동_엔티티를_초기화한다() {
        // when
        ReviewReservationInteraction actual = assertDoesNotThrow(
                () -> ReviewReservationInteraction.create("T1", 123L, 10L, "U1")
        );

        // then
        assertAll(
                () -> assertThat(actual.getTeamId()).isEqualTo("T1"),
                () -> assertThat(actual.getProjectId()).isEqualTo(123L),
                () -> assertThat(actual.getPullRequestId()).isEqualTo(10L),
                () -> assertThat(actual.getReviewerSlackId()).isEqualTo("U1"),
                () -> assertThat(actual.getInteractionTimeline().getReviewScheduledAt()).isNull(),
                () -> assertThat(actual.getInteractionTimeline().getReviewTimeSelectedAt()).isNull(),
                () -> assertThat(actual.getInteractionTimeline().getPullRequestNotifiedAt()).isNull(),
                () -> assertThat(actual.getInteractionCount().getScheduleCancelCount()).isZero(),
                () -> assertThat(actual.getInteractionCount().getScheduleChangeCount()).isZero(),
                () -> assertThat(actual.isReviewFulfilled()).isFalse()
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void Team_ID가_비어_있으면_생성할_수_없다(String teamId) {
        // when & then
        assertThatThrownBy(() -> ReviewReservationInteraction.create(teamId, 123L, 10L, "U1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teamId는 비어 있을 수 없습니다.");
    }

    @Test
    void Project_ID가_비어_있으면_생성할_수_없다() {
        // when & then
        assertThatThrownBy(() -> ReviewReservationInteraction.create("T1", null, 10L, "U1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("projectId는 비어 있을 수 없습니다.");
    }

    @Test
    void Pull_Request_ID가_비어_있으면_생성할_수_없다() {
        // when & then
        assertThatThrownBy(() -> ReviewReservationInteraction.create("T1", 123L, null, "U1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pullRequestId는 비어 있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void Reviewer_Slack_ID가_비어_있으면_생성할_수_없다(String reviewerSlackId) {
        // when & then
        assertThatThrownBy(() -> ReviewReservationInteraction.create("T1", 123L, 10L, reviewerSlackId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reviewerSlackId는 비어 있을 수 없습니다.");
    }

    @Test
    void 슬랙_연동_정보를_갱신한다() {
        // given
        ReviewReservationInteraction interaction = ReviewReservationInteraction.create("T1", 123L, 10L, "U1");
        Instant reviewTimeSelectedAt = Instant.parse("2099-01-01T09:00:00Z");
        Instant reviewScheduledAt = Instant.parse("2099-01-01T10:00:00Z");
        Instant pullRequestNotifiedAt = Instant.parse("2099-01-01T09:30:00Z");

        // when
        interaction.recordReviewTimeSelectedAt(reviewTimeSelectedAt);
        interaction.recordReviewScheduledAt(reviewScheduledAt);
        interaction.recordPullRequestNotifiedAt(pullRequestNotifiedAt);
        interaction.increaseScheduleCancelCount();
        interaction.increaseScheduleChangeCount();
        interaction.markReviewFulfilled();

        // then
        assertAll(
                () -> assertThat(interaction.getInteractionTimeline().getReviewTimeSelectedAt())
                        .isEqualTo(reviewTimeSelectedAt),
                () -> assertThat(interaction.getInteractionTimeline().getReviewScheduledAt())
                        .isEqualTo(reviewScheduledAt),
                () -> assertThat(interaction.getInteractionTimeline().getPullRequestNotifiedAt())
                        .isEqualTo(pullRequestNotifiedAt),
                () -> assertThat(interaction.getInteractionCount().getScheduleCancelCount()).isEqualTo(1),
                () -> assertThat(interaction.getInteractionCount().getScheduleChangeCount()).isEqualTo(1),
                () -> assertThat(interaction.isReviewFulfilled()).isTrue()
        );
    }
}
