package com.slack.bot.application.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.event.client.SlackEventApiClient;
import com.slack.bot.application.event.dto.ChannelInfoDto;
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
import org.springframework.transaction.support.TransactionTemplate;

@Component("memberJoinedEventHandler")
@RequiredArgsConstructor
public class MemberJoinedChannelEventHandler implements SlackEventHandler {

    private final ChannelRepository channelRepository;
    private final WorkspaceRepository workspaceRepository;
    private final SlackEventApiClient slackEventApiClient; // conversations.info 기능이 추가되어야 함
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

        // 4. [핵심 수정] Slack API를 통해 진짜 채널명 조회
        String realChannelName = fetchChannelName(workspace.getAccessToken(), eventPayload.channelId());

        // 5. DB 동기화 (조회한 채널명 사용)
        transactionTemplate.executeWithoutResult(status ->
                synchronizeChannel(eventPayload, realChannelName)
        );

        // 6. 메시지 전송
        sendMessage(workspace, eventPayload);
    }

    private String fetchChannelName(String accessToken, String channelId) {
        try {
            ChannelInfoDto channelInfoDto = slackEventApiClient.fetchChannelInfo(accessToken, channelId);

            return channelInfoDto.name();
        } catch (Exception e) {
            return "channel-" + channelId;
        }
    }

    private Workspace findWorkspace(MemberJoinedEventPayload eventPayload) {
        Workspace workspace = workspaceRepository.findByTeamId(eventPayload.teamId())
                                                 .orElseThrow(UnregisteredWorkspaceException::new);

        if (workspace.getBotUserId() == null) {
            throw new BotUserIdMissingException(workspace.getTeamId());
        }
        return workspace;
    }

    private boolean isNotBotUserId(MemberJoinedEventPayload eventPayload, Workspace workspace) {
        return !eventPayload.joinedUserId().equals(workspace.getBotUserId());
    }

    // 변경: channelName을 외부에서 주입받도록 수정
    private void synchronizeChannel(MemberJoinedEventPayload eventPayload, String fetchedChannelName) {
        channelRepository.findChannelInTeam(eventPayload.teamId(), eventPayload.channelId())
                         .ifPresentOrElse(
                                 existing -> existing.updateChannelName(fetchedChannelName),
                                 () -> saveChannel(eventPayload, fetchedChannelName)
                         );
    }

    // 변경: eventPayload의 null 이름 대신 fetchedChannelName 사용
    private void saveChannel(MemberJoinedEventPayload eventPayload, String fetchedChannelName) {
        Channel channel = Channel.builder()
                                 .teamId(eventPayload.teamId())
                                 .channelId(eventPayload.channelId())
                                 .channelName(fetchedChannelName) // <--- 여기서 실제 이름 저장
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
