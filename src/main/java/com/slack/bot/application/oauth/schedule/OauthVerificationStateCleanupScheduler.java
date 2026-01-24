package com.slack.bot.application.oauth.schedule;

import com.slack.bot.domain.oauth.repository.OauthVerificationStateRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OauthVerificationStateCleanupScheduler {

    private final Clock clock;
    private final OauthVerificationStateRepository oauthVerificationStateRepository;

    @Scheduled(cron = "0 */30 * * * *")
    public void cleanExpiredStates() {
        LocalDateTime now = LocalDateTime.now(clock);

        oauthVerificationStateRepository.deleteAllExpired(now);
    }
}
