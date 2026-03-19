package com.slack.bot.application.review.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.review.box.ReviewNotificationIdempotencyKeyGenerator;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import com.slack.bot.infrastructure.review.box.out.repository.ReviewNotificationOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewNotificationOutboxEnqueuerTest {

    @Mock
    ReviewNotificationOutboxRepository reviewNotificationOutboxRepository;

    ReviewNotificationOutboxEnqueuer enqueuer;

    @BeforeEach
    void setUp() {
        enqueuer = new ReviewNotificationOutboxEnqueuer(
                reviewNotificationOutboxRepository,
                new ReviewNotificationIdempotencyKeyGenerator()
        );
    }

    @Test
    void 채널_블록_메시지를_review_outbox에_enqueue한다() throws Exception {
        // when
        enqueuer.enqueueChannelBlocks(
                "SOURCE-1",
                "T1",
                "C1",
                new ObjectMapper().readTree("[{\"type\":\"section\"}]"),
                "fallback"
        );

        // then
        ArgumentCaptor<ReviewNotificationOutbox> captor = ArgumentCaptor.forClass(ReviewNotificationOutbox.class);
        verify(reviewNotificationOutboxRepository).enqueue(captor.capture());

        ReviewNotificationOutbox actual = captor.getValue();

        assertAll(
                () -> assertThat(actual.getTeamId()).isEqualTo("T1"),
                () -> assertThat(actual.getChannelId()).isEqualTo("C1"),
                () -> assertThat(actual.getBlocksJson()).isEqualTo("[{\"type\":\"section\"}]"),
                () -> assertThat(actual.getFallbackText()).isEqualTo("fallback"),
                () -> assertThat(actual.getIdempotencyKey()).hasSize(64)
        );
    }

    @Test
    void attachment_blocks를_포함한_메시지를_하나의_blocks_json으로_merge해_enqueue한다() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        // when
        enqueuer.enqueueChannelBlocks(
                "SOURCE-1",
                "T1",
                "C1",
                objectMapper.readTree("[{\"type\":\"section\"}]"),
                objectMapper.readTree("""
                        [
                          {"blocks":[{"type":"actions"},{"type":"context"}]},
                          {}
                        ]
                        """),
                "fallback"
        );

        // then
        ArgumentCaptor<ReviewNotificationOutbox> captor = ArgumentCaptor.forClass(ReviewNotificationOutbox.class);
        verify(reviewNotificationOutboxRepository).enqueue(captor.capture());

        assertThat(captor.getValue().getBlocksJson())
                .isEqualTo("[{\"type\":\"section\"},{\"type\":\"actions\"},{\"type\":\"context\"}]");
    }

    @Test
    void 동일한_입력이면_동일한_멱등키로_enqueue한다() throws Exception {
        // when
        enqueuer.enqueueChannelBlocks(
                "SOURCE-1",
                "T1",
                "C1",
                new ObjectMapper().readTree("[]"),
                "fallback"
        );
        enqueuer.enqueueChannelBlocks(
                "SOURCE-1",
                "T1",
                "C1",
                new ObjectMapper().readTree("[]"),
                "fallback"
        );

        // then
        ArgumentCaptor<ReviewNotificationOutbox> captor = ArgumentCaptor.forClass(ReviewNotificationOutbox.class);
        verify(reviewNotificationOutboxRepository, org.mockito.Mockito.times(2)).enqueue(captor.capture());

        assertThat(captor.getAllValues().get(0).getIdempotencyKey())
                .isEqualTo(captor.getAllValues().get(1).getIdempotencyKey());
    }

    @Test
    void sourceKey가_null이어도_멱등키를_생성해_enqueue한다() throws Exception {
        // when
        enqueuer.enqueueChannelBlocks(
                null,
                "T1",
                "C1",
                new ObjectMapper().readTree("[]"),
                "fallback"
        );

        // then
        ArgumentCaptor<ReviewNotificationOutbox> captor = ArgumentCaptor.forClass(ReviewNotificationOutbox.class);
        verify(reviewNotificationOutboxRepository).enqueue(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).hasSize(64);
    }

    @Test
    void blocks가_null이면_예외를_던지고_enqueue하지_않는다() {
        // when & then
        assertThatThrownBy(() -> enqueuer.enqueueChannelBlocks("SOURCE", "T1", "C1", null, "fallback"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blocks");

        verify(reviewNotificationOutboxRepository, never()).enqueue(org.mockito.ArgumentMatchers.any());
    }
}
