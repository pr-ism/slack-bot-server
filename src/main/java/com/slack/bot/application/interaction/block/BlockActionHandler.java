package com.slack.bot.application.interaction.block;

import com.slack.bot.application.interaction.block.dto.BlockActionCommandDto;
import com.slack.bot.application.interaction.block.dto.BlockActionOutcomeDto;

public interface BlockActionHandler {

    BlockActionOutcomeDto handle(BlockActionCommandDto command);
}
