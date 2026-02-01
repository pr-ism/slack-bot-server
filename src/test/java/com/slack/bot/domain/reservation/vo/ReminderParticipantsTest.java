package com.slack.bot.domain.reservation.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReminderParticipantsTest {

    @Test
    void 리마인더_참여자_정보를_초기화한다() {
        // when & then
        ReminderParticipants participants = assertDoesNotThrow(
                () -> ReminderParticipants.builder()
                                          .pullRequestAuthorSlackId("U_AUTHOR")
                                          .reviewerSlackId("U_REVIEWER")
                                          .build()
        );

        assertAll(
                () -> assertThat(participants.getPullRequestAuthorSlackId()).isEqualTo("U_AUTHOR"),
                () -> assertThat(participants.getReviewerSlackId()).isEqualTo("U_REVIEWER")
        );
    }
}
