package com.slack.bot.application.interactivity.block.dto;

public record BlockActionHandlingResultDto(
        BlockActionContextDto context,
        BlockActionOutcomeDto outcome
) {

    public static BlockActionHandlingResultDto of(BlockActionContextDto context, BlockActionOutcomeDto outcome) {
        return new BlockActionHandlingResultDto(context, outcome);
    }
}
