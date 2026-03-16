package com.slack.bot.application.interaction.block.handler;

import com.slack.bot.application.interaction.block.BlockActionHandler;
import com.slack.bot.application.interaction.block.dto.BlockActionCommandDto;
import com.slack.bot.application.interaction.block.dto.BlockActionOutcomeDto;
import com.slack.bot.application.interaction.workflow.ClaimMappingWorkflow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClaimMappingActionHandler implements BlockActionHandler {

    private final ClaimMappingWorkflow claimMappingWorkflow;

    @Override
    public BlockActionOutcomeDto handle(BlockActionCommandDto command) {
        String githubId = command.action()
                                 .path("value")
                                 .asText();

        claimMappingWorkflow.handle(
                command.teamId(),
                command.slackUserId(),
                githubId,
                command.botToken(),
                command.channelId()
        );

        return BlockActionOutcomeDto.empty();
    }
}
