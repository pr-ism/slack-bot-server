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
        public void dispatch(Dispatcher dispatcher) throws JsonProcessingException {
            dispatcher.dispatchEphemeralText();
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
        public void dispatch(Dispatcher dispatcher) throws JsonProcessingException {
            dispatcher.dispatchEphemeralBlocks();
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
        public void dispatch(Dispatcher dispatcher) throws JsonProcessingException {
            dispatcher.dispatchChannelText();
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
        public void dispatch(Dispatcher dispatcher) throws JsonProcessingException {
            dispatcher.dispatchChannelBlocks();
        }
    };

    public abstract boolean supportsUserId();

    public abstract boolean supportsText();

    public abstract boolean supportsBlocksJson();

    public abstract boolean supportsFallbackText();

    public abstract void dispatch(Dispatcher dispatcher) throws JsonProcessingException;

    public interface Dispatcher {

        void dispatchEphemeralText() throws JsonProcessingException;

        void dispatchEphemeralBlocks() throws JsonProcessingException;

        void dispatchChannelText() throws JsonProcessingException;

        void dispatchChannelBlocks() throws JsonProcessingException;
    }
}
