package com.slack.bot.application.oauth;

import com.slack.bot.application.oauth.dto.response.SlackTokenResponse;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SlackWorkspaceService {

    private final WorkspaceRepository workspaceRepository;

    @Transactional
    public void registerWorkspace(SlackTokenResponse tokenResponse) {
        workspaceRepository.findByTeamId(tokenResponse.teamId())
                           .ifPresentOrElse(
                                   workspace -> workspace.reconnect(tokenResponse.accessToken()),
                                   () -> workspaceRepository.save(tokenResponse.toEntity())
                           );

    }
}
