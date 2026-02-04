package com.slack.bot.application.interactivity.reservation.dto;

import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import com.slack.bot.domain.reservation.vo.ReservationPullRequest;
import java.time.Instant;
import lombok.Builder;

@Builder
public record ReservationContextDto(
        ReviewScheduleMetaDto meta,
        String reviewerId,
        String token,
        Instant scheduledAt,
        String authorSlackId,
        boolean isReschedule,
        Long projectId,
        ReservationPullRequest reservationPullRequest,
        Long reservationId
) {

    public static final String UNKNOWN_AUTHOR_SLACK_ID = "UNKNOWN";

    public ReservationCommandDto toReservationCommand() {
        return ReservationCommandDto.builder()
                                    .reservationId(reservationId)
                                    .teamId(meta.teamId())
                                    .channelId(meta.channelId())
                                    .projectId(projectId)
                                    .reservationPullRequest(reservationPullRequest)
                                    .authorSlackId(authorSlackId)
                                    .reviewerSlackId(reviewerId)
                                    .scheduledAt(scheduledAt)
                                    .build();
    }
}
