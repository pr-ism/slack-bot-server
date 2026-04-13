package com.slack.bot.infrastructure.interaction.box.persistence.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.infrastructure.interaction.box.SlackInteractionFailureType;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInbox;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxHistory;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxStatus;
import com.slack.bot.infrastructure.interaction.box.in.SlackInteractionInboxType;
import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SlackInteractionInboxRepositoryAdapterTest {

    @Autowired
    SlackInteractionInboxRepository slackInteractionInboxRepository;

    @Autowired
    JpaSlackInteractionInboxRepository jpaSlackInteractionInboxRepository;

    @Autowired
    JpaSlackInteractionInboxHistoryRepository jpaSlackInteractionInboxHistoryRepository;

    @Test
    void recoverTimeoutProcessing은_interactionType이_null이면_예외를_던진다() {
        // given
        SlackInteractionInboxRepositoryAdapter adapter = new SlackInteractionInboxRepositoryAdapter(null, null, null);

        // when & then
        assertThatThrownBy(() -> adapter.recoverTimeoutProcessing(
                null,
                Instant.parse("2026-02-24T00:02:00Z"),
                Instant.parse("2026-02-24T00:05:00Z"),
                "timeout",
                3,
                100
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("interactionType은 비어 있을 수 없습니다.");
    }

    @Test
    void 완료된_inbox와_history를_같이_삭제한다() {
        // given
        SlackInteractionInbox oldProcessedInbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                "cleanup-processed",
                "{\"value\":1}"
        );
        oldProcessedInbox.claim(Instant.parse("2026-02-24T00:00:00Z"));
        SlackInteractionInboxHistory oldProcessedHistory = oldProcessedInbox.markProcessed(
                Instant.parse("2026-02-24T00:01:00Z")
        );
        SlackInteractionInbox savedProcessedInbox = slackInteractionInboxRepository.save(
                oldProcessedInbox,
                oldProcessedHistory
        );

        SlackInteractionInbox recentFailedInbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.VIEW_SUBMISSION,
                "cleanup-failed",
                "{\"value\":2}"
        );
        recentFailedInbox.claim(Instant.parse("2026-02-24T00:02:00Z"));
        SlackInteractionInboxHistory recentFailedHistory = recentFailedInbox.markFailed(
                Instant.parse("2026-02-24T00:09:00Z"),
                "failure",
                SlackInteractionFailureType.BUSINESS_INVARIANT
        );
        SlackInteractionInbox savedFailedInbox = slackInteractionInboxRepository.save(
                recentFailedInbox,
                recentFailedHistory
        );

        // when
        int deletedCount = slackInteractionInboxRepository.deleteCompletedBefore(
                Instant.parse("2026-02-24T00:05:00Z"),
                100
        );

        // then
        List<SlackInteractionInboxHistory> histories = jpaSlackInteractionInboxHistoryRepository.findAllDomains();
        assertAll(
                () -> assertThat(deletedCount).isEqualTo(1),
                () -> assertThat(jpaSlackInteractionInboxRepository.findDomainById(savedProcessedInbox.getId())).isEmpty(),
                () -> assertThat(jpaSlackInteractionInboxRepository.findDomainById(savedFailedInbox.getId())).isPresent(),
                () -> assertThat(histories)
                        .filteredOn(history -> history.getInboxId().equals(savedProcessedInbox.getId()))
                        .isEmpty(),
                () -> assertThat(histories)
                        .filteredOn(history -> history.getInboxId().equals(savedFailedInbox.getId()))
                        .hasSize(1)
        );
    }

    @Test
    void deleteCompletedBefore는_batch_크기만큼만_삭제한다() {
        // given
        SlackInteractionInbox firstInbox = createProcessedInbox("cleanup-batch-1", Instant.parse("2026-02-24T00:01:00Z"));
        SlackInteractionInbox secondInbox = createProcessedInbox("cleanup-batch-2", Instant.parse("2026-02-24T00:02:00Z"));

        // when
        int deletedCount = slackInteractionInboxRepository.deleteCompletedBefore(
                Instant.parse("2026-02-24T00:05:00Z"),
                1
        );

        // then
        assertAll(
                () -> assertThat(deletedCount).isEqualTo(1),
                () -> assertThat(jpaSlackInteractionInboxRepository.findDomainById(firstInbox.getId())).isEmpty(),
                () -> assertThat(jpaSlackInteractionInboxRepository.findDomainById(secondInbox.getId())).isPresent()
        );
    }

    private SlackInteractionInbox createProcessedInbox(String idempotencyKey, Instant processedAt) {
        SlackInteractionInbox inbox = SlackInteractionInbox.pending(
                SlackInteractionInboxType.BLOCK_ACTIONS,
                idempotencyKey,
                "{\"payload\":true}"
        );
        inbox.claim(processedAt.minusSeconds(30L));
        SlackInteractionInboxHistory history = inbox.markProcessed(processedAt);
        return slackInteractionInboxRepository.save(inbox, history);
    }
}
