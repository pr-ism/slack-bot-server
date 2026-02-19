package com.slack.bot.infrastructure.workspace.persistence;

import com.slack.bot.domain.workspace.Workspace;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface JpaWorkspaceRepository extends CrudRepository<Workspace, Long> {

    Optional<Workspace> findByTeamId(String teamId);

    Optional<Workspace> findByUserId(Long userId);

    Optional<Workspace> findByAccessToken(String accessToken);

    void deleteByTeamId(String teamId);
}
