package com.slack.bot.domain.reservation.vo;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReminderParticipants {

    private String pullRequestAuthorSlackId;
    private String reviewerSlackId;

    @Builder
    private ReminderParticipants(String pullRequestAuthorSlackId, String reviewerSlackId) {
        this.pullRequestAuthorSlackId = pullRequestAuthorSlackId;
        this.reviewerSlackId = reviewerSlackId;
    }
}
