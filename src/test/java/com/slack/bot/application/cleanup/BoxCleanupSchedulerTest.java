package com.slack.bot.application.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.slack.bot.global.config.properties.BoxCleanupProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BoxCleanupSchedulerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(BoxCleanupSchedulerTestConfiguration.class);

    @Mock
    BoxCleanupService boxCleanupService;

    @Test
    void enabled가_true면_완료된_box_정리를_실행한다() {
        // given
        Clock clock = Clock.fixed(Instant.parse("2026-04-13T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        BoxCleanupProperties properties = new BoxCleanupProperties(true, 1_800_000L, 30L, 500);
        BoxCleanupScheduler scheduler = new BoxCleanupScheduler(clock, properties, boxCleanupService);
        given(boxCleanupService.cleanCompletedBoxes(Instant.parse("2026-03-14T00:00:00Z"), 500))
                .willReturn(new BoxCleanupService.CleanupResult(
                        BoxCleanupService.DomainCleanupResult.succeeded(1),
                        BoxCleanupService.DomainCleanupResult.succeeded(2),
                        BoxCleanupService.DomainCleanupResult.succeeded(3),
                        BoxCleanupService.DomainCleanupResult.succeeded(4)
                ));

        // when
        scheduler.cleanCompletedBoxes();

        // then
        verify(boxCleanupService).cleanCompletedBoxes(Instant.parse("2026-03-14T00:00:00Z"), 500);
    }

    @Test
    void enabled가_true이고_실패가_없으면_완료_로그를_남긴다() {
        // given
        Clock clock = Clock.fixed(Instant.parse("2026-04-13T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        BoxCleanupProperties properties = new BoxCleanupProperties(true, 1_800_000L, 30L, 500);
        BoxCleanupScheduler scheduler = new BoxCleanupScheduler(clock, properties, boxCleanupService);
        ListAppender<ILoggingEvent> listAppender = attachListAppender();
        given(boxCleanupService.cleanCompletedBoxes(Instant.parse("2026-03-14T00:00:00Z"), 500))
                .willReturn(new BoxCleanupService.CleanupResult(
                        BoxCleanupService.DomainCleanupResult.succeeded(1),
                        BoxCleanupService.DomainCleanupResult.succeeded(2),
                        BoxCleanupService.DomainCleanupResult.succeeded(3),
                        BoxCleanupService.DomainCleanupResult.succeeded(4)
                ));

        // when
        try {
            scheduler.cleanCompletedBoxes();

            // then
            assertThat(logLevels(listAppender)).contains(Level.INFO);
            assertThat(logMessages(listAppender)).contains("box cleanup을 완료했습니다. interactionInboxDeleted=1, interactionOutboxDeleted=2, reviewInboxDeleted=3, reviewOutboxDeleted=4, totalDeleted=10");
        } finally {
            detachListAppender(listAppender);
        }
    }

    @Test
    void enabled가_true면_scheduler_빈을_등록한다() {
        // given
        ApplicationContextRunner runner = contextRunner.withPropertyValues(
                "app.cleanup.box.enabled=true",
                "app.cleanup.box.fixed-delay-ms=1800000",
                "app.cleanup.box.retention-days=30",
                "app.cleanup.box.delete-batch-size=500"
        );

        // when & then
        runner.run(context -> assertThat(context).hasSingleBean(BoxCleanupScheduler.class));
    }

    @Test
    void enabled가_false면_scheduler_빈을_등록하지_않는다() {
        // given
        ApplicationContextRunner runner = contextRunner.withPropertyValues(
                "app.cleanup.box.enabled=false",
                "app.cleanup.box.fixed-delay-ms=1800000",
                "app.cleanup.box.retention-days=30",
                "app.cleanup.box.delete-batch-size=500"
        );

        // when & then
        runner.run(context -> assertThat(context).doesNotHaveBean(BoxCleanupScheduler.class));
    }

    @Test
    void 정리_실행중_예외가_발생해도_전파하지_않는다() {
        // given
        Clock clock = Clock.fixed(Instant.parse("2026-04-13T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        BoxCleanupProperties properties = new BoxCleanupProperties(true, 1_800_000L, 30L, 500);
        BoxCleanupScheduler scheduler = new BoxCleanupScheduler(clock, properties, boxCleanupService);
        willThrow(new RuntimeException("cleanup failure"))
                .given(boxCleanupService)
                .cleanCompletedBoxes(Instant.parse("2026-03-14T00:00:00Z"), 500);

        // when & then
        assertThatCode(() -> scheduler.cleanCompletedBoxes()).doesNotThrowAnyException();
    }

    @Test
    void 부분_실패결과가_오면_경고_로그를_남기고_예외없이_종료한다() {
        // given
        Clock clock = Clock.fixed(Instant.parse("2026-04-13T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        BoxCleanupProperties properties = new BoxCleanupProperties(true, 1_800_000L, 30L, 500);
        BoxCleanupScheduler scheduler = new BoxCleanupScheduler(clock, properties, boxCleanupService);
        ListAppender<ILoggingEvent> listAppender = attachListAppender();
        given(boxCleanupService.cleanCompletedBoxes(Instant.parse("2026-03-14T00:00:00Z"), 500))
                .willReturn(new BoxCleanupService.CleanupResult(
                        BoxCleanupService.DomainCleanupResult.succeeded(1),
                        BoxCleanupService.DomainCleanupResult.failedResult(),
                        BoxCleanupService.DomainCleanupResult.succeeded(3),
                        BoxCleanupService.DomainCleanupResult.succeeded(4)
                ));

        // when
        try {
            assertThatCode(() -> scheduler.cleanCompletedBoxes()).doesNotThrowAnyException();

            // then
            assertThat(logLevels(listAppender)).contains(Level.WARN);
            assertThat(logMessages(listAppender)).contains(
                    "box cleanup이 부분 실패로 종료됐습니다. interactionInboxDeleted=1, interactionInboxFailed=false, interactionOutboxDeleted=0, interactionOutboxFailed=true, reviewInboxDeleted=3, reviewInboxFailed=false, reviewOutboxDeleted=4, reviewOutboxFailed=false, totalDeleted=8"
            );
        } finally {
            detachListAppender(listAppender);
        }
    }

    private ListAppender<ILoggingEvent> attachListAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(BoxCleanupScheduler.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        return listAppender;
    }

    private void detachListAppender(ListAppender<ILoggingEvent> listAppender) {
        Logger logger = (Logger) LoggerFactory.getLogger(BoxCleanupScheduler.class);
        logger.detachAppender(listAppender);
        listAppender.stop();
    }

    private List<String> logMessages(ListAppender<ILoggingEvent> listAppender) {
        List<String> messages = new ArrayList<>();

        for (ILoggingEvent event : listAppender.list) {
            messages.add(event.getFormattedMessage());
        }

        return messages;
    }

    private List<Level> logLevels(ListAppender<ILoggingEvent> listAppender) {
        List<Level> levels = new ArrayList<>();

        for (ILoggingEvent event : listAppender.list) {
            levels.add(event.getLevel());
        }

        return levels;
    }

    @Configuration
    @EnableConfigurationProperties(BoxCleanupProperties.class)
    @Import(BoxCleanupScheduler.class)
    static class BoxCleanupSchedulerTestConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-04-13T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        }

        @Bean
        BoxCleanupService boxCleanupService() {
            return mock(BoxCleanupService.class);
        }
    }
}
