package com.slack.bot.application.interactivity.block;

import com.slack.bot.application.interactivity.block.dto.BlockActionCommandDto;
import com.slack.bot.application.interactivity.block.dto.BlockActionOutcomeDto;

public interface BlockActionHandler {

    BlockActionOutcomeDto handle(BlockActionCommandDto command);
}
