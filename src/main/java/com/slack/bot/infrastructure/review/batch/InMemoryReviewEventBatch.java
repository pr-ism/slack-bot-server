package com.slack.bot.infrastructure.review.batch;

import com.slack.bot.application.review.ReviewNotificationService;
import com.slack.bot.application.review.ReviewEventBatch;
import com.slack.bot.application.review.dto.request.ReviewAssignmentRequest;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InMemoryReviewEventBatch implements ReviewEventBatch {

    private static final long DEFAULT_BATCH_WINDOW_MILLIS = 5_000L;

    private final TaskScheduler taskScheduler;
    private final ReviewNotificationService notificationService;
    private final ConcurrentHashMap<BatchKey, PendingEvent> pendingEvents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<BatchKey, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final ReentrantLock batchLock = new ReentrantLock();

    @Value("${review.notification.batch.window-millis:" + DEFAULT_BATCH_WINDOW_MILLIS + "}")
    private long batchWindowMillis;

    @Override
    public void buffer(String apiKey, ReviewAssignmentRequest report) {
        BatchKey key = new BatchKey(apiKey, report.pullRequestId());

        batchLock.lock();
        try {
            pendingEvents.put(key, new PendingEvent(apiKey, report));

            scheduledTasks.computeIfAbsent(key, k -> taskScheduler.schedule(
                    () -> flush(k),
                    Instant.now().plusMillis(batchWindowMillis)
            ));
        } finally {
            batchLock.unlock();
        }
    }

    private void flush(BatchKey key) {
        PendingEvent pendingEvent;
        batchLock.lock();
        try {
            scheduledTasks.remove(key);
            pendingEvent = pendingEvents.remove(key);
        } finally {
            batchLock.unlock();
        }

        if (pendingEvent == null) {
            return;
        }

        notificationService.sendSimpleNotification(pendingEvent.apiKey(), pendingEvent.request());
    }

    private record BatchKey(String apiKey, String pullRequestId) {
    }

    private record PendingEvent(String apiKey, ReviewAssignmentRequest request) {
    }
}
