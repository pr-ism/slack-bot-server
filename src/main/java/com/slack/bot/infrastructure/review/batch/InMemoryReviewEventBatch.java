package com.slack.bot.infrastructure.review.batch;

import com.slack.bot.application.review.ReviewNotificationService;
import com.slack.bot.application.review.ReviewEventBatch;
import com.slack.bot.application.review.dto.request.ReviewRequestEventRequest;
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

    private final TaskScheduler taskScheduler;
    private final ReviewNotificationService notificationService;
    private final ConcurrentHashMap<BatchKey, PendingEvent> pendingEvents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<BatchKey, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @Value("${review.notification.batch.window-millis:" + DEFAULT_BATCH_WINDOW_MILLIS + "}")
    private long batchWindowMillis;

    @Override
    public void buffer(String apiKey, ReviewRequestEventRequest report) {
        BatchKey key = new BatchKey(apiKey, report.pullRequestId());

        pendingEvents.put(key, new PendingEvent(apiKey, report));

        scheduledTasks.computeIfAbsent(key, k -> taskScheduler.schedule(
                () -> flush(k),
                Instant.now().plusMillis(batchWindowMillis)
        ));
    }

    private void flush(BatchKey key) {
        scheduledTasks.remove(key);

        PendingEvent pendingEvent = pendingEvents.remove(key);

        if (pendingEvent == null) {
            return;
        }

        notificationService.sendSimpleNotification(pendingEvent.apiKey(), pendingEvent.request());
    }

    private record BatchKey(String apiKey, String pullRequestId) {
    }

    private record PendingEvent(String apiKey, ReviewRequestEventRequest request) {
    }
}
