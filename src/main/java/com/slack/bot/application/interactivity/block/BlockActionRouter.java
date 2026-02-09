package com.slack.bot.application.interactivity.block;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.interactivity.block.dto.BlockActionCommandDto;
import com.slack.bot.application.interactivity.block.dto.BlockActionContextDto;
import com.slack.bot.application.interactivity.block.dto.BlockActionHandlingResultDto;
import com.slack.bot.application.interactivity.block.dto.BlockActionOutcomeDto;
import com.slack.bot.application.interactivity.block.factory.BlockActionContextFactory;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BlockActionRouter {

    private final BlockActionDispatcher blockActionDispatcher;
    private final BlockActionContextFactory blockActionContextFactory;

    public Optional<BlockActionHandlingResultDto> route(JsonNode payload) {
        return blockActionContextFactory.create(payload)
                                        .flatMap(context -> dispatch(payload, context));
    }

    private Optional<BlockActionHandlingResultDto> dispatch(JsonNode payload, BlockActionContextDto context) {
        BlockActionType actionType = BlockActionType.from(context.actionId());

        if (actionType.isUnknown()) {
            return Optional.empty();
        }

        BlockActionCommandDto command = BlockActionCommandDto.builder()
                                                             .payload(payload)
                                                             .action(context.action())
                                                             .actionId(context.actionId())
                                                             .actionType(actionType)
                                                             .teamId(context.teamId())
                                                             .channelId(context.channelId())
                                                             .slackUserId(context.slackUserId())
                                                             .botToken(context.botToken())
                                                             .build();
        BlockActionOutcomeDto outcome = blockActionDispatcher.dispatch(command);

        return Optional.of(BlockActionHandlingResultDto.of(context, outcome));
    }
}
