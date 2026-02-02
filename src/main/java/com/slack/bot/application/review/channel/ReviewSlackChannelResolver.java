package com.slack.bot.application.review.channel;

import com.slack.bot.application.review.channel.dto.SlackChannelDto;
import com.slack.bot.application.review.channel.exception.ReviewChannelResolveException;
import com.slack.bot.domain.channel.Channel;
import com.slack.bot.domain.channel.repository.ChannelRepository;
import com.slack.bot.domain.project.Project;
import com.slack.bot.domain.project.repository.ProjectRepository;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewSlackChannelResolver {

    private final ProjectRepository projectRepository;
    private final ChannelRepository channelRepository;
    private final WorkspaceRepository workspaceRepository;


    public SlackChannelDto resolve(String apiKey) {
        Project project = projectRepository.findByApiKey(apiKey)
                                .orElseThrow(() -> new ReviewChannelResolveException("유효하지 않은 API Key입니다."));
        Workspace workspace = workspaceRepository.findByUserId(project.getUserId())
                                .orElseThrow(() -> new ReviewChannelResolveException("워크스페이스 정보가 없습니다."));
        Channel channel = channelRepository.findByTeamId(workspace.getTeamId())
                                .orElseThrow(() -> new ReviewChannelResolveException("채널 정보가 없습니다."));

        return new SlackChannelDto(channel.getTeamId(), channel.getSlackChannelId(), workspace.getAccessToken());
    }
}
