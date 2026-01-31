package com.slack.bot.domain.reservation.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReminderPullRequestTest {

    @Test
    void 리마인더_Pull_Request_정보를_초기화한다() {
        // when & then
        ReminderPullRequest pullRequest = assertDoesNotThrow(
                () -> ReminderPullRequest.builder()
                                         .pullRequestTitle("feat: 기능 구현")
                                         .pullRequestUrl("https://github.com/org/repo/pull/1")
                                         .build()
        );

        assertAll(
                () -> assertThat(pullRequest.getPullRequestTitle()).isEqualTo("feat: 기능 구현"),
                () -> assertThat(pullRequest.getPullRequestUrl()).isEqualTo("https://github.com/org/repo/pull/1")
        );
    }
}
