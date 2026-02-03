package com.slack.bot.domain.setting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DeliverySpaceTest {

    @Test
    void DIRECT_MESSAGE는_DM이_활성화되어_있다() {
        // when
        boolean actual = DeliverySpace.DIRECT_MESSAGE.isDirectMessage();

        // then
        assertThat(actual).isTrue();
    }

    @Test
    void TRIGGER_CHANNEL은_DM이_비활성화되어_있다() {
        // when
        boolean actual = DeliverySpace.TRIGGER_CHANNEL.isDirectMessage();

        // then
        assertThat(actual).isFalse();
    }
}
