package com.slack.bot.application.interaction.block.handler;

import com.slack.bot.application.interaction.block.BlockActionHandler;
import com.slack.bot.application.interaction.block.dto.BlockActionCommandDto;
import com.slack.bot.application.interaction.block.dto.BlockActionOutcomeDto;
import com.slack.bot.application.interaction.workflow.ReservationCommandWorkflow;
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
