package com.slack.bot.infrastructure.interaction.box.out;

import com.fasterxml.jackson.core.JsonProcessingException;

public enum SlackNotificationOutboxMessageType {
    EPHEMERAL_TEXT(
            true,
            true,
            false,
            false,
            dispatcher -> dispatcher.dispatchEphemeralText()
    ),
    EPHEMERAL_BLOCKS(
            true,
            false,
            true,
            true,
            dispatcher -> dispatcher.dispatchEphemeralBlocks()
    ),
    CHANNEL_TEXT(
            false,
            true,
            false,
            false,
            dispatcher -> dispatcher.dispatchChannelText()
    ),
    CHANNEL_BLOCKS(
            false,
            false,
            true,
            true,
            dispatcher -> dispatcher.dispatchChannelBlocks()
    );

    private final boolean supportsUserId;
    private final boolean supportsText;
    private final boolean supportsBlocksJson;
    private final boolean supportsFallbackText;
    private final DispatchRouter dispatchRouter;

    SlackNotificationOutboxMessageType(
            boolean supportsUserId,
            boolean supportsText,
            boolean supportsBlocksJson,
            boolean supportsFallbackText,
            DispatchRouter dispatchRouter
    ) {
        this.supportsUserId = supportsUserId;
        this.supportsText = supportsText;
        this.supportsBlocksJson = supportsBlocksJson;
        this.supportsFallbackText = supportsFallbackText;
        this.dispatchRouter = dispatchRouter;
    }

    public boolean supportsUserId() {
        return supportsUserId;
    }

    public boolean supportsText() {
        return supportsText;
    }

    public boolean supportsBlocksJson() {
        return supportsBlocksJson;
    }

    public boolean supportsFallbackText() {
        return supportsFallbackText;
    }

    public void dispatch(Dispatcher dispatcher) throws JsonProcessingException {
        dispatchRouter.route(dispatcher);
    }

    public interface Dispatcher {

        void dispatchEphemeralText() throws JsonProcessingException;

        void dispatchEphemeralBlocks() throws JsonProcessingException;

        void dispatchChannelText() throws JsonProcessingException;

        void dispatchChannelBlocks() throws JsonProcessingException;

        static DispatchBuilder builder() {
            return new DispatchBuilder();
        }
    }

    public static final class DispatchBuilder {

        private DispatchAction dispatchEphemeralText;
        private DispatchAction dispatchEphemeralBlocks;
        private DispatchAction dispatchChannelText;
        private DispatchAction dispatchChannelBlocks;

        public DispatchBuilder dispatchEphemeralText(DispatchAction action) {
            validateAction(action, "dispatchEphemeralText");
            this.dispatchEphemeralText = action;
            return this;
        }

        public DispatchBuilder dispatchEphemeralBlocks(DispatchAction action) {
            validateAction(action, "dispatchEphemeralBlocks");
            this.dispatchEphemeralBlocks = action;
            return this;
        }

        public DispatchBuilder dispatchChannelText(DispatchAction action) {
            validateAction(action, "dispatchChannelText");
            this.dispatchChannelText = action;
            return this;
        }

        public DispatchBuilder dispatchChannelBlocks(DispatchAction action) {
            validateAction(action, "dispatchChannelBlocks");
            this.dispatchChannelBlocks = action;
            return this;
        }

        public Dispatcher build() {
            validateConfigured(dispatchEphemeralText, "dispatchEphemeralText");
            validateConfigured(dispatchEphemeralBlocks, "dispatchEphemeralBlocks");
            validateConfigured(dispatchChannelText, "dispatchChannelText");
            validateConfigured(dispatchChannelBlocks, "dispatchChannelBlocks");

            return new ActionDispatcher(
                    dispatchEphemeralText,
                    dispatchEphemeralBlocks,
                    dispatchChannelText,
                    dispatchChannelBlocks
            );
        }

        private void validateAction(DispatchAction action, String fieldName) {
            if (action == null) {
                throw new IllegalArgumentException(fieldName + "는 비어 있을 수 없습니다.");
            }
        }

        private void validateConfigured(DispatchAction action, String fieldName) {
            if (action == null) {
                throw new IllegalStateException(fieldName + "가 설정되지 않았습니다.");
            }
        }
    }

    private static final class ActionDispatcher implements Dispatcher {

        private final DispatchAction dispatchEphemeralText;
        private final DispatchAction dispatchEphemeralBlocks;
        private final DispatchAction dispatchChannelText;
        private final DispatchAction dispatchChannelBlocks;

        private ActionDispatcher(
                DispatchAction dispatchEphemeralText,
                DispatchAction dispatchEphemeralBlocks,
                DispatchAction dispatchChannelText,
                DispatchAction dispatchChannelBlocks
        ) {
            this.dispatchEphemeralText = dispatchEphemeralText;
            this.dispatchEphemeralBlocks = dispatchEphemeralBlocks;
            this.dispatchChannelText = dispatchChannelText;
            this.dispatchChannelBlocks = dispatchChannelBlocks;
        }

        @Override
        public void dispatchEphemeralText() throws JsonProcessingException {
            dispatchEphemeralText.run();
        }

        @Override
        public void dispatchEphemeralBlocks() throws JsonProcessingException {
            dispatchEphemeralBlocks.run();
        }

        @Override
        public void dispatchChannelText() throws JsonProcessingException {
            dispatchChannelText.run();
        }

        @Override
        public void dispatchChannelBlocks() throws JsonProcessingException {
            dispatchChannelBlocks.run();
        }
    }

    @FunctionalInterface
    public interface DispatchAction {

        void run() throws JsonProcessingException;
    }

    @FunctionalInterface
    private interface DispatchRouter {

        void route(Dispatcher dispatcher) throws JsonProcessingException;
    }
}
