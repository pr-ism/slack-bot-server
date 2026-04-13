package com.slack.bot.application.cleanup;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.slack.bot.global.config.properties.BoxCleanupProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BoxCleanupSchedulerTest {

    @Mock
    BoxCleanupService boxCleanupService;

    @Test
    void enabled가_true면_완료된_box_정리를_실행한다() {
        // given
        Clock clock = Clock.fixed(Instant.parse("2026-04-13T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        BoxCleanupProperties properties = new BoxCleanupProperties(true, 1_800_000L, 30L, 500);
        BoxCleanupScheduler scheduler = new BoxCleanupScheduler(clock, properties, boxCleanupService);
        given(boxCleanupService.cleanCompletedBoxes(Instant.parse("2026-03-14T00:00:00Z"), 500))
                .willReturn(new BoxCleanupService.CleanupResult(1, 2, 3, 4));

        // when
        scheduler.cleanCompletedBoxes();

        // then
        verify(boxCleanupService).cleanCompletedBoxes(Instant.parse("2026-03-14T00:00:00Z"), 500);
    }

    @Test
    void enabled가_false면_정리를_실행하지_않는다() {
        // given
        Clock clock = Clock.fixed(Instant.parse("2026-04-13T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        BoxCleanupProperties properties = new BoxCleanupProperties(false, 1_800_000L, 30L, 500);
        BoxCleanupScheduler scheduler = new BoxCleanupScheduler(clock, properties, boxCleanupService);

        // when
        scheduler.cleanCompletedBoxes();

        // then
        verifyNoInteractions(boxCleanupService);
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
}
