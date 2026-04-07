package com.slack.bot.infrastructure.interaction.box.out;

import com.fasterxml.jackson.core.JsonProcessingException;

public enum SlackNotificationOutboxMessageType {
    EPHEMERAL_TEXT {
        @Override
        public boolean supportsUserId() {
            return true;
        }

        @Override
        public boolean supportsText() {
            return true;
        }

        @Override
        public boolean supportsBlocksJson() {
            return false;
        }

        @Override
        public boolean supportsFallbackText() {
            return false;
        }

        @Override
        public void dispatch(
                DispatchAction dispatchEphemeralText,
                DispatchAction dispatchEphemeralBlocks,
                DispatchAction dispatchChannelText,
                DispatchAction dispatchChannelBlocks
        ) throws JsonProcessingException {
            dispatchEphemeralText.run();
        }
    },
    EPHEMERAL_BLOCKS {
        @Override
        public boolean supportsUserId() {
            return true;
        }

        @Override
        public boolean supportsText() {
            return false;
        }

        @Override
        public boolean supportsBlocksJson() {
            return true;
        }

        @Override
        public boolean supportsFallbackText() {
            return true;
        }

        @Override
        public void dispatch(
                DispatchAction dispatchEphemeralText,
                DispatchAction dispatchEphemeralBlocks,
                DispatchAction dispatchChannelText,
                DispatchAction dispatchChannelBlocks
        ) throws JsonProcessingException {
            dispatchEphemeralBlocks.run();
        }
    },
    CHANNEL_TEXT {
        @Override
        public boolean supportsUserId() {
            return false;
        }

        @Override
        public boolean supportsText() {
            return true;
        }

        @Override
        public boolean supportsBlocksJson() {
            return false;
        }

        @Override
        public boolean supportsFallbackText() {
            return false;
        }

        @Override
        public void dispatch(
                DispatchAction dispatchEphemeralText,
                DispatchAction dispatchEphemeralBlocks,
                DispatchAction dispatchChannelText,
                DispatchAction dispatchChannelBlocks
        ) throws JsonProcessingException {
            dispatchChannelText.run();
        }
    },
    CHANNEL_BLOCKS {
        @Override
        public boolean supportsUserId() {
            return false;
        }

        @Override
        public boolean supportsText() {
            return false;
        }

        @Override
        public boolean supportsBlocksJson() {
            return true;
        }

        @Override
        public boolean supportsFallbackText() {
            return true;
        }

        @Override
        public void dispatch(
                DispatchAction dispatchEphemeralText,
                DispatchAction dispatchEphemeralBlocks,
                DispatchAction dispatchChannelText,
                DispatchAction dispatchChannelBlocks
        ) throws JsonProcessingException {
            dispatchChannelBlocks.run();
        }
    };

    public abstract boolean supportsUserId();

    public abstract boolean supportsText();

    public abstract boolean supportsBlocksJson();

    public abstract boolean supportsFallbackText();

    public abstract void dispatch(
            DispatchAction dispatchEphemeralText,
            DispatchAction dispatchEphemeralBlocks,
            DispatchAction dispatchChannelText,
            DispatchAction dispatchChannelBlocks
    ) throws JsonProcessingException;

    @FunctionalInterface
    public interface DispatchAction {

        void run() throws JsonProcessingException;
    }
}
