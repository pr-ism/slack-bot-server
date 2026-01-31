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

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReservationPullRequestTest {

    @Test
    void Pull_Request_정보를_초기화한다() {
        // when & then
        ReservationPullRequest pullRequest = assertDoesNotThrow(
                () -> ReservationPullRequest.of(
                        "PR_1",
                        "123",
                        "feat: 기능 추가",
                        "https://github.com/org/repo/pull/123"
                )
        );

        assertAll(
                () -> assertThat(pullRequest.getPullRequestId()).isEqualTo("PR_1"),
                () -> assertThat(pullRequest.getPullRequestNumber()).isEqualTo("123"),
                () -> assertThat(pullRequest.getPullRequestTitle()).isEqualTo("feat: 기능 추가"),
                () -> assertThat(pullRequest.getPullRequestUrl()).isEqualTo("https://github.com/org/repo/pull/123")
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    void Pull_Request_ID가_비어_있으면_초기화할_수_없다(String pullRequestId) {
        // when & then
        assertThatThrownBy(
                () -> ReservationPullRequest.of(
                        pullRequestId,
                        "123",
                        "feat: 기능 추가",
                        "https://github.com/org/repo/pull/123"
                )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Pull Request ID는 비어 있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void Pull_Request_번호가_비어_있으면_초기화할_수_없다(String pullRequestNumber) {
        // when & then
        assertThatThrownBy(
                () -> ReservationPullRequest.of(
                        "PR_1",
                        pullRequestNumber,
                        "feat: 기능 추가",
                        "https://github.com/org/repo/pull/123"
                )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Pull Request 번호는 비어 있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void Pull_Request_제목이_비어_있으면_초기화할_수_없다(String pullRequestTitle) {
        // when & then
        assertThatThrownBy(
                () -> ReservationPullRequest.of(
                        "PR_1",
                        "123",
                        pullRequestTitle,
                        "https://github.com/org/repo/pull/123"
                )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Pull Request 제목은 비어 있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void Pull_Request_URL이_비어_있으면_초기화할_수_없다(String pullRequestUrl) {
        // when & then
        assertThatThrownBy(
                () -> ReservationPullRequest.of(
                        "PR_1",
                        "123",
                        "feat: 기능 추가",
                        pullRequestUrl
                )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Pull Request URL은 비어 있을 수 없습니다.");
    }
}
