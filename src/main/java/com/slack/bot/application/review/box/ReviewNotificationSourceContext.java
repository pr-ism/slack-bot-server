package com.slack.bot.application.review.box;

import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class ReviewNotificationSourceContext {

    private static final ThreadLocal<String> SOURCE_KEY = new ThreadLocal<>();

    public String requireSourceKey() {
        return currentSourceKey().orElseThrow(
                () -> new IllegalStateException("review_notification sourceKey가 필요합니다.")
        );
    }

    public Optional<String> currentSourceKey() {
        return Optional.ofNullable(SOURCE_KEY.get());
    }

    public <T> T withSourceKey(String sourceKey, Supplier<T> supplier) {
        validateSourceKey(sourceKey);

        String previous = SOURCE_KEY.get();
        SOURCE_KEY.set(sourceKey);

        try {
            return supplier.get();
        } finally {
            if (previous != null) {
                SOURCE_KEY.set(previous);
            } else {
                SOURCE_KEY.remove();
            }
        }
    }

    public void withSourceKey(String sourceKey, Runnable runnable) {
        withSourceKey(sourceKey, () -> {
            runnable.run();
            return null;
        });
    }

    private void validateSourceKey(String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank()) {
            throw new IllegalArgumentException("sourceKey는 비어 있을 수 없습니다.");
        }
    }
}
