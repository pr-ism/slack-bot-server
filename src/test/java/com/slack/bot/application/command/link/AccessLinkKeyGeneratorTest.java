package com.slack.bot.application.command.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.domain.link.AccessLinkSequence;
import com.slack.bot.domain.link.repository.AccessLinkSequenceRepository;
import com.slack.bot.global.config.properties.AccessLinkKeyProperties;
import com.slack.bot.infrastructure.link.persistence.JpaAccessLinkSequenceRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AccessLinkKeyGeneratorTest {

    @Autowired
    AccessLinkSequenceRepository sequenceRepository;

    @Autowired
    JpaAccessLinkSequenceRepository sequenceJpaRepository;

    @Autowired
    AccessLinkKeyProperties accessLinkKeyProperties;

    private AccessLinkKeyGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new AccessLinkKeyGenerator(sequenceRepository, accessLinkKeyProperties);
    }

    @Test
    void 키는_최소_길이를_만족하고_url_safe_문자만_포함한다() {
        // when
        String key = generator.generateKey();

        // then
        assertAll(
                () -> assertThat(key).hasSize(22),
                () -> assertThat(key).matches("^[0-9A-Za-z]+$")
        );
    }

    @Test
    void 키는_요청마다_서로_다르게_생성된다() {
        // when
        String first = generator.generateKey();
        String second = generator.generateKey();

        // then
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/link/access_link_sequence_initial.sql")
    void 블록을_모두_사용하면_다음_블록을_확보한다() {
        // when
        int total = 1_001;
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < total; i++) {
            keys.add(generator.generateKey());
        }
        long initialCounter = 916_132_831L;
        AccessLinkSequence sequence = sequenceJpaRepository.findById(AccessLinkSequence.DEFAULT_ID)
                                                           .orElseThrow();

        // then
        assertAll(
                () -> assertThat(keys).hasSize(total),
                () -> assertThat(sequence.getNextValue()).isEqualTo(initialCounter + 2_000L)
        );
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/link/access_link_sequence_initial.sql")
    void 동시에_요청해도_키는_중복되지_않는다() throws Exception {
        // given
        int threadCount = 10;
        int perThread = 200;
        int total = threadCount * perThread;
        Set<String> keys = ConcurrentHashMap.newKeySet();
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        // when
        boolean finished;
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < threadCount; i++) {
                executor.execute(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        for (int j = 0; j < perThread; j++) {
                            keys.add(generator.generateKey());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            ready.await();
            start.countDown();
            finished = done.await(5, TimeUnit.SECONDS);
        }

        // then
        assertAll(
                () -> assertThat(finished).isTrue(),
                () -> assertThat(keys).hasSize(total)
        );
    }
}
