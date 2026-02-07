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

    public static BlockActionDispatcher create(
            BlockActionHandler claimMappingActionHandler,
            BlockActionHandler openReviewSchedulerActionHandler,
            BlockActionHandler changeReviewReservationActionHandler,
            BlockActionHandler cancelReviewReservationActionHandler
    ) {
        return new BlockActionDispatcher(
                createHandlerMap(
                        claimMappingActionHandler,
                        openReviewSchedulerActionHandler,
                        changeReviewReservationActionHandler,
                        cancelReviewReservationActionHandler
                )
        );
    }

    private static Map<BlockActionType, BlockActionHandler> createHandlerMap(
            BlockActionHandler claimMappingActionHandler,
            BlockActionHandler openReviewSchedulerActionHandler,
            BlockActionHandler changeReviewReservationActionHandler,
            BlockActionHandler cancelReviewReservationActionHandler
    ) {
        EnumMap<BlockActionType, BlockActionHandler> map = new EnumMap<>(BlockActionType.class);

        map.put(BlockActionType.CLAIM_PREFIX, claimMappingActionHandler);
        map.put(BlockActionType.OPEN_REVIEW_SCHEDULER, openReviewSchedulerActionHandler);
        map.put(BlockActionType.CHANGE_REVIEW_RESERVATION, changeReviewReservationActionHandler);
        map.put(BlockActionType.CANCEL_REVIEW_RESERVATION, cancelReviewReservationActionHandler);

        return map;
    }

    public BlockActionOutcomeDto dispatch(BlockActionCommandDto command) {
        BlockActionHandler handler = handlerMap.get(command.actionType());

        if (handler == null) {
            return BlockActionOutcomeDto.empty();
        }

        return handler.handle(command);
    }
}
