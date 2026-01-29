package com.slack.bot.infrastructure.workspace.persistence;

import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class WorkspaceRepositoryAdapter implements WorkspaceRepository {

    private final JpaWorkspaceRepository jpaWorkspaceRepository;

    @Override
    @Transactional
    public void save(Workspace workspace) {
        jpaWorkspaceRepository.save(workspace);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Workspace> findByTeamId(String teamId) {
        return jpaWorkspaceRepository.findByTeamId(teamId);
    }

    @Override
    @Transactional
    public void deleteByTeamId(String teamId) {
        jpaWorkspaceRepository.deleteByTeamId(teamId);
    }
}
