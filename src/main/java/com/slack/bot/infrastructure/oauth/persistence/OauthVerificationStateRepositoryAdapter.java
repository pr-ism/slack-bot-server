package com.slack.bot.infrastructure.oauth.persistence;

import static com.slack.bot.domain.oauth.QOauthVerificationState.oauthVerificationState;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.domain.oauth.OauthVerificationState;
import com.slack.bot.domain.oauth.repository.OauthVerificationStateRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class OauthVerificationStateRepositoryAdapter implements OauthVerificationStateRepository {

    private final EntityManager entityManager;
    private final JPAQueryFactory queryFactory;
    private final JpaOauthVerificationStateRepository jpaOauthVerificationStateRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<OauthVerificationState> findByState(String state) {
        return jpaOauthVerificationStateRepository.findByState(state);
    }

    @Override
    @Transactional
    public void save(OauthVerificationState oauthVerificationState) {
        jpaOauthVerificationStateRepository.save(oauthVerificationState);
    }

    @Override
    @Transactional
    public void delete(OauthVerificationState oauthVerificationState) {
        jpaOauthVerificationStateRepository.delete(oauthVerificationState);
    }

    @Override
    @Transactional
    public void deleteAllExpired(LocalDateTime cutoff) {
        queryFactory.delete(oauthVerificationState)
                    .where(oauthVerificationState.expiresAt.loe(cutoff))
                    .execute();

        entityManager.flush();
        entityManager.clear();
    }
}
