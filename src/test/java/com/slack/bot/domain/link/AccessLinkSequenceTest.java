package com.slack.bot.domain.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.domain.link.dto.AccessLinkSequenceBlockDto;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AccessLinkSequenceTest {

    @Test
    void 블록을_할당하면_현재_값이_갱신된다() {
        // given
        AccessLinkSequence sequence = AccessLinkSequence.create(1L, 100L);

        // when
        AccessLinkSequenceBlockDto block = sequence.allocateBlock(10L);

        // then
        assertAll(
                () -> assertThat(block.start()).isEqualTo(101L),
                () -> assertThat(block.end()).isEqualTo(110L),
                () -> assertThat(sequence.getNextValue()).isEqualTo(110L)
        );
    }

    @Test
    void 블록_크기가_0_이하면_할당할_수_없다() {
        // given
        AccessLinkSequence sequence = AccessLinkSequence.create(1L, 0L);

        // when & then
        assertThatThrownBy(() -> sequence.allocateBlock(0L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("블록 크기는 0보다 커야 합니다.");
    }

    @Test
    void 블록_범위를_계산할_수_없으면_할당할_수_없다() {
        // given
        AccessLinkSequence sequence = AccessLinkSequence.create(1L, Long.MAX_VALUE);

        // when & then
        assertThatThrownBy(() -> sequence.allocateBlock(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("블록 범위가 올바르지 않습니다.");
    }

    @Test
    void 기본_ID가_아니면_초기화할_수_없다() {
        // when & then
        assertThatThrownBy(() -> AccessLinkSequence.create(2L, 0L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("시퀀스 ID는 기본값과 일치해야 합니다.");
    }

    @Test
    void 다음_값이_음수면_초기화할_수_없다() {
        // when & then
        assertThatThrownBy(() -> AccessLinkSequence.create(1L, -1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("다음 값은 0 이상이어야 합니다.");
    }

    @Test
    void 블록_시작값이_0_이하면_할당할_수_없다() throws Exception {
        // given
        AccessLinkSequence sequence = AccessLinkSequence.create(1L, 0L);
        Field field = AccessLinkSequence.class.getDeclaredField("nextValue");

        field.setAccessible(true);
        field.set(sequence, -1L);

        // when & then
        assertThatThrownBy(() -> sequence.allocateBlock(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("블록 범위가 올바르지 않습니다.");
    }
}
