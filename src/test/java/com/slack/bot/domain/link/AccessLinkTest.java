package com.slack.bot.domain.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AccessLinkTest {

    @Test
    void 링크를_초기화한다() {
        // given
        String linkKey = "abc123";
        Long projectMemberId = 1L;

        // when
        AccessLink link = AccessLink.create(linkKey, projectMemberId);

        // then
        assertAll(
                () -> assertThat(link.getLinkKey()).isEqualTo(linkKey),
                () -> assertThat(link.getProjectMemberId()).isEqualTo(projectMemberId)
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 링크_키가_비어_있으면_초기화할_수_없다(String linkKey) {
        // when & then
        assertThatThrownBy(() -> AccessLink.create(linkKey, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("linkKey는 비어 있을 수 없습니다.");
    }

    @Test
    void 멤버_ID가_비어_있으면_초기화할_수_없다() {
        // when & then
        assertThatThrownBy(() -> AccessLink.create("abc123", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("projectMemberId는 비어 있을 수 없습니다.");
    }
}
