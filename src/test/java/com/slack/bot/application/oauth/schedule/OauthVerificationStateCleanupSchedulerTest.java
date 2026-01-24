package com.slack.bot.application.oauth.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.domain.oauth.OauthVerificationState;
import com.slack.bot.domain.oauth.repository.OauthVerificationStateRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OauthVerificationStateCleanupSchedulerTest {

    @Autowired
    private OauthVerificationStateCleanupScheduler scheduler;

    @Autowired
    private OauthVerificationStateRepository stateRepository;

    @Autowired
    private Clock clock;

    @Test
    void 만료된_state를_삭제한다() {
        // given
        LocalDateTime now = LocalDateTime.now(clock);
        OauthVerificationState expired = OauthVerificationState.create(
                1L,
                "expired-state",
                now.minusMinutes(1L)
        );
        OauthVerificationState valid = OauthVerificationState.create(
                2L,
                "valid-state",
                now.plusMinutes(10L)
        );

        stateRepository.save(expired);
        stateRepository.save(valid);

        // when
        scheduler.cleanExpiredStates();

        // then
        assertAll(
                () -> assertThat(stateRepository.findByState("expired-state")).isEmpty(),
                () -> assertThat(stateRepository.findByState("valid-state")).isPresent()
        );
    }
}
