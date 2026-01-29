package com.slack.bot.application.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.domain.channel.repository.ChannelRepository;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AppUninstalledEventHandler implements SlackEventHandler {

    private final ChannelRepository channelRepository;
    private final WorkspaceRepository workspaceRepository;

    @Override
    @Transactional
    @Async("slackEventExecutor")
    public void handle(JsonNode payload) {
        String teamId = payload.path("team_id")
                               .asText();

        channelRepository.deleteByTeamId(teamId);
        workspaceRepository.deleteByTeamId(teamId);
    }
}
