package com.slack.bot.application.interactivity.block.handler;

import com.slack.bot.application.interactivity.block.BlockActionHandler;
import com.slack.bot.application.interactivity.block.dto.BlockActionCommandDto;
import com.slack.bot.application.interactivity.block.dto.BlockActionOutcomeDto;
import com.slack.bot.application.interactivity.workflow.ReservationCommandWorkflow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChangeReviewReservationActionHandler implements BlockActionHandler {

    private final ReservationCommandWorkflow reservationCommandWorkflow;

    @Override
    public BlockActionOutcomeDto handle(BlockActionCommandDto command) {
        reservationCommandWorkflow.handleChange(
                command.payload(),
                command.action(),
                command.teamId(),
                command.channelId(),
                command.slackUserId(),
                command.botToken()
        );

        return BlockActionOutcomeDto.empty();
    }
}
