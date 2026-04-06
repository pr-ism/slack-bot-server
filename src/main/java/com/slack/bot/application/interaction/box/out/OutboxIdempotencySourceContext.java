package com.slack.bot.application.interaction.box.out;

import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class OutboxIdempotencySourceContext {

    private static final ThreadLocal<OutboxIdempotencySourceBinding> SOURCE =
            new ThreadLocal<>() {
                @Override
                protected OutboxIdempotencySourceBinding initialValue() {
                    return AbsentOutboxIdempotencySourceBinding.INSTANCE;
                }
            };
    private static final String INBOX_PREFIX = "INBOX";
    private static final String BUSINESS_PREFIX = "BUSINESS";

    public String requireSourceKey() {
        Optional<String> sourceKey = currentSourceKey();
        if (sourceKey.isPresent()) {
            return sourceKey.get();
        }

        throw new IllegalStateException("아웃박스 멱등성 source 키가 필요합니다.");
    }

    public Optional<String> currentSourceKey() {
        OutboxIdempotencySourceBinding sourceBinding = SOURCE.get();
        if (!sourceBinding.isPresent()) {
            return Optional.empty();
        }
        if (sourceBinding instanceof PresentOutboxIdempotencySourceBinding presentSourceBinding) {
            return Optional.of(presentSourceBinding.value());
        }

        throw new IllegalStateException("source key binding 상태가 올바르지 않습니다.");
    }

    public void withInboxSource(Long inboxId, Runnable runnable) {
        OutboxSourceId sourceId = normalizeSourceId(inboxId);
        withSource(INBOX_PREFIX, sourceId, runnable);
    }

    public <T> T withBusinessEventSource(String businessEventId, Supplier<T> supplier) {
        OutboxSourceId sourceId = normalizeSourceId(businessEventId);
        return withSource(BUSINESS_PREFIX, sourceId, supplier);
    }

    private void withSource(String prefix, OutboxSourceId sourceId, Runnable runnable) {
        OutboxIdempotencySourceBinding previous = SOURCE.get();
        SOURCE.set(PresentOutboxIdempotencySourceBinding.from(prefix, sourceId));

        try {
            runnable.run();
        } finally {
            SOURCE.set(previous);
        }
    }

    private <T> T withSource(String prefix, OutboxSourceId sourceId, Supplier<T> supplier) {
        OutboxIdempotencySourceBinding previous = SOURCE.get();
        SOURCE.set(PresentOutboxIdempotencySourceBinding.from(prefix, sourceId));

        try {
            return supplier.get();
        } finally {
            SOURCE.set(previous);
        }
    }

    private OutboxSourceId normalizeSourceId(Object sourceId) {
        if (sourceId == null) {
            return AbsentOutboxSourceId.INSTANCE;
        }

        return new PresentOutboxSourceId(sourceId.toString());
    }

    private sealed interface OutboxIdempotencySourceBinding
            permits AbsentOutboxIdempotencySourceBinding, PresentOutboxIdempotencySourceBinding {

        boolean isPresent();
    }

    private static final class AbsentOutboxIdempotencySourceBinding implements OutboxIdempotencySourceBinding {

        private static final AbsentOutboxIdempotencySourceBinding INSTANCE =
                new AbsentOutboxIdempotencySourceBinding();

        private AbsentOutboxIdempotencySourceBinding() {
        }

        @Override
        public boolean isPresent() {
            return false;
        }
    }

    private record PresentOutboxIdempotencySourceBinding(
            String value
    ) implements OutboxIdempotencySourceBinding {

        private static PresentOutboxIdempotencySourceBinding from(String prefix, OutboxSourceId sourceId) {
            return new PresentOutboxIdempotencySourceBinding(prefix + ":" + sourceId.value());
        }

        private PresentOutboxIdempotencySourceBinding {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("source key는 비어 있을 수 없습니다.");
            }
        }

        @Override
        public boolean isPresent() {
            return true;
        }
    }

    private sealed interface OutboxSourceId permits AbsentOutboxSourceId, PresentOutboxSourceId {

        String value();
    }

    private static final class AbsentOutboxSourceId implements OutboxSourceId {

        private static final AbsentOutboxSourceId INSTANCE = new AbsentOutboxSourceId();

        private AbsentOutboxSourceId() {
        }

        @Override
        public String value() {
            return "";
        }
    }

    private record PresentOutboxSourceId(String value) implements OutboxSourceId {

        private PresentOutboxSourceId {
            if (value == null) {
                throw new IllegalArgumentException("sourceId는 비어 있을 수 없습니다.");
            }
        }
    }
}
