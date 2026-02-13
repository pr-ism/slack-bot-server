package com.slack.bot.domain.setting.vo;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
public class OptionalNotifications {

    private boolean reservationCanceledConfirmationEnabled;
    private boolean reservationChannelEphemeralEnabled;
    private boolean reviewReminderEnabled;
    private boolean pullRequestMentionEnabled;
    private boolean reviewCompletedEnabled;

    public static OptionalNotifications defaults() {
        return new OptionalNotifications(true, true, true, true, true);
    }

    private OptionalNotifications(
            boolean reservationCanceledConfirmationEnabled,
            boolean reservationChannelEphemeralEnabled,
            boolean reviewReminderEnabled,
            boolean pullRequestMentionEnabled,
            boolean reviewCompletedEnabled
    ) {
        this.reservationCanceledConfirmationEnabled = reservationCanceledConfirmationEnabled;
        this.reservationChannelEphemeralEnabled = reservationChannelEphemeralEnabled;
        this.reviewReminderEnabled = reviewReminderEnabled;
        this.pullRequestMentionEnabled = pullRequestMentionEnabled;
        this.reviewCompletedEnabled = reviewCompletedEnabled;
    }

    public OptionalNotifications updateReservationCanceledConfirmation(boolean enabled) {
        return new OptionalNotifications(
                enabled,
                reservationChannelEphemeralEnabled,
                reviewReminderEnabled,
                pullRequestMentionEnabled,
                reviewCompletedEnabled
        );
    }

    public OptionalNotifications updateReservationChannelEphemeral(boolean enabled) {
        return new OptionalNotifications(
                reservationCanceledConfirmationEnabled,
                enabled,
                reviewReminderEnabled,
                pullRequestMentionEnabled,
                reviewCompletedEnabled
        );
    }

    public OptionalNotifications updateReviewReminder(boolean enabled) {
        return new OptionalNotifications(
                reservationCanceledConfirmationEnabled,
                reservationChannelEphemeralEnabled,
                enabled,
                pullRequestMentionEnabled,
                reviewCompletedEnabled
        );
    }

    public OptionalNotifications updatePrMention(boolean enabled) {
        return new OptionalNotifications(
                reservationCanceledConfirmationEnabled,
                reservationChannelEphemeralEnabled,
                reviewReminderEnabled,
                enabled,
                reviewCompletedEnabled
        );
    }

    public OptionalNotifications updateReviewCompleted(boolean enabled) {
        return new OptionalNotifications(
                reservationCanceledConfirmationEnabled,
                reservationChannelEphemeralEnabled,
                reviewReminderEnabled,
                pullRequestMentionEnabled,
                enabled
        );
    }
}
