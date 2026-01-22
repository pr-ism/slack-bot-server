package com.slack.bot.domain.oauth.repository;

import com.slack.bot.domain.oauth.OauthVerificationState;
import java.time.LocalDateTime;
import java.util.Optional;

public interface OauthVerificationStateRepository {

    Optional<OauthVerificationState> findByState(String state);

    void save(OauthVerificationState oauthVerificationState);

    void delete(OauthVerificationState oauthVerificationState);

    void deleteAllExpired(LocalDateTime cutoff);
}
