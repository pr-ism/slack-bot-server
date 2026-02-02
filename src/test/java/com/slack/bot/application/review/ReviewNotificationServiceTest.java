package com.slack.bot.application.review;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.review.dto.request.ReviewRequestEventRequest;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewNotificationServiceTest {

    @Autowired
    ReviewNotificationService notificationService;

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/review/project_t1.sql",
            "classpath:sql/fixtures/review/workspace_t1.sql",
            "classpath:sql/fixtures/review/channel_t1.sql",
            "classpath:sql/fixtures/review/project_member_t1_mapped.sql"
    })
    void 리뷰_요청_알림을_정상적으로_발송한다() {
        // given
        ReviewRequestEventRequest request = new ReviewRequestEventRequest(
                "test-api-key", "my-repo", "PR-1", 42,
                "Fix bug", "https://github.com/pr/1",
                "author-gh", "opened",
                List.of("reviewer-gh-1"), List.of()
        );

        // when & then
        assertDoesNotThrow(() -> notificationService.sendSimpleNotification(request));
    }
}
