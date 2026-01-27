package com.slack.bot.infrastructure.link.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.domain.link.AccessLinkSequence;
import com.slack.bot.domain.link.dto.AccessLinkSequenceBlockDto;
import com.slack.bot.domain.link.repository.AccessLinkSequenceRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.test.context.jdbc.Sql;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AccessLinkSequenceRepositoryAdapterTest {

    @Autowired
    AccessLinkSequenceRepository sequenceRepository;

    @Autowired
    JpaAccessLinkSequenceRepository sequenceJpaRepository;

    @Test
    @Sql(scripts = "classpath:sql/fixtures/link/access_link_sequence_initial_zero_seed.sql")
    void 동시에_요청해도_시퀀스_블록이_중복되지_않고_초기화된다() throws Exception {
        // given
        int threadCount = 10;
        long blockSize = 10L;
        long initialValue = 0L;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<Future<AccessLinkSequenceBlockDto>> futures = new ArrayList<>();

        // when
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < threadCount; i++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    ready.countDown();
                                    try {
                                        start.await();
                                        return sequenceRepository.allocateBlock(blockSize, initialValue);
                                    } finally {
                                        done.countDown();
                                    }
                                }
                        )
                );
            }
            ready.await();
            start.countDown();
            done.await(5, TimeUnit.SECONDS);
        }

        Set<Long> allocatedValues = ConcurrentHashMap.newKeySet();
        for (Future<AccessLinkSequenceBlockDto> future : futures) {
            AccessLinkSequenceBlockDto block = future.get();
            for (long value = block.start(); value <= block.end(); value++) {
                allocatedValues.add(value);
            }
        }
        Optional<AccessLinkSequence> sequence = sequenceJpaRepository.findById(AccessLinkSequence.DEFAULT_ID);

        // then
        assertAll(
                () -> assertThat(allocatedValues).hasSize(100), // threadCount(10) * blockSize(10) = 100
                () -> assertThat(sequence).isPresent(),
                () -> assertThat(sequence.get().getNextValue()).isEqualTo(100L) // initialValue(0) + ( threadCount(10) * blockSize(10) ) = 100
        );
    }
}
