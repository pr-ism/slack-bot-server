package com.slack.bot.infrastructure.project.persistence;

import com.slack.bot.domain.project.Project;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaProjectRepository extends ListCrudRepository<Project, Long> {

    Optional<Project> findByApiKey(String apiKey);
}

