package com.slack.bot.application.interactivity.block.handler.store;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryStartReviewMarkStore implements StartReviewMarkStore {

    private final Map<String, Instant> marks = new ConcurrentHashMap<>();

    @Override
    public Instant get(String key) {
        return marks.get(key);
    }

    @Override
    public void put(String key, Instant markedAt) {
        marks.put(key, markedAt);
    }

    @Override
    public void remove(String key) {
        marks.remove(key);
    }
}
