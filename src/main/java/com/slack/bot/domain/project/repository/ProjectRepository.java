package com.slack.bot.domain.project.repository;

import com.slack.bot.domain.project.Project;
import java.util.Optional;

public interface ProjectRepository {

    Optional<Project> findByApiKey(String apiKey);

    Optional<Long> findIdByApiKey(String apiKey);
}
