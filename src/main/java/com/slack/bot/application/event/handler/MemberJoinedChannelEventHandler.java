package com.slack.bot.application.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.event.client.SlackEventApiClient;
import com.slack.bot.application.event.dto.ChannelNameWrapper;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component("memberJoinedEventHandler")
@RequiredArgsConstructor
public class MemberJoinedChannelEventHandler implements SlackEventHandler {

    private final ChannelRepository channelRepository;
    private final WorkspaceRepository workspaceRepository;
    private final SlackEventApiClient slackEventApiClient;
    private final MemberJoinedEventParser memberJoinedEventParser;
    private final EventMessageProperties eventMessageProperties;
    private final TransactionTemplate transactionTemplate;

    @Override
    @Async("slackEventExecutor")
    public void handle(JsonNode payload) {
        MemberJoinedEventPayload eventPayload = memberJoinedEventParser.parse(payload);
        Workspace workspace = findWorkspace(eventPayload);

        if (isNotBotUserId(eventPayload, workspace)) {
            return;
        }

        String realChannelName = fetchChannelName(workspace.getAccessToken(), eventPayload.channelId());

        transactionTemplate.executeWithoutResult(status -> synchronizeChannel(eventPayload, realChannelName));
        sendMessage(workspace, eventPayload);
    }

    private String fetchChannelName(String accessToken, String channelId) {
        try {
            ChannelNameWrapper channelNameWrapper = slackEventApiClient.fetchChannelInfo(accessToken, channelId);

            return channelNameWrapper.name();
        } catch (Exception ex) {
            log.info("채널 정보 조회 실패 : ", ex);

            return "channel-" + channelId;
        }
    }

    private Workspace findWorkspace(MemberJoinedEventPayload eventPayload) {
        Workspace workspace = workspaceRepository.findByTeamId(eventPayload.teamId())
                                                 .orElseThrow(() -> new UnregisteredWorkspaceException());

        if (workspace.getBotUserId() == null) {
            throw new BotUserIdMissingException(workspace.getTeamId());
        }
        return workspace;
    }

    private boolean isNotBotUserId(MemberJoinedEventPayload eventPayload, Workspace workspace) {
        return !eventPayload.joinedUserId().equals(workspace.getBotUserId());
    }

    private void synchronizeChannel(MemberJoinedEventPayload eventPayload, String fetchedChannelName) {
        channelRepository.findChannelInTeam(eventPayload.teamId(), eventPayload.channelId())
                         .ifPresentOrElse(
                                 existing -> existing.updateChannelName(fetchedChannelName),
                                 () -> saveChannel(eventPayload, fetchedChannelName)
                         );
    }

    private void saveChannel(MemberJoinedEventPayload eventPayload, String fetchedChannelName) {
        Channel channel = Channel.builder()
                                 .teamId(eventPayload.teamId())
                                 .channelId(eventPayload.channelId())
                                 .channelName(fetchedChannelName)
                                 .build();

        channelRepository.save(channel);
    }

    private void sendMessage(Workspace workspace, MemberJoinedEventPayload eventPayload) {
        slackEventApiClient.sendMessage(
                workspace.getAccessToken(),
                eventPayload.channelId(),
                eventMessageProperties.welcome()
        );
    }
}
