package com.slack.bot.application.review.box.aop.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.review.box.ReviewNotificationSourceContext;
import com.slack.bot.application.review.box.aop.aspect.support.ReviewAspectIntegrationProbes.ReviewNotificationSourceBindingProbe;
import com.slack.bot.application.review.dto.ReviewNotificationPayload;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewNotificationSourceBindingAspectIntegrationTest {

    @Autowired
    ReviewNotificationSourceContext reviewNotificationSourceContext;

    @Autowired
    ReviewNotificationSourceBindingProbe reviewNotificationSourceBindingProbe;

    @BeforeEach
    void setUp() {
        reviewNotificationSourceBindingProbe.reset();
    }

    @Test
    void 컨텍스트에_source_key가_없으면_기본_형식으로_바인딩한다() {
        // when
        String actual = reviewNotificationSourceBindingProbe.bind("api-key", request(101L));

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo("REVIEW_REQUEST:api-key:101"),
                () -> assertThat(reviewNotificationSourceBindingProbe.proceedCount()).isEqualTo(1),
                () -> assertThat(reviewNotificationSourceBindingProbe.observedSourceKey())
                        .hasValue("REVIEW_REQUEST:api-key:101"),
                () -> assertThat(reviewNotificationSourceContext.currentSourceKey()).isEmpty()
        );
    }

    @Test
    void 컨텍스트에_source_key가_이미_있으면_기존값을_유지한다() {
        // when
        String actual = reviewNotificationSourceContext.withSourceKey(
                "EXISTING:SOURCE",
                () -> reviewNotificationSourceBindingProbe.bind("api-key", request(202L))
        );

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo("EXISTING:SOURCE"),
                () -> assertThat(reviewNotificationSourceBindingProbe.proceedCount()).isEqualTo(1),
                () -> assertThat(reviewNotificationSourceBindingProbe.observedSourceKey()).hasValue("EXISTING:SOURCE"),
                () -> assertThat(reviewNotificationSourceContext.currentSourceKey()).isEmpty()
        );
    }

    @Test
    void 인박스에서_전달된_라운드_source_key를_그대로_유지한다() {
        // given
        String inboxRoundSourceKey = "REVIEW_REQUEST_INBOX:api-key:202:2:1700000000000";

        // when
        String actual = reviewNotificationSourceContext.withSourceKey(
                inboxRoundSourceKey,
                () -> reviewNotificationSourceBindingProbe.bind("api-key", request(202L))
        );

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(inboxRoundSourceKey),
                () -> assertThat(reviewNotificationSourceBindingProbe.proceedCount()).isEqualTo(1),
                () -> assertThat(reviewNotificationSourceBindingProbe.observedSourceKey())
                        .hasValue(inboxRoundSourceKey),
                () -> assertThat(reviewNotificationSourceContext.currentSourceKey()).isEmpty()
        );
    }

    private ReviewNotificationPayload request(Long githubPullRequestId) {
        return new ReviewNotificationPayload(
                "repo-name",
                githubPullRequestId,
                1,
                "PR title",
                "https://github.com/org/repo/pull/1",
                "author-id",
                List.of("reviewer-a"),
                List.of("reviewer-a")
        );
    }
}
