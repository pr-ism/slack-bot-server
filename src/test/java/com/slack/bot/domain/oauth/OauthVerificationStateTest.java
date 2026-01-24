package com.slack.bot.domain.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class OauthVerificationStateTest {

    @ParameterizedTest
    @NullAndEmptySource
    void state가_비어_있다면_OAuth_state를_초기화_할_수_없다(String state) {
        // given
        LocalDateTime validExpiresAt = LocalDateTime.now().plusMinutes(5);

        // when & then
        assertThatThrownBy(() -> OauthVerificationState.create(1L, state, validExpiresAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("state는 비어 있을 수 없습니다.");
    }

    @Test
    void userId가_비어_있다면_OAuth_state를_초기화_할_수_없다() {
        // given
        LocalDateTime validExpiresAt = LocalDateTime.now().plusMinutes(5);

        // when & then
        assertThatThrownBy(() -> OauthVerificationState.create(null, "valid-state", validExpiresAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("회원 ID는 비어 있을 수 없습니다.");
    }

    @Test
    void expiresAt이_비어_있다면_OAuth_state를_초기화_할_수_없다() {
        // when & then
        assertThatThrownBy(() -> OauthVerificationState.create(1L, "valid-state", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("만료 시간은 비어 있을 수 없습니다.");
    }

    @Test
    void 현재_시간이_만료_시간_이전이면_만료되지_않았다고_판단한다() {
        // given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime futureExpiresAt = now.plusMinutes(1);
        OauthVerificationState state = OauthVerificationState.create(1L, "valid-state", futureExpiresAt);

        // when
        boolean result = state.isExpired(now);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void 현재_시간이_만료_시간을_지났으면_만료되었다고_판단한다() {
        // given
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime pastExpiresAt = now.minusMinutes(1);
        OauthVerificationState state = OauthVerificationState.create(1L, "valid-state", pastExpiresAt);

        // when
        boolean result = state.isExpired(now);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void 현재_시간이_만료_시간과_정확히_일치하면_만료되었다고_판단한다() {
        // given
        LocalDateTime now = LocalDateTime.now();
        OauthVerificationState state = OauthVerificationState.create(1L, "valid-state", now);

        // when
        boolean result = state.isExpired(now);

        // then
        assertThat(result).isTrue();
    }
}
