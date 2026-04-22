package com.slack.bot.infrastructure.interaction.box.persistence.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxHistory;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStatus;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStringField;
import com.slack.bot.infrastructure.interaction.box.out.repository.SlackNotificationOutboxRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.InvalidDataAccessApiUsageException;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackNotificationOutboxRepositoryAdapterTest {

    @Autowired
    SlackNotificationOutboxRepository slackNotificationOutboxRepository;

    @Autowired
    SlackNotificationOutboxMybatisMapper slackNotificationOutboxMybatisMapper;

    @Autowired
    SlackNotificationOutboxHistoryMybatisMapper slackNotificationOutboxHistoryMybatisMapper;

    @Test
    void save는_할당된_id의_row를_찾지_못하면_예외를_던지고_새_row를_만들지_않는다() {
        // given
        SlackNotificationOutbox missingOutbox = SlackNotificationOutbox.rehydrate(
                999_999L,
                SlackNotificationOutboxMessageType.CHANNEL_TEXT,
                "OUTBOX-MISSING-ID",
                "T1",
                "C1",
                SlackNotificationOutboxStringField.absent(),
                SlackNotificationOutboxStringField.present("hello"),
                SlackNotificationOutboxStringField.absent(),
                SlackNotificationOutboxStringField.absent(),
                SlackNotificationOutboxStatus.PENDING,
                0,
                BoxProcessingLease.idle(),
                BoxEventTime.absent(),
                BoxEventTime.absent(),
                BoxFailureSnapshot.absent()
        );

        // when & then
        assertThatThrownBy(() -> slackNotificationOutboxRepository.save(missingOutbox))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("저장 대상 outbox를 찾을 수 없습니다. id=999999")
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(slackNotificationOutboxMybatisMapper.findAllDomains()).isEmpty();
    }

    @Test
    void 완료된_outbox와_history를_같이_삭제한다() {
        // given
        SlackNotificationOutbox oldSentOutbox = createSentOutbox("cleanup-sent", Instant.parse("2026-02-24T00:01:00Z"));
        SlackNotificationOutbox recentFailedOutbox = createFailedOutbox(
                "cleanup-failed",
                Instant.parse("2026-02-24T00:09:00Z")
        );

        // when
        int deletedCount = slackNotificationOutboxRepository.deleteCompletedBefore(
                Instant.parse("2026-02-24T00:05:00Z"),
                100
        );

        // then
        List<SlackNotificationOutboxHistory> histories = slackNotificationOutboxHistoryMybatisMapper.findAllDomains();
        assertAll(
                () -> assertThat(deletedCount).isEqualTo(1),
                () -> assertThat(slackNotificationOutboxMybatisMapper.findDomainById(oldSentOutbox.getId())).isEmpty(),
                () -> assertThat(slackNotificationOutboxMybatisMapper.findDomainById(recentFailedOutbox.getId())).isPresent(),
                () -> assertThat(histories)
                        .filteredOn(history -> history.getOutboxId().equals(oldSentOutbox.getId()))
                        .isEmpty(),
                () -> assertThat(histories)
                        .filteredOn(history -> history.getOutboxId().equals(recentFailedOutbox.getId()))
                        .hasSize(1)
        );
    }

    @Test
    void deleteCompletedBefore는_batch_크기만큼만_삭제한다() {
        // given
        SlackNotificationOutbox firstOutbox = createSentOutbox("cleanup-batch-1", Instant.parse("2026-02-24T00:01:00Z"));
        SlackNotificationOutbox secondOutbox = createSentOutbox("cleanup-batch-2", Instant.parse("2026-02-24T00:02:00Z"));

        // when
        int deletedCount = slackNotificationOutboxRepository.deleteCompletedBefore(
                Instant.parse("2026-02-24T00:05:00Z"),
                1
        );

        // then
        assertAll(
                () -> assertThat(deletedCount).isEqualTo(1),
                () -> assertThat(slackNotificationOutboxMybatisMapper.findDomainById(firstOutbox.getId())).isEmpty(),
                () -> assertThat(slackNotificationOutboxMybatisMapper.findDomainById(secondOutbox.getId())).isPresent()
        );
    }

    private SlackNotificationOutbox createSentOutbox(String idempotencyKey, Instant sentAt) {
        SlackNotificationOutbox outbox = SlackNotificationOutbox.builder()
                                                                .messageType(SlackNotificationOutboxMessageType.CHANNEL_TEXT)
                                                                .idempotencyKey(idempotencyKey)
                                                                .teamId("T1")
                                                                .channelId("C1")
                                                                .text("hello")
                                                                .build();
        SlackNotificationOutbox saved = slackNotificationOutboxRepository.save(outbox);
        saved.claim(sentAt.minusSeconds(30L));
        SlackNotificationOutboxHistory history = saved.markSent(sentAt);
        return saveOutboxWithHistory(saved, history);
    }

    private SlackNotificationOutbox createFailedOutbox(String idempotencyKey, Instant failedAt) {
        SlackNotificationOutbox outbox = SlackNotificationOutbox.builder()
                                                                .messageType(SlackNotificationOutboxMessageType.CHANNEL_TEXT)
                                                                .idempotencyKey(idempotencyKey)
                                                                .teamId("T1")
                                                                .channelId("C1")
                                                                .text("hello")
                                                                .build();
        SlackNotificationOutbox saved = slackNotificationOutboxRepository.save(outbox);
        saved.claim(failedAt.minusSeconds(30L));
        SlackNotificationOutboxHistory history = saved.markFailed(
                failedAt,
                "failure",
                SlackInteractionFailureType.BUSINESS_INVARIANT
        );
        return saveOutboxWithHistory(saved, history);
    }

    private SlackNotificationOutbox saveOutboxWithHistory(
            SlackNotificationOutbox outbox,
            SlackNotificationOutboxHistory history
    ) {
        SlackNotificationOutbox persistedOutbox = slackNotificationOutboxRepository.save(outbox);
        slackNotificationOutboxHistoryMybatisMapper.insert(SlackNotificationOutboxHistoryRow.from(history));
        return persistedOutbox;
    }
}
