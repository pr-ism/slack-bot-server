package com.slack.bot.application.review.box;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewNotificationIdempotencyKeyGeneratorTest {

    ReviewNotificationIdempotencyKeyGenerator generator = new ReviewNotificationIdempotencyKeyGenerator();

    @Test
    void 동일한_scope와_payload면_같은_해시를_생성한다() {
        // when
        String first = generator.generate(ReviewNotificationIdempotencyScope.REVIEW_NOTIFICATION_OUTBOX, "payload");
        String second = generator.generate(ReviewNotificationIdempotencyScope.REVIEW_NOTIFICATION_OUTBOX, "payload");

        // then
        assertThat(first).isEqualTo(second);
    }

    @Test
    void scope나_payload가_null이어도_해시를_생성한다() {
        // when
        String hash = generator.generate(null, null);

        // then
        assertThat(hash).hasSize(64);
    }
}

