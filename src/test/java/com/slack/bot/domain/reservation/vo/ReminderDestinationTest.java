package com.slack.bot.domain.reservation.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReminderDestinationTest {

    @Test
    void 리마인더_전송_채널_정보를_초기화한다() {
        // when & then
        ReminderDestination destination = assertDoesNotThrow(
                () -> ReminderDestination.builder()
                                         .teamId("T1")
                                         .channelId("C1")
                                         .build()
        );

        assertAll(
                () -> assertThat(destination.getTeamId()).isEqualTo("T1"),
                () -> assertThat(destination.getChannelId()).isEqualTo("C1")
        );
    }
}
