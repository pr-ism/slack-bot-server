package com.slack.bot.application.review.box.aop.aspect.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.review.box.ReviewNotificationSourceContext;
import com.slack.bot.application.review.box.aop.BindReviewNotificationSourceKey;
import com.slack.bot.application.review.box.aop.EnqueueReviewNotificationOutbox;
import com.slack.bot.application.review.dto.request.ReviewAssignmentRequest;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class ReviewAspectIntegrationProbes {

    public static class ReviewNotificationOutboxEnqueueProbe {

        private final AtomicInteger proceedCount = new AtomicInteger();

        @EnqueueReviewNotificationOutbox
        public Object send(
                String token,
                String channelId,
                JsonNode blocks,
                JsonNode attachments,
                String fallbackText
        ) {
            proceedCount.incrementAndGet();
            return "PROCEEDED";
        }

        public int proceedCount() {
            return proceedCount.get();
        }

        public void reset() {
            proceedCount.set(0);
        }
    }

    public static class ReviewNotificationSourceBindingProbe {

        private final ReviewNotificationSourceContext reviewNotificationSourceContext;
        private final AtomicInteger proceedCount = new AtomicInteger();
        private volatile String observedSourceKey;

        public ReviewNotificationSourceBindingProbe(
                ReviewNotificationSourceContext reviewNotificationSourceContext
        ) {
            this.reviewNotificationSourceContext = reviewNotificationSourceContext;
        }

        @BindReviewNotificationSourceKey
        public String bind(String apiKey, ReviewAssignmentRequest request) {
            proceedCount.incrementAndGet();
            observedSourceKey = reviewNotificationSourceContext.requireSourceKey();
            return observedSourceKey;
        }

        public int proceedCount() {
            return proceedCount.get();
        }

        public Optional<String> observedSourceKey() {
            return Optional.ofNullable(observedSourceKey);
        }

        public void reset() {
            proceedCount.set(0);
            observedSourceKey = null;
        }
    }
}
