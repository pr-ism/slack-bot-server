package com.slack.bot.domain.workspace.repository;

import com.slack.bot.domain.workspace.Workspace;
import java.util.Optional;

public interface WorkspaceRepository {

    void save(Workspace workspace);

    Optional<Workspace> findByTeamId(String teamId);

    Optional<Workspace> findByAccessToken(String accessToken);

    Optional<Workspace> findByUserId(Long userId);

    void deleteByTeamId(String teamId);
}
