package com.slack.bot.application.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.oauth.exception.ExpiredSlackOauthStateException;
import com.slack.bot.application.oauth.exception.SlackOauthStateNotFoundException;
import com.slack.bot.domain.oauth.OauthVerificationState;
import com.slack.bot.domain.oauth.repository.OauthVerificationStateRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OauthVerificationStateServiceTest {

    @Autowired
    private OauthVerificationStateService oauthVerificationStateService;

    @Autowired
    private OauthVerificationStateRepository stateRepository;

    @Autowired
    private Clock clock;

    @Test
    void 회원_ID와_매핑된_state를_생성한다() {
        // when
        String stateToken = oauthVerificationStateService.generateSlackOauthState(42L);

        // then
        Optional<OauthVerificationState> actual = stateRepository.findByState(stateToken);

        assertAll(
                () -> assertThat(actual).isPresent(),
                () -> assertThat(actual.get().getUserId()).isEqualTo(42L),
                () -> assertThat(actual.get().getState()).isEqualTo(stateToken),
                () -> assertThat(actual.get().isExpired(LocalDateTime.now(clock))).isFalse()
        );
    }

    @Test
    void 정상적인_state라면_해당_state와_매핑된_회원_ID를_조회한다() {
        // given
        String stateToken = oauthVerificationStateService.generateSlackOauthState(100L);

        // when
        Long actual = oauthVerificationStateService.resolveUserIdByState(stateToken);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(100L),
                () -> assertThat(stateRepository.findByState(stateToken)).isEmpty()
        );
    }

    @Test
    void 매핑되지_않은_state로는_회원_ID를_조회할_수_없다() {
        // when & then
        assertThatThrownBy(() -> oauthVerificationStateService.resolveUserIdByState("invalid-token"))
                .isInstanceOf(SlackOauthStateNotFoundException.class)
                .hasMessage("존재하지 않는 state 입니다.");
    }

    @Test
    void 만료된_state로는_회원_ID를_조회할_수_없다() {
        // given
        LocalDateTime expiredAt = LocalDateTime.now(clock).minusMinutes(1);
        OauthVerificationState state = OauthVerificationState.create(99L, "expired-state", expiredAt);

        stateRepository.save(state);

        // when & then
        assertThatThrownBy(() -> oauthVerificationStateService.resolveUserIdByState("expired-state"))
                .isInstanceOf(ExpiredSlackOauthStateException.class)
                .hasMessage("만료된 state 입니다.");
    }
}
