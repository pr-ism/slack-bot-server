package com.slack.bot.application.interaction.reservation.dto;

import com.slack.bot.domain.reservation.vo.ReservationPullRequest;
import java.time.Instant;
import lombok.Builder;

@Builder
public record ReservationCommandDto(
        Long reservationId,
        String teamId,
        String channelId,
        Long projectId,
        ReservationPullRequest reservationPullRequest,
        String authorSlackId,
        String reviewerSlackId,
        Instant scheduledAt
) {
}
