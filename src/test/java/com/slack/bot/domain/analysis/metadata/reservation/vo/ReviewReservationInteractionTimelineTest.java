package com.slack.bot.domain.analysis.metadata.reservation.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewReservationInteractionTimelineTest {

    @Test
    void 모든_시간이_비어_있는_기본_값을_생성한다() {
        // when
        ReviewReservationInteractionTimeline actual = ReviewReservationInteractionTimeline.defaults();

        // then
        assertAll(
                () -> assertThat(actual.getReviewScheduledAt()).isNull(),
                () -> assertThat(actual.getReviewTimeSelectedAt()).isNull(),
                () -> assertThat(actual.getPullRequestNotifiedAt()).isNull()
        );
    }

    @Test
    void 리뷰_예약_시각을_기록한다() {
        // given
        ReviewReservationInteractionTimeline timeline = ReviewReservationInteractionTimeline.defaults();
        Instant reviewScheduledAt = Instant.parse("2099-01-01T10:00:00Z");

        // when
        ReviewReservationInteractionTimeline actual = timeline.recordReviewScheduledAt(reviewScheduledAt);

        // then
        assertThat(actual.getReviewScheduledAt()).isEqualTo(reviewScheduledAt);
    }

    @Test
    void 리뷰_시간_선정_버튼_클릭_시각을_기록한다() {
        // given
        ReviewReservationInteractionTimeline timeline = ReviewReservationInteractionTimeline.defaults();
        Instant reviewTimeSelectedAt = Instant.parse("2099-01-01T09:00:00Z");

        // when
        ReviewReservationInteractionTimeline actual = timeline.recordReviewTimeSelectedAt(reviewTimeSelectedAt);

        // then
        assertThat(actual.getReviewTimeSelectedAt()).isEqualTo(reviewTimeSelectedAt);
    }

    @Test
    void pull_request_알림_발송_시각을_기록한다() {
        // given
        ReviewReservationInteractionTimeline timeline = ReviewReservationInteractionTimeline.defaults();
        Instant pullRequestNotifiedAt = Instant.parse("2099-01-01T09:30:00Z");

        // when
        ReviewReservationInteractionTimeline actual = timeline.recordPullRequestNotifiedAt(
                pullRequestNotifiedAt
        );

        // then
        assertThat(actual.getPullRequestNotifiedAt()).isEqualTo(pullRequestNotifiedAt);
    }

    @Test
    void 리뷰_예약_시각_초기화_시_시각이_비어_있을_수_없다() {
        // given
        ReviewReservationInteractionTimeline timeline = ReviewReservationInteractionTimeline.defaults();

        // when & then
        assertThatThrownBy(() -> timeline.recordReviewScheduledAt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("리뷰 예약 시각은 비어 있을 수 없습니다.");
    }

    @Test
    void 리뷰_시간_선정_버튼_클릭_시각_초기화_시_시각이_비어_있을_수_없다() {
        // given
        ReviewReservationInteractionTimeline timeline = ReviewReservationInteractionTimeline.defaults();

        // when & then
        assertThatThrownBy(() -> timeline.recordReviewTimeSelectedAt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("리뷰 시간 선정 버튼 클릭 시각은 비어 있을 수 없습니다.");
    }

    @Test
    void pull_request_알림_발송_시각_초기화_시_시각이_비어_있을_수_없다() {
        // given
        ReviewReservationInteractionTimeline timeline = ReviewReservationInteractionTimeline.defaults();

        // when & then
        assertThatThrownBy(() -> timeline.recordPullRequestNotifiedAt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pullRequest 알림 발송 시각은 비어 있을 수 없습니다.");
    }
}
