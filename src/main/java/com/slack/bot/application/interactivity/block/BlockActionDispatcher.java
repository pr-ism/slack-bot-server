package com.slack.bot.application.interactivity.block;

import com.slack.bot.application.interactivity.block.dto.BlockActionCommandDto;
import com.slack.bot.application.interactivity.block.dto.BlockActionOutcomeDto;
import java.util.EnumMap;
import java.util.Map;

public class BlockActionDispatcher {

    private final Map<BlockActionType, BlockActionHandler> handlerMap;

    private BlockActionDispatcher(Map<BlockActionType, BlockActionHandler> handlerMap) {
        this.handlerMap = handlerMap;
    }

    public static BlockActionDispatcher create(Map<BlockActionType, BlockActionHandler> handlerMap) {
        return new BlockActionDispatcher(new EnumMap<>(handlerMap));
    }

    public BlockActionOutcomeDto dispatch(BlockActionCommandDto command) {
        BlockActionHandler handler = handlerMap.get(command.actionType());

        if (handler == null) {
            return BlockActionOutcomeDto.empty();
        }

        return handler.handle(command);
    }
}
