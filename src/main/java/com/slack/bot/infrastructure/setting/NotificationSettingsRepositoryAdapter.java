package com.slack.bot.infrastructure.setting;

import static com.slack.bot.domain.member.QProjectMember.projectMember;
import static com.slack.bot.domain.setting.QNotificationSettings.notificationSettings;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.domain.setting.NotificationSettings;
import com.slack.bot.domain.setting.repository.NotificationSettingsRepository;
import com.slack.bot.infrastructure.common.MysqlDuplicateKeyDetector;
import com.slack.bot.infrastructure.setting.exception.NotificationSettingsCreationConflictException;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
@RequiredArgsConstructor
public class NotificationSettingsRepositoryAdapter implements NotificationSettingsRepository {

    private final EntityManager entityManager;
    private final JPAQueryFactory queryFactory;
    private final PlatformTransactionManager transactionManager;
    private final JpaNotificationSettings jpaNotificationSettings;
    private final MysqlDuplicateKeyDetector mysqlDuplicateKeyDetector;

    @Override
    public Optional<NotificationSettings> findByProjectMemberId(Long projectMemberId) {
        return jpaNotificationSettings.findByProjectMemberId(projectMemberId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NotificationSettings> findBySlackUser(String teamId, String slackUserId) {
        NotificationSettings result = queryFactory
                .selectFrom(notificationSettings)
                .innerJoin(projectMember)
                .on(projectMember.id.eq(notificationSettings.projectMemberId))
                .where(
                        projectMember.teamId.eq(teamId),
                        projectMember.slackUserId.eq(slackUserId)
                )
                .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public NotificationSettings save(NotificationSettings notificationSettings) {
        return jpaNotificationSettings.save(notificationSettings);
    }

    @Override
    public NotificationSettings saveOrFindOnDuplicate(NotificationSettings notificationSettings) {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);

        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        try {
            return requiresNew.execute(status -> jpaNotificationSettings.save(notificationSettings));
        } catch (DataIntegrityViolationException ex) {
            if (mysqlDuplicateKeyDetector.isNotDuplicateKey(ex)) {
                throw ex;
            }
            return requiresNew.execute(status -> {
                entityManager.clear();
                return jpaNotificationSettings.findByProjectMemberId(notificationSettings.getProjectMemberId())
                                              .orElseThrow(() -> new NotificationSettingsCreationConflictException(notificationSettings.getProjectMemberId(), ex));
            });
        }
    }
}
