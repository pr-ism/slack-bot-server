package com.slack.bot.infrastructure.member.persistence;

import static com.slack.bot.domain.member.QProjectMember.projectMember;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.domain.member.ProjectMember;
import com.slack.bot.domain.member.repository.ProjectMemberRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ProjectMemberRepositoryAdapter implements ProjectMemberRepository {

    private final JPAQueryFactory queryFactory;
    private final JpaProjectMemberRepository projectMemberRepository;

    @Override
    @Transactional
    public void save(ProjectMember projectMember) {
        projectMemberRepository.save(projectMember);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectMember> findById(Long id) {
        return projectMemberRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectMember> findBySlackUser(String teamId, String slackUserId) {
        ProjectMember result = queryFactory.selectFrom(projectMember)
                                           .where(
                                                   projectMember.teamId.eq(teamId),
                                                   projectMember.slackUserId.eq(slackUserId)
                                           )
                                           .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProjectMember> findByGithubUser(String teamId, String githubId) {
        ProjectMember result = queryFactory.selectFrom(projectMember)
                                           .where(
                                                   projectMember.teamId.eq(teamId),
                                                   projectMember.githubId.value.eq(githubId)
                                           )
                                           .fetchOne();

        return Optional.ofNullable(result);
    }
}
