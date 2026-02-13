package com.slack.bot.application.interactivity.block.handler.store;

import java.time.Instant;

public interface StartReviewMarkStore {

    Instant get(String key);

    Instant putIfAbsent(String key, Instant markedAt);

    void put(String key, Instant markedAt);

    void remove(String key);

    boolean remove(String key, Instant markedAt);
}
