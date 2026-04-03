package com.slack.bot.application.interaction.box.aop.aspect.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.bot.application.interaction.box.ProcessingSourceContext;
import com.slack.bot.application.interaction.box.aop.BindInboxToOutboxSource;
import com.slack.bot.application.interaction.box.aop.EnqueueBlockActionInInbox;
import com.slack.bot.application.interaction.box.aop.EnqueueViewSubmissionInInbox;
import com.slack.bot.application.interaction.box.aop.ResolveOutboxSource;
import com.slack.bot.application.interaction.box.out.OutboxIdempotencySourceContext;
import com.slack.bot.application.interaction.reply.dto.response.SlackActionResponse;
import com.slack.bot.application.interaction.view.dto.ViewSubmissionImmediateDto;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class AspectIntegrationProbes {

    public enum BlockActionProbeMode {
        RETURN_VALUE,
        THROW_RUNTIME,
        THROW_ERROR,
        THROW_CHECKED
    }

    public static class BlockActionAspectProbe {

        private final AtomicInteger proceedCount = new AtomicInteger();

        @EnqueueBlockActionInInbox
        public void handle(JsonNode payload, BlockActionProbeMode mode) {
            proceedCount.incrementAndGet();

            if (mode == BlockActionProbeMode.RETURN_VALUE) {
                return;
            }
            if (mode == BlockActionProbeMode.THROW_RUNTIME) {
                throw new IllegalArgumentException("runtime failure");
            }
            if (mode == BlockActionProbeMode.THROW_ERROR) {
                throw new AssertionError("error failure");
            }

            sneakyThrow(new Exception("checked failure"));
        }

        public int proceedCount() {
            return proceedCount.get();
        }

        public void reset() {
            proceedCount.set(0);
        }
    }

    public enum ViewSubmissionProbeMode {
        RETURN_ENQUEUE,
        RETURN_NO_ENQUEUE,
        THROW_RUNTIME,
        THROW_ERROR,
        THROW_CHECKED
    }

    public static class ViewSubmissionAspectProbe {

        private final AtomicInteger proceedCount = new AtomicInteger();
        private final AtomicInteger invalidProceedCount = new AtomicInteger();

        @EnqueueViewSubmissionInInbox
        public ViewSubmissionImmediateDto handle(JsonNode payload, ViewSubmissionProbeMode mode) {
            proceedCount.incrementAndGet();

            if (mode == ViewSubmissionProbeMode.RETURN_ENQUEUE) {
                return ViewSubmissionImmediateDto.enqueue(SlackActionResponse.empty());
            }
            if (mode == ViewSubmissionProbeMode.RETURN_NO_ENQUEUE) {
                return ViewSubmissionImmediateDto.noEnqueue(SlackActionResponse.empty());
            }
            if (mode == ViewSubmissionProbeMode.THROW_RUNTIME) {
                throw new IllegalArgumentException("runtime failure");
            }
            if (mode == ViewSubmissionProbeMode.THROW_ERROR) {
                throw new AssertionError("error failure");
            }

            sneakyThrow(new Exception("checked failure"));
            throw new IllegalStateException("checked failure는 여기까지 도달할 수 없습니다.");
        }

        @EnqueueViewSubmissionInInbox
        public Object invalidReturn(JsonNode payload) {
            invalidProceedCount.incrementAndGet();
            return "invalid";
        }

        public int proceedCount() {
            return proceedCount.get();
        }

        public int invalidProceedCount() {
            return invalidProceedCount.get();
        }

        public void reset() {
            proceedCount.set(0);
            invalidProceedCount.set(0);
        }
    }

    public enum InboxToOutboxMode {
        RETURN_VALUE,
        THROW_RUNTIME,
        THROW_ERROR,
        THROW_CHECKED
    }

    public static class InboxToOutboxProbe {

        private final ProcessingSourceContext processingSourceContext;
        private final OutboxIdempotencySourceContext outboxIdempotencySourceContext;

        private volatile boolean observedInboxProcessing;
        private volatile String observedSourceKey;

        public InboxToOutboxProbe(
                ProcessingSourceContext processingSourceContext,
                OutboxIdempotencySourceContext outboxIdempotencySourceContext
        ) {
            this.processingSourceContext = processingSourceContext;
            this.outboxIdempotencySourceContext = outboxIdempotencySourceContext;
        }

        @BindInboxToOutboxSource
        public String bind(SlackInteractionInbox inbox, InboxToOutboxMode mode) throws Exception {
            observedInboxProcessing = processingSourceContext.isInboxProcessing();
            observedSourceKey = outboxIdempotencySourceContext.currentSourceKey().orElse(null);

            if (mode == InboxToOutboxMode.RETURN_VALUE) {
                return observedSourceKey;
            }
            if (mode == InboxToOutboxMode.THROW_RUNTIME) {
                throw new IllegalArgumentException("runtime failure");
            }
            if (mode == InboxToOutboxMode.THROW_ERROR) {
                throw new AssertionError("error failure");
            }

            throw new Exception("checked failure");
        }

        public Optional<String> observedSourceKey() {
            return Optional.ofNullable(observedSourceKey);
        }

        public boolean observedInboxProcessing() {
            return observedInboxProcessing;
        }

        public void reset() {
            observedInboxProcessing = false;
            observedSourceKey = null;
        }
    }

    public static class OutboxSourceResolverProbe {

        private final AtomicInteger noArgsProceedCount = new AtomicInteger();
        private final AtomicInteger wrongTypeProceedCount = new AtomicInteger();
        private final AtomicInteger resolveProceedCount = new AtomicInteger();

        private volatile String observedSourceKey;

        @ResolveOutboxSource
        public String noArgs() {
            noArgsProceedCount.incrementAndGet();
            return "NO_ARGS";
        }

        @ResolveOutboxSource
        public String wrongType(Object sourceKey, String payload) {
            wrongTypeProceedCount.incrementAndGet();
            return sourceKey + "|" + payload;
        }

        @ResolveOutboxSource
        public String resolve(String sourceKey, String payload) {
            resolveProceedCount.incrementAndGet();
            observedSourceKey = sourceKey;
            return sourceKey + "|" + payload;
        }

        public int noArgsProceedCount() {
            return noArgsProceedCount.get();
        }

        public int wrongTypeProceedCount() {
            return wrongTypeProceedCount.get();
        }

        public int resolveProceedCount() {
            return resolveProceedCount.get();
        }

        public String observedSourceKey() {
            return observedSourceKey;
        }

        public void reset() {
            noArgsProceedCount.set(0);
            wrongTypeProceedCount.set(0);
            resolveProceedCount.set(0);
            observedSourceKey = null;
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable throwable) throws E {
        throw (E) throwable;
    }
}
