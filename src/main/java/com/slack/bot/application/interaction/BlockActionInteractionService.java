package com.slack.bot.application.interaction;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.interaction.block.BlockActionRouter;
import com.slack.bot.application.interaction.block.dto.BlockActionContextDto;
import com.slack.bot.application.interaction.block.dto.BlockActionHandlingResultDto;
import com.slack.bot.application.interaction.block.dto.BlockActionOutcomeDto;
import com.slack.bot.application.interaction.box.aop.EnqueueBlockActionInInbox;
import com.slack.bot.application.interaction.notification.ReviewReservationNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BlockActionInteractionService {

    private final BlockActionRouter blockActionRouter;
    private final ReviewReservationNotifier reservationNotifier;

    @EnqueueBlockActionInInbox
    public void handle(JsonNode payload) {
        blockActionRouter.route(payload)
                         .ifPresent(result -> processResult(result));
    }

    private void processResult(BlockActionHandlingResultDto resultContext) {
        BlockActionContextDto context = resultContext.context();
        BlockActionOutcomeDto outcome = resultContext.outcome();

        if (outcome.hasDuplicateReservation()) {
            reservationNotifier.sendReservationBlock(
                    context.botToken(),
                    context.teamId(),
                    context.channelId(),
                    context.slackUserId(),
                    outcome.duplicateReservation(),
                    "이미 이 PR 리뷰를 예약했습니다."
            );
        }
        if (outcome.hasCancelledReservation()) {
            reservationNotifier.sendCancellationMessage(
                    context.botToken(),
                    context.channelId(),
                    context.slackUserId(),
                    outcome.cancelledReservation()
            );
        }
    }
}
