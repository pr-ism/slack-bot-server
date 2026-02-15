package com.slack.bot.domain.analysis.metadata.reservation.vo;

import jakarta.persistence.Embeddable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewReservationInteractionTimeline {

    private Instant reviewScheduledAt;
    private Instant reviewTimeSelectedAt;
    private Instant pullRequestNotifiedAt;

    public static ReviewReservationInteractionTimeline defaults() {
        return new ReviewReservationInteractionTimeline(null, null, null);
    }

    private ReviewReservationInteractionTimeline(
            Instant reviewScheduledAt,
            Instant reviewTimeSelectedAt,
            Instant pullRequestNotifiedAt
    ) {
        this.reviewScheduledAt = reviewScheduledAt;
        this.reviewTimeSelectedAt = reviewTimeSelectedAt;
        this.pullRequestNotifiedAt = pullRequestNotifiedAt;
    }

    public ReviewReservationInteractionTimeline recordReviewScheduledAt(Instant reviewScheduledAt) {
        validateReviewScheduledAt(reviewScheduledAt);

        return new ReviewReservationInteractionTimeline(
                reviewScheduledAt,
                this.reviewTimeSelectedAt,
                this.pullRequestNotifiedAt
        );
    }

    public ReviewReservationInteractionTimeline recordReviewTimeSelectedAt(Instant reviewTimeSelectedAt) {
        validateReviewTimeSelectedAt(reviewTimeSelectedAt);

        return new ReviewReservationInteractionTimeline(
                this.reviewScheduledAt,
                reviewTimeSelectedAt,
                this.pullRequestNotifiedAt
        );
    }

    public ReviewReservationInteractionTimeline recordPullRequestNotifiedAt(Instant pullRequestNotifiedAt) {
        validatePullRequestNotifiedAt(pullRequestNotifiedAt);

        return new ReviewReservationInteractionTimeline(
                this.reviewScheduledAt,
                this.reviewTimeSelectedAt,
                pullRequestNotifiedAt
        );
    }

    private void validateReviewScheduledAt(Instant reviewScheduledAt) {
        if (reviewScheduledAt == null) {
            throw new IllegalArgumentException("리뷰 예약 시각은 비어 있을 수 없습니다.");
        }
    }

    private void validateReviewTimeSelectedAt(Instant reviewTimeSelectedAt) {
        if (reviewTimeSelectedAt == null) {
            throw new IllegalArgumentException("리뷰 시간 선정 버튼 클릭 시각은 비어 있을 수 없습니다.");
        }
    }

    private void validatePullRequestNotifiedAt(Instant pullRequestNotifiedAt) {
        if (pullRequestNotifiedAt == null) {
            throw new IllegalArgumentException("pullRequest 알림 발송 시각은 비어 있을 수 없습니다.");
        }
    }
}
