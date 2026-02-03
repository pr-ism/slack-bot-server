package com.slack.bot.domain.setting;

public enum DeliverySpace {
    DIRECT_MESSAGE,
    TRIGGER_CHANNEL;

    public boolean isDirectMessage() {
        return this == DIRECT_MESSAGE;
    }
}
