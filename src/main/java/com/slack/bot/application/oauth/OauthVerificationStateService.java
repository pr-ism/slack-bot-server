package com.slack.bot.application.oauth;

import com.slack.bot.application.oauth.exception.ExpiredSlackOauthStateException;
import com.slack.bot.application.oauth.exception.SlackOauthStateNotFoundException;
import com.slack.bot.domain.oauth.OauthVerificationState;
import com.slack.bot.domain.oauth.repository.OauthVerificationStateRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OauthVerificationStateService {

    private static final Duration STATE_TTL = Duration.ofMinutes(5L);

    private final Clock clock;
    private final OauthVerificationStateRepository stateRepository;

    @Transactional
    public String generateSlackOauthState(Long userId) {
        String state = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now(clock)
                                               .plus(STATE_TTL);
        OauthVerificationState oauthVerificationState = OauthVerificationState.create(userId, state, expiresAt);

        stateRepository.save(oauthVerificationState);
        return state;
    }

    @Transactional
    public Long resolveUserIdByState(String state) {
        OauthVerificationState oauthState = stateRepository.findByState(state)
                                                           .orElseThrow(() -> new SlackOauthStateNotFoundException());
        LocalDateTime now = LocalDateTime.now(clock);

        if (oauthState.isExpired(now)) {
            throw new ExpiredSlackOauthStateException();
        }

        Long userId = oauthState.getUserId();

        stateRepository.delete(oauthState);
        return userId;
    }
}
