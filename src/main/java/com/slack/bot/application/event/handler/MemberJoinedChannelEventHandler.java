package com.slack.bot.application.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.event.client.SlackEventApiClient;
import com.slack.bot.application.event.handler.exception.BotUserIdMissingException;
import com.slack.bot.application.event.handler.exception.UnregisteredWorkspaceException;
import com.slack.bot.application.event.parser.MemberJoinedEventParser;
import com.slack.bot.application.event.parser.dto.MemberJoinedEventPayload;
import com.slack.bot.domain.channel.Channel;
import com.slack.bot.domain.channel.repository.ChannelRepository;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import com.slack.bot.global.config.properties.EventMessageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MemberJoinedChannelEventHandler implements SlackEventHandler {

    private final ChannelRepository channelRepository;
    private final WorkspaceRepository workspaceRepository;
    private final SlackEventApiClient slackEventApiClient;
    private final MemberJoinedEventParser memberJoinedEventParser;
    private final EventMessageProperties eventMessageProperties;

    @Override
    @Transactional
    @Async("slackEventExecutor")
    public void handle(JsonNode payload) {
        MemberJoinedEventPayload eventPayload = parseEventPayload(payload);
        Workspace workspace = findWorkspace(eventPayload);
        validateBotUserId(workspace);

        if (isNotBotUserId(eventPayload, workspace)) {
            return;
        }

        synchronizeChannel(eventPayload);
        sendMessage(workspace, eventPayload);
    }

    private MemberJoinedEventPayload parseEventPayload(JsonNode payload) {
        return memberJoinedEventParser.parse(payload);
    }

    private Workspace findWorkspace(MemberJoinedEventPayload eventPayload) {
        return workspaceRepository.findByTeamId(eventPayload.teamId())
                                  .orElseThrow(() -> new UnregisteredWorkspaceException());
    }

    private void saveChannel(MemberJoinedEventPayload eventPayload) {
        Channel channel = Channel.builder()
                                 .teamId(eventPayload.teamId())
                                 .channelId(eventPayload.channelId())
                                 .channelName(eventPayload.channelName())
                                 .build();

        channelRepository.save(channel);
    }

    private void validateBotUserId(Workspace workspace) {
        if (workspace.getBotUserId() == null) {
            throw new BotUserIdMissingException(workspace.getTeamId());
        }
    }

    private boolean isNotBotUserId(MemberJoinedEventPayload eventPayload, Workspace workspace) {
        return !eventPayload.joinedUserId().equals(workspace.getBotUserId());
    }

    private void synchronizeChannel(MemberJoinedEventPayload eventPayload) {
        channelRepository.findChannelInTeam(eventPayload.teamId(), eventPayload.channelId())
                         .ifPresentOrElse(
                                 existing -> existing.updateChannelName(eventPayload.channelName()),
                                 () -> saveChannel(eventPayload)
                         );
    }

    private void sendMessage(Workspace workspace, MemberJoinedEventPayload eventPayload) {
        slackEventApiClient.sendMessage(
                workspace.getAccessToken(),
                eventPayload.channelId(),
                eventMessageProperties.welcome()
        );
    }
}
