package com.slack.bot.infrastructure.review.box.out.repository;

import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface ReviewNotificationOutboxRepository {

    boolean enqueue(ReviewNotificationOutbox outbox);

    ReviewNotificationOutbox save(ReviewNotificationOutbox outbox);

    boolean renewProcessingLease(
            Long outboxId,
            Instant currentProcessingStartedAt,
            Instant renewedProcessingStartedAt
    );

    boolean saveIfProcessingLeaseMatched(
            ReviewNotificationOutbox outbox,
            Instant claimedProcessingStartedAt
    );

    Optional<ReviewNotificationOutbox> findById(Long outboxId);

    Optional<Long> claimNextId(Instant processingStartedAt, Collection<Long> excludedOutboxIds);

    int recoverTimeoutProcessing(
            Instant processingStartedBefore,
            Instant failedAt,
            String failureReason,
            int maxAttempts
    );
}
