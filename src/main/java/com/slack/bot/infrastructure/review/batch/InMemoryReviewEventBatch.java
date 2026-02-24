package com.slack.bot.infrastructure.review.batch;

import com.slack.bot.application.review.ReviewEventBatch;
import com.slack.bot.application.review.box.in.ReviewRequestInboxProcessor;
import com.slack.bot.application.review.dto.request.ReviewAssignmentRequest;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InMemoryReviewEventBatch implements ReviewEventBatch {

    private static final long DEFAULT_BATCH_WINDOW_MILLIS = 5_000L;
    private static final int DEFAULT_PROCESS_BATCH_SIZE = 30;
    private static final long DEFAULT_PROCESSING_TIMEOUT_MS = 60_000L;

    private final TaskScheduler taskScheduler;
    private final ReviewRequestInboxProcessor reviewRequestInboxProcessor;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @Value("${review.notification.batch.window-millis:" + DEFAULT_BATCH_WINDOW_MILLIS + "}")
    private long batchWindowMillis;

    @Value("${review.notification.inbox.batch-size:" + DEFAULT_PROCESS_BATCH_SIZE + "}")
    private int batchSize;

    @Value("${review.notification.inbox.processing-timeout-ms:" + DEFAULT_PROCESSING_TIMEOUT_MS + "}")
    private long processingTimeoutMs;

    @Override
    public void buffer(String apiKey, ReviewAssignmentRequest request) {
        reviewRequestInboxProcessor.enqueue(apiKey, request, batchWindowMillis);
        scheduleFlush(apiKey, request.githubPullRequestId());
    }

    private void scheduleFlush(String apiKey, Long githubPullRequestId) {
        String coalescingKey = apiKey + ":" + githubPullRequestId;

        scheduledTasks.computeIfAbsent(
                coalescingKey,
                key -> taskScheduler.schedule(() -> flush(key), Instant.now().plusMillis(batchWindowMillis))
        );
    }

    private void flush(String coalescingKey) {
        try {
            reviewRequestInboxProcessor.processPending(batchSize, processingTimeoutMs);
        } finally {
            scheduledTasks.remove(coalescingKey);
        }
    }
}
