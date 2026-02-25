package com.slack.bot.infrastructure.review.box.in.repository;

import com.slack.bot.infrastructure.review.box.in.ReviewRequestInbox;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReviewRequestInboxRepository {

    void upsertPending(
            String coalescingKey,
            String apiKey,
            Long githubPullRequestId,
            String requestJson,
            Instant availableAt
    );

    List<ReviewRequestInbox> findClaimable(Instant availableBeforeOrAt, int limit);

    Optional<ReviewRequestInbox> findById(Long inboxId);

    boolean markProcessingIfClaimable(Long inboxId, Instant processingStartedAt, Instant availableBeforeOrAt);

    int recoverTimeoutProcessing(Instant processingStartedBefore, Instant failedAt, String failureReason);

    ReviewRequestInbox save(ReviewRequestInbox inbox);
}
