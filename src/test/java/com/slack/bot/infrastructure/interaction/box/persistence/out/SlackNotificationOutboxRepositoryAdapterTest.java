package com.slack.bot.infrastructure.interaction.box.persistence.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.infrastructure.common.BoxEventTime;
import com.slack.bot.infrastructure.common.BoxFailureSnapshot;
import com.slack.bot.infrastructure.common.BoxProcessingLease;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutbox;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxMessageType;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStatus;
import com.slack.bot.infrastructure.interaction.box.out.SlackNotificationOutboxStringField;
import com.slack.bot.infrastructure.interaction.box.out.repository.SlackNotificationOutboxRepository;
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
    JpaSlackNotificationOutboxRepository jpaSlackNotificationOutboxRepository;

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
        assertThat(jpaSlackNotificationOutboxRepository.findAllDomains()).isEmpty();
    }
}
