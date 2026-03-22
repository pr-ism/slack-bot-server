package com.slack.bot.application.review.box.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.review.dto.ReviewNotificationPayload;
import com.slack.bot.support.jackson.FailingObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewRequestInboxIdempotencyPayloadEncoderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void apiKey와_pullRequestId를_DTO_JSON으로_인코딩한다() throws Exception {
        // given
        ReviewRequestInboxIdempotencyPayloadEncoder encoder =
                new ReviewRequestInboxIdempotencyPayloadEncoder(objectMapper);

        // when
        String actualJson = encoder.encode("api-key", request(101L, "title"));

        // then
        JsonNode actual = objectMapper.readTree(actualJson);
        assertAll(
                () -> assertThat(actual.path("apiKey").asText()).isEqualTo("api-key"),
                () -> assertThat(actual.path("githubPullRequestId").asLong()).isEqualTo(101L),
                () -> assertThat(actual.path("reviewRoundKey").asText()).isEmpty()
        );
    }

    @Test
    void apiKey가_null이어도_빈문자열로_인코딩한다() throws Exception {
        // given
        ReviewRequestInboxIdempotencyPayloadEncoder encoder =
                new ReviewRequestInboxIdempotencyPayloadEncoder(objectMapper);

        // when
        String actualJson = encoder.encode(null, request(202L, "title"));

        // then
        JsonNode actual = objectMapper.readTree(actualJson);
        assertThat(actual.path("apiKey").asText()).isEmpty();
    }

    @Test
    void reviewRoundKey가_있으면_함께_인코딩한다() throws Exception {
        // given
        ReviewRequestInboxIdempotencyPayloadEncoder encoder =
                new ReviewRequestInboxIdempotencyPayloadEncoder(objectMapper);

        // when
        String actualJson = encoder.encode("api-key", request(303L, "title", "api-key:303:2"));

        // then
        JsonNode actual = objectMapper.readTree(actualJson);
        assertThat(actual.path("reviewRoundKey").asText()).isEqualTo("api-key:303:2");
    }

    @Test
    void 직렬화_실패_시_IllegalStateException을_던진다() {
        // given
        ReviewRequestInboxIdempotencyPayloadEncoder encoder =
                new ReviewRequestInboxIdempotencyPayloadEncoder(new FailingObjectMapper());

        // when & then
        assertThatThrownBy(() -> encoder.encode("api-key", request(404L, "title")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("review request inbox 멱등성 payload 직렬화에 실패했습니다.");
    }

    private ReviewNotificationPayload request(Long githubPullRequestId, String pullRequestTitle) {
        return request(githubPullRequestId, pullRequestTitle, null);
    }

    private ReviewNotificationPayload request(Long githubPullRequestId, String pullRequestTitle, String reviewRoundKey) {
        return new ReviewNotificationPayload(
                "my-repo",
                githubPullRequestId,
                Math.toIntExact(githubPullRequestId),
                pullRequestTitle,
                "https://github.com/pr/" + githubPullRequestId,
                "author-gh",
                List.of("reviewer-gh-1"),
                List.of("reviewer-gh-1"),
                reviewRoundKey
        );
    }
}
