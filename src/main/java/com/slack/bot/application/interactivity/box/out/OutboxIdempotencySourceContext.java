package com.slack.bot.application.interactivity.box.out;

import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class OutboxIdempotencySourceContext {

    private static final ThreadLocal<String> SOURCE = new ThreadLocal<>();
    private static final String INBOX_PREFIX = "INBOX";
    private static final String BUSINESS_PREFIX = "BUSINESS";

    public String requireSourceKey() {
        return currentSourceKey().orElseThrow(
                () -> new IllegalStateException("아웃박스 멱등성 source 키가 필요합니다.")
        );
    }

    public Optional<String> currentSourceKey() {
        return Optional.ofNullable(SOURCE.get());
    }

    public void withInboxSource(Long inboxId, Runnable runnable) {
        withSource(INBOX_PREFIX, normalizeSourceId(inboxId), () -> {
            runnable.run();
            return null;
        });
    }

    public <T> T withBusinessEventSource(String businessEventId, Supplier<T> supplier) {
        return withSource(BUSINESS_PREFIX, normalizeSourceId(businessEventId), supplier);
    }

    private <T> T withSource(String prefix, String id, Supplier<T> supplier) {
        String previous = SOURCE.get();
        SOURCE.set(prefix + ":" + id);

        try {
            return supplier.get();
        } finally {
            if (previous != null) {
                SOURCE.set(previous);
            }
            if (previous == null) {
                SOURCE.remove();
            }
        }
    }

    private String normalizeSourceId(Object sourceId) {
        if (sourceId == null) {
            return "";
        }

        return sourceId.toString();
    }
}
