package com.slack.bot.application.interactivity.box;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class ProcessingSourceContext {

    private static final ThreadLocal<ProcessingSourceType> SOURCE = new ThreadLocal<>();

    public boolean isInboxProcessing() {
        return SOURCE.get() == ProcessingSourceType.INBOX;
    }

    public void withInboxProcessing(Runnable runnable) {
        withSource(ProcessingSourceType.INBOX, () -> {
            runnable.run();
            return null;
        });
    }

    public <T> T withInboxProcessing(Supplier<T> supplier) {
        return withSource(ProcessingSourceType.INBOX, supplier);
    }

    private <T> T withSource(ProcessingSourceType sourceType, Supplier<T> supplier) {
        ProcessingSourceType previous = SOURCE.get();
        SOURCE.set(sourceType);

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

    private enum ProcessingSourceType {
        INBOX
    }
}
