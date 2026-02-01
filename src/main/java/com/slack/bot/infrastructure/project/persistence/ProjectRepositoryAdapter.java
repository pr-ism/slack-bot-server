package com.slack.bot.infrastructure.project.persistence;

import com.slack.bot.domain.project.Project;
import com.slack.bot.domain.project.repository.ProjectRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ProjectRepositoryAdapter implements ProjectRepository {

    private final JpaProjectRepository projectRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Project> findByApiKey(String apiKey) {
        return projectRepository.findByApiKey(apiKey);
    }
}
