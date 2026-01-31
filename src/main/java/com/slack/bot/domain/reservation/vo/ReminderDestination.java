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
public class ReminderDestination {

    private String teamId;
    private String channelId;

    @Builder
    private ReminderDestination(String teamId, String channelId) {
        this.teamId = teamId;
        this.channelId = channelId;
    }
}
