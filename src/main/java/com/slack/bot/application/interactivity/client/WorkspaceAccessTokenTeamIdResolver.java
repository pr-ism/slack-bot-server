package com.slack.bot.application.interactivity.client;

import com.slack.bot.application.interactivity.box.out.exception.OutboxWorkspaceNotFoundException;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkspaceAccessTokenTeamIdResolver {

    private final WorkspaceRepository workspaceRepository;

    @Cacheable(cacheNames = "workspaceTeamIdByAccessToken", key = "#token")
    public String resolve(String token) {
        Workspace workspace = workspaceRepository.findByAccessToken(token)
                                                 .orElse(null);
        if (workspace == null) {
            throw OutboxWorkspaceNotFoundException.forToken(token);
        }

        return workspace.getTeamId();
    }
}
