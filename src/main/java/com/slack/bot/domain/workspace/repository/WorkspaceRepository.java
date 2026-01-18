package com.slack.bot.domain.workspace.repository;

import com.slack.bot.domain.workspace.Workspace;

public interface WorkspaceRepository {

    void save(Workspace workspace);
}
