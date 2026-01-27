package com.slack.bot.domain.member.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class GithubIdTest {

    @Test
    void 깃허브_ID를_초기화한다() {
        // when & then
        GithubId githubId = assertDoesNotThrow(() -> GithubId.create("gildong"));

        assertThat(githubId.getValue()).isEqualTo("gildong");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 깃허브_ID가_비어_있으면_초기화할_수_없다(String value) {
        // when & then
        assertThatThrownBy(() -> GithubId.create(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GitHub ID는 비어 있을 수 없습니다.");
    }
}
