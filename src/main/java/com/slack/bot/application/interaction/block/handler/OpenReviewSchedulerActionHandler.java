package com.slack.bot.application.interaction.block.handler;

import com.slack.bot.application.interaction.block.BlockActionHandler;
import com.slack.bot.application.interaction.block.dto.BlockActionCommandDto;
import com.slack.bot.application.interaction.block.dto.BlockActionOutcomeDto;
import com.slack.bot.application.interaction.workflow.ReviewSchedulerWorkflow;
import com.slack.bot.domain.reservation.ReviewReservation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenReviewSchedulerActionHandler implements BlockActionHandler {

    private final ReviewSchedulerWorkflow reviewSchedulerWorkflow;

    @Override
    public BlockActionOutcomeDto handle(BlockActionCommandDto command) {
        ReviewReservation duplicate = reviewSchedulerWorkflow.handleOpenScheduler(
                command.payload(),
                command.action(),
                command.teamId(),
                command.channelId(),
                command.slackUserId(),
                command.botToken()
        ).orElse(null);
        return new BlockActionOutcomeDto(duplicate, null);
    }
}
