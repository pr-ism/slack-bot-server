package com.slack.bot.application.interactivity.client;

import com.slack.bot.application.interactivity.box.out.exception.OutboxWorkspaceNotFoundException;
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
        return workspaceRepository.findByAccessToken(token)
                                  .orElseThrow(() -> OutboxWorkspaceNotFoundException.forToken(token))
                                  .getTeamId();
    }
}
