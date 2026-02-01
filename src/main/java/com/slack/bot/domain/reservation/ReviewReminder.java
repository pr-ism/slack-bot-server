package com.slack.bot.domain.reservation;

import com.slack.bot.domain.common.BaseTimeEntity;
import com.slack.bot.domain.reservation.vo.ReminderDestination;
import com.slack.bot.domain.reservation.vo.ReminderFiredTime;
import com.slack.bot.domain.reservation.vo.ReminderParticipants;
import com.slack.bot.domain.reservation.vo.ReminderPullRequest;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "review_reminders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewReminder extends BaseTimeEntity {

    private Long reservationId;

    private Instant scheduledAt;

    @Embedded
    private ReminderDestination destination;

    @Embedded
    private ReminderParticipants participants;

    @Embedded
    private ReminderPullRequest pullRequest;

    @Embedded
    private ReminderFiredTime firedTime;

    @Builder
    private ReviewReminder(
            Long reservationId,
            Instant scheduledAt,
            ReminderDestination destination,
            ReminderParticipants participants,
            ReminderPullRequest pullRequest,
            ReminderFiredTime firedTime
    ) {
        this.reservationId = reservationId;
        this.scheduledAt = scheduledAt;
        this.destination = destination;
        this.participants = participants;
        this.pullRequest = pullRequest;
        this.firedTime = resolveFiredTime(firedTime);
    }

    public void markFired(Instant firedAt) {
        this.firedTime = ReminderFiredTime.of(firedAt);
    }

    public boolean isFired() {
        return this.firedTime.isFired();
    }

    private ReminderFiredTime resolveFiredTime(ReminderFiredTime firedTime) {
        if (firedTime == null) {
            return ReminderFiredTime.notFired();
        }
        return firedTime;
    }
}
