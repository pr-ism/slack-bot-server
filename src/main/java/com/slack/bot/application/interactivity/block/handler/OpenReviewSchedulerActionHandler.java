package com.slack.bot.application.interactivity.block.handler;

import com.slack.bot.application.interactivity.block.BlockActionHandler;
import com.slack.bot.application.interactivity.block.dto.BlockActionCommandDto;
import com.slack.bot.application.interactivity.block.dto.BlockActionOutcomeDto;
import com.slack.bot.application.interactivity.workflow.ReviewSchedulerWorkflow;
import com.slack.bot.domain.reservation.ReviewReservation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenReviewSchedulerActionHandler implements BlockActionHandler {

    private final ReviewSchedulerWorkflow reviewSchedulerUseCase;

    @Override
    public BlockActionOutcomeDto handle(BlockActionCommandDto command) {
        ReviewReservation duplicate = reviewSchedulerUseCase.handleOpenScheduler(
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
