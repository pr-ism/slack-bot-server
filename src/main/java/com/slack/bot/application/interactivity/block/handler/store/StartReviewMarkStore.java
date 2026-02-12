package com.slack.bot.application.interactivity.block.handler.store;

import java.time.Instant;

public interface StartReviewMarkStore {

    Instant get(String key);

    void put(String key, Instant markedAt);

    void remove(String key);
}
