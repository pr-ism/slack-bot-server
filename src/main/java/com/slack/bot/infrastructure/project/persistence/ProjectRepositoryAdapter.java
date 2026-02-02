package com.slack.bot.infrastructure.project.persistence;

import static com.slack.bot.domain.project.QProject.project;

import com.slack.bot.domain.project.Project;
import com.slack.bot.domain.project.repository.ProjectRepository;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ProjectRepositoryAdapter implements ProjectRepository {

    private final JPAQueryFactory queryFactory;
    private final JpaProjectRepository jpaProjectRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Project> findByApiKey(String apiKey) {
        return jpaProjectRepository.findByApiKey(apiKey);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findIdByApiKey(String apiKey) {
        Long result = queryFactory.select(project.id)
                                  .from(project)
                                  .where(project.apiKey.eq(apiKey))
                                  .fetchOne();

        return Optional.ofNullable(result);
    }
}
