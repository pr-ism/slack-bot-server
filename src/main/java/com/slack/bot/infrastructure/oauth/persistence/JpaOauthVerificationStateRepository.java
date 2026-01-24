package com.slack.bot.infrastructure.oauth.persistence;

import com.slack.bot.domain.oauth.OauthVerificationState;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface JpaOauthVerificationStateRepository extends CrudRepository<OauthVerificationState, Long> {

    Optional<OauthVerificationState> findByState(String state);
}
