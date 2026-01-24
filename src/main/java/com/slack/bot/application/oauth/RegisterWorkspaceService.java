package com.slack.bot.application.oauth;

import com.slack.bot.application.oauth.dto.response.SlackTokenResponse;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterWorkspaceService {

    private final WorkspaceRepository workspaceRepository;

    @Transactional
    public void registerWorkspace(SlackTokenResponse tokenResponse, Long userId) {
        workspaceRepository.findByTeamId(tokenResponse.teamId())
                           .ifPresentOrElse(
                                   workspace -> workspace.reconnect(
                                           tokenResponse.accessToken(),
                                           tokenResponse.botUserId()
                                   ),
                                   () -> workspaceRepository.save(tokenResponse.toEntity(userId))
                           );

    }
}
