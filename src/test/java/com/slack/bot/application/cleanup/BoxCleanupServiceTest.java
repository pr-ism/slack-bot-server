package com.slack.bot.application.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.slack.bot.infrastructure.interaction.box.in.repository.SlackInteractionInboxRepository;
import com.slack.bot.infrastructure.interaction.box.out.repository.SlackNotificationOutboxRepository;
import com.slack.bot.infrastructure.review.box.in.repository.ReviewRequestInboxRepository;
import com.slack.bot.infrastructure.review.box.out.repository.ReviewNotificationOutboxRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BoxCleanupServiceTest {

    @Mock
    SlackInteractionInboxRepository slackInteractionInboxRepository;

    @Mock
    SlackNotificationOutboxRepository slackNotificationOutboxRepository;

    @Mock
    ReviewRequestInboxRepository reviewRequestInboxRepository;

    @Mock
    ReviewNotificationOutboxRepository reviewNotificationOutboxRepository;

    @InjectMocks
    BoxCleanupService boxCleanupService;

    @Test
    void 완료된_box를_모두_정리하고_집계결과를_반환한다() {
        // given
        Instant completedBefore = Instant.parse("2026-04-13T00:00:00Z");
        given(slackInteractionInboxRepository.deleteCompletedBefore(completedBefore, 100)).willReturn(1);
        given(slackNotificationOutboxRepository.deleteCompletedBefore(completedBefore, 100)).willReturn(2);
        given(reviewRequestInboxRepository.deleteCompletedBefore(completedBefore, 100)).willReturn(3);
        given(reviewNotificationOutboxRepository.deleteCompletedBefore(completedBefore, 100)).willReturn(4);

        // when
        BoxCleanupService.CleanupResult result = boxCleanupService.cleanCompletedBoxes(completedBefore, 100);

        // then
        assertAll(
                () -> assertThat(result.interactionInboxDeleted()).isEqualTo(1),
                () -> assertThat(result.interactionOutboxDeleted()).isEqualTo(2),
                () -> assertThat(result.reviewInboxDeleted()).isEqualTo(3),
                () -> assertThat(result.reviewOutboxDeleted()).isEqualTo(4),
                () -> assertThat(result.totalDeleted()).isEqualTo(10)
        );
        verify(slackInteractionInboxRepository).deleteCompletedBefore(completedBefore, 100);
        verify(slackNotificationOutboxRepository).deleteCompletedBefore(completedBefore, 100);
        verify(reviewRequestInboxRepository).deleteCompletedBefore(completedBefore, 100);
        verify(reviewNotificationOutboxRepository).deleteCompletedBefore(completedBefore, 100);
    }

    @Test
    void completed_before가_null이면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> boxCleanupService.cleanCompletedBoxes(null, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("completedBefore는 비어 있을 수 없습니다.");
    }

    @Test
    void delete_batch_size가_0이하면_예외를_던진다() {
        // when & then
        assertThatThrownBy(() -> boxCleanupService.cleanCompletedBoxes(Instant.parse("2026-04-13T00:00:00Z"), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("deleteBatchSize는 0보다 커야 합니다.");
    }
}
