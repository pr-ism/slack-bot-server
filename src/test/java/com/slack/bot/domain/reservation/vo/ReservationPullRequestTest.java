package com.slack.bot.domain.reservation.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReservationPullRequestTest {

    @Test
    void pull_request_정보를_초기화한다() {
        // when & then
        ReservationPullRequest pullRequest = assertDoesNotThrow(
                () -> ReservationPullRequest.builder()
                        .pullRequestId(1L)
                        .pullRequestNumber(123)
                        .pullRequestTitle("feat: 기능 추가")
                        .pullRequestUrl("https://github.com/org/repo/pull/123")
                        .build()
        );

        assertAll(
                () -> assertThat(pullRequest.getPullRequestId()).isEqualTo(1L),
                () -> assertThat(pullRequest.getPullRequestNumber()).isEqualTo(123),
                () -> assertThat(pullRequest.getPullRequestTitle()).isEqualTo("feat: 기능 추가"),
                () -> assertThat(pullRequest.getPullRequestUrl()).isEqualTo("https://github.com/org/repo/pull/123")
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void pull_request_ID가_0_이하이면_초기화할_수_없다(Long pullRequestId) {
        // when & then
        assertThatThrownBy(
                () -> ReservationPullRequest.builder()
                        .pullRequestId(pullRequestId)
                        .pullRequestNumber(123)
                        .pullRequestTitle("feat: 기능 추가")
                        .pullRequestUrl("https://github.com/org/repo/pull/123")
                        .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Pull Request ID는 0보다 커야 합니다.");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void pull_request_번호가_0_이하이면_초기화할_수_없다(int pullRequestNumber) {
        // when & then
        assertThatThrownBy(
                () -> ReservationPullRequest.builder()
                        .pullRequestId(1L)
                        .pullRequestNumber(pullRequestNumber)
                        .pullRequestTitle("feat: 기능 추가")
                        .pullRequestUrl("https://github.com/org/repo/pull/123")
                        .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Pull Request 번호는 0보다 커야 합니다.");
    }

    @Test
    void pull_request_ID가_비어_있다면_초기화할_수_없다() {
        // when & then
        assertThatThrownBy(
                () -> ReservationPullRequest.builder()
                                            .pullRequestId(null)
                                            .pullRequestNumber(123)
                                            .pullRequestTitle("feat: 기능 추가")
                                            .pullRequestUrl("https://github.com/org/repo/pull/123")
                                            .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Pull Request ID는 0보다 커야 합니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void pull_request_제목이_비어_있으면_초기화할_수_없다(String pullRequestTitle) {
        // when & then
        assertThatThrownBy(
                () -> ReservationPullRequest.builder()
                        .pullRequestId(1L)
                        .pullRequestNumber(123)
                        .pullRequestTitle(pullRequestTitle)
                        .pullRequestUrl("https://github.com/org/repo/pull/123")
                        .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Pull Request 제목은 비어 있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void pull_request_URL이_비어_있으면_초기화할_수_없다(String pullRequestUrl) {
        // when & then
        assertThatThrownBy(
                () -> ReservationPullRequest.builder()
                        .pullRequestId(1L)
                        .pullRequestNumber(123)
                        .pullRequestTitle("feat: 기능 추가")
                        .pullRequestUrl(pullRequestUrl)
                        .build()
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Pull Request URL은 비어 있을 수 없습니다.");
    }
}
