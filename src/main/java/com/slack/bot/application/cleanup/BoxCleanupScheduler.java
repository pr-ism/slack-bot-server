package com.slack.bot.application.cleanup;

import com.slack.bot.global.config.properties.BoxCleanupProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BoxCleanupScheduler {

    private final Clock clock;
    private final BoxCleanupProperties boxCleanupProperties;
    private final BoxCleanupService boxCleanupService;

    @Scheduled(fixedDelayString = "${app.cleanup.box.fixed-delay-ms:1800000}")
    public void cleanCompletedBoxes() {
        if (!boxCleanupProperties.enabled()) {
            return;
        }

        try {
            Instant completedBefore = clock.instant()
                                           .minus(Duration.ofDays(boxCleanupProperties.retentionDays()));
            BoxCleanupService.CleanupResult result = boxCleanupService.cleanCompletedBoxes(
                    completedBefore,
                    boxCleanupProperties.deleteBatchSize()
            );
            log.info(
                    "box cleanup을 완료했습니다. interactionInboxDeleted={}, interactionOutboxDeleted={}, reviewInboxDeleted={}, reviewOutboxDeleted={}, totalDeleted={}",
                    result.interactionInboxDeleted(),
                    result.interactionOutboxDeleted(),
                    result.reviewInboxDeleted(),
                    result.reviewOutboxDeleted(),
                    result.totalDeleted()
            );
        } catch (Exception exception) {
            log.error("box cleanup scheduler 실행에 실패했습니다.", exception);
        }
    }
}
