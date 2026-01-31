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
public class ReminderPullRequest {

    private String pullRequestUrl;
    private String pullRequestTitle;

    @Builder
    private ReminderPullRequest(String pullRequestUrl, String pullRequestTitle) {
        this.pullRequestUrl = pullRequestUrl;
        this.pullRequestTitle = pullRequestTitle;
    }
}
