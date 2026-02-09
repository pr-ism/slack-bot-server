package com.slack.bot.application.interactivity.block.handler;

import com.slack.bot.application.interactivity.block.BlockActionHandler;
import com.slack.bot.application.interactivity.block.dto.BlockActionCommandDto;
import com.slack.bot.application.interactivity.block.dto.BlockActionOutcomeDto;
import com.slack.bot.application.interactivity.workflow.ReservationCommandWorkflow;
import com.slack.bot.domain.reservation.ReviewReservation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CancelReviewReservationActionHandler implements BlockActionHandler {

    private final ReservationCommandWorkflow reservationCommandWorkflow;

    @Override
    public BlockActionOutcomeDto handle(BlockActionCommandDto command) {
        ReviewReservation cancelled = reservationCommandWorkflow.handleCancel(
                command.action(),
                command.teamId(),
                command.channelId(),
                command.slackUserId(),
                command.botToken()
        ).orElse(null);

        return new BlockActionOutcomeDto(null, cancelled);
    }
}
