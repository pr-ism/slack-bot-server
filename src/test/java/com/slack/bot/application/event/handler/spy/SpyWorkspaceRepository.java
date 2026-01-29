package com.slack.bot.application.event.handler.spy;

import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import java.util.Optional;
import lombok.Getter;

@Getter
public class SpyWorkspaceRepository implements WorkspaceRepository {

    private int deleteByTeamIdCallCount = 0;
    private String lastDeletedTeamId = null;

    @Override
    public void deleteByTeamId(String teamId) {
        this.deleteByTeamIdCallCount++;
        this.lastDeletedTeamId = teamId;
    }

    @Override
    public void save(Workspace workspace) {
    }

    @Override
    public Optional<Workspace> findByTeamId(String teamId) {
        return Optional.empty();
    }
}
