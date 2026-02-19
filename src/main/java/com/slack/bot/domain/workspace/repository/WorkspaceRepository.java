package com.slack.bot.domain.workspace.repository;

import com.slack.bot.domain.workspace.Workspace;
import java.util.Optional;

public interface WorkspaceRepository {

    void save(Workspace workspace);

    Optional<Workspace> findByTeamId(String teamId);

    Optional<Workspace> findByUserId(Long userId);

    Optional<Workspace> findByAccessToken(String accessToken);

    void deleteByTeamId(String teamId);
}
