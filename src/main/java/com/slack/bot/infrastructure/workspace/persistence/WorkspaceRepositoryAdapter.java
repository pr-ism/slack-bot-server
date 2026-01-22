package com.slack.bot.infrastructure.workspace.persistence;

import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WorkspaceRepositoryAdapter implements WorkspaceRepository {

    private final JpaWorkspaceRepository jpaWorkspaceRepository;

    @Override
    public void save(Workspace workspace) {
        jpaWorkspaceRepository.save(workspace);
    }

    @Override
    public Optional<Workspace> findByTeamId(String teamId) {
        return jpaWorkspaceRepository.findByTeamId(teamId);
    }
}
