package com.slack.bot.application.interactivity;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.interactivity.block.BlockActionRouter;
import com.slack.bot.application.interactivity.block.dto.BlockActionContextDto;
import com.slack.bot.application.interactivity.block.dto.BlockActionHandlingResultDto;
import com.slack.bot.application.interactivity.block.dto.BlockActionOutcomeDto;
import com.slack.bot.application.interactivity.notification.ReviewReservationNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class BlockActionInteractionService {

    private final BlockActionRouter blockActionRouter;
    private final ReviewReservationNotifier reservationNotifier;

    void handle(JsonNode payload) {
        blockActionRouter.route(payload)
                         .ifPresent(result -> processResult(result));
    }

    private void processResult(BlockActionHandlingResultDto resultContext) {
        BlockActionContextDto context = resultContext.context();
        BlockActionOutcomeDto outcome = resultContext.outcome();

        if (outcome.hasDuplicateReservation()) {
            reservationNotifier.sendDuplicateReservationNoticeToDmAndEphemeral(
                    context.botToken(),
                    context.teamId(),
                    context.channelId(),
                    context.slackUserId(),
                    outcome.duplicateReservation()
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
