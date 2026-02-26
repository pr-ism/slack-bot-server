package com.slack.bot.infrastructure.review.box.out.repository;

import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReviewNotificationOutboxRepository {

    boolean enqueue(ReviewNotificationOutbox outbox);

    ReviewNotificationOutbox save(ReviewNotificationOutbox outbox);

    Optional<ReviewNotificationOutbox> findById(Long outboxId);

    List<ReviewNotificationOutbox> findClaimable(int limit);

    boolean markProcessingIfClaimable(Long outboxId, Instant processingStartedAt);

    int recoverTimeoutProcessing(
            Instant processingStartedBefore,
            Instant failedAt,
            String failureReason,
            int maxAttempts
    );
}
