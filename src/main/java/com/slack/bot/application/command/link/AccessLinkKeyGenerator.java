package com.slack.bot.application.command.link;

import com.slack.bot.domain.link.dto.AccessLinkSequenceBlockDto;
import com.slack.bot.domain.link.repository.AccessLinkSequenceRepository;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessLinkKeyGenerator {

    private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final Long INITIAL_COUNTER = 916_132_831L;
    private static final Long BLOCK_SIZE = 1_000L;

    private final AccessLinkSequenceRepository sequenceRepository;
    private final AtomicLong nextValue = new AtomicLong(1L);
    private final AtomicLong endValue = new AtomicLong(0L);
    private final Object lock = new Object();

    public String generateKey() {
        long value = nextValue();

        return encode(value);
    }

    private long nextValue() {
        while (true) {
            long value = nextValue.getAndIncrement();

            if (value <= endValue.get()) {
                return value;
            }

            refillBlock();
        }
    }

    private void refillBlock() {
        synchronized (lock) {
            if (nextValue.get() <= endValue.get()) {
                return;
            }

            AccessLinkSequenceBlockDto block = sequenceRepository.allocateBlock(BLOCK_SIZE, INITIAL_COUNTER);

            nextValue.set(block.start());
            endValue.set(block.end());
        }
    }

    private String encode(long value) {
        StringBuilder builder = new StringBuilder();
        long current = value;

        while (current > 0L) {
            int index = (int) (current % ALPHABET.length);
            builder.append(ALPHABET[index]);
            current = current / ALPHABET.length;
        }

        return builder.reverse()
                      .toString();
    }
}
