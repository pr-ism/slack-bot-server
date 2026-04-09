package com.slack.bot.application.review.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.review.box.ReviewNotificationIdempotencyKeyGenerator;
import com.slack.bot.application.review.dto.ReviewNotificationPayload;
import com.slack.bot.application.worker.PollingHintPublisher;
import com.slack.bot.application.worker.PollingHintTarget;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutbox;
import com.slack.bot.infrastructure.review.box.out.ReviewNotificationOutboxStringField;
import com.slack.bot.infrastructure.review.box.out.repository.ReviewNotificationOutboxRepository;
import java.util.List;
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

    @Mock
    PollingHintPublisher pollingHintPublisher;

    ReviewNotificationOutboxEnqueuer enqueuer;

    @BeforeEach
    void setUp() {
        enqueuer = new ReviewNotificationOutboxEnqueuer(
                new ObjectMapper(),
                reviewNotificationOutboxRepository,
                new ReviewNotificationIdempotencyKeyGenerator(),
                new ReviewNotificationOutboxIdempotencyPayloadEncoder(new ObjectMapper()),
                pollingHintPublisher
        );
    }

    @Test
    void semantic_review_notification을_review_outbox에_enqueue한다() {
        // given
        ReviewNotificationPayload payload = new ReviewNotificationPayload(
                "repo",
                101L,
                42,
                "Fix bug",
                "https://github.com/pr/1",
                "author-gh",
                List.of("reviewer-gh-1"),
                List.of("reviewer-gh-1")
        );

        // when
        enqueuer.enqueueReviewNotification("SOURCE-1", 1L, "T1", "C1", payload);

        // then
        ArgumentCaptor<ReviewNotificationOutbox> captor = ArgumentCaptor.forClass(ReviewNotificationOutbox.class);
        verify(reviewNotificationOutboxRepository).enqueue(captor.capture());

        ReviewNotificationOutbox actual = captor.getValue();

        assertAll(
                () -> assertThat(actual.requiredProjectId()).isEqualTo(1L),
                () -> assertThat(actual.getTeamId()).isEqualTo("T1"),
                () -> assertThat(actual.getChannelId()).isEqualTo("C1"),
                () -> assertThat(actual.requiredPayloadJson()).contains("\"repositoryName\":\"repo\""),
                () -> assertThat(actual.getBlocksJson().isPresent()).isFalse(),
                () -> assertThat(actual.getIdempotencyKey()).hasSize(64)
        );
        verify(pollingHintPublisher).publish(PollingHintTarget.REVIEW_NOTIFICATION_OUTBOX);
    }

    @Test
    void 채널_블록_메시지_snapshot을_review_outbox에_enqueue한다() throws Exception {
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
                () -> assertThat(actual.requiredBlocksJson()).isEqualTo("[{\"type\":\"section\"}]"),
                () -> assertThat(actual.getAttachmentsJson().isPresent()).isFalse(),
                () -> assertThat(actual.getFallbackText().value()).isEqualTo("fallback"),
                () -> assertThat(actual.getIdempotencyKey()).hasSize(64)
        );
        verify(pollingHintPublisher).publish(PollingHintTarget.REVIEW_NOTIFICATION_OUTBOX);
    }

    @Test
    void attachments를_원형_그대로_보존해_enqueue한다() throws Exception {
        // given
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

        assertAll(
                () -> assertThat(captor.getValue().requiredBlocksJson())
                        .isEqualTo("[{\"type\":\"section\"}]"),
                () -> assertThat(captor.getValue().getAttachmentsJson().value()).isEqualTo(
                        "[{\"blocks\":[{\"type\":\"actions\"},{\"type\":\"context\"}]},{}]"
                )
        );
    }

    @Test
    void attachments가_null이면_attachmentsJson은_absent로_enqueue한다() throws Exception {
        // when
        enqueuer.enqueueChannelBlocks(
                "SOURCE-1",
                "T1",
                "C1",
                new ObjectMapper().readTree("[{\"type\":\"section\"}]"),
                null,
                "fallback"
        );

        // then
        ArgumentCaptor<ReviewNotificationOutbox> captor = ArgumentCaptor.forClass(ReviewNotificationOutbox.class);
        verify(reviewNotificationOutboxRepository).enqueue(captor.capture());

        assertThat(captor.getValue().getAttachmentsJson().isPresent()).isFalse();
    }

    @Test
    void fallbackText가_공백이면_fallbackText는_absent로_enqueue한다() throws Exception {
        // when
        enqueuer.enqueueChannelBlocks(
                "SOURCE-1",
                "T1",
                "C1",
                new ObjectMapper().readTree("[{\"type\":\"section\"}]"),
                " "
        );

        // then
        ArgumentCaptor<ReviewNotificationOutbox> captor = ArgumentCaptor.forClass(ReviewNotificationOutbox.class);
        verify(reviewNotificationOutboxRepository).enqueue(captor.capture());

        assertThat(captor.getValue().getFallbackText().isPresent()).isFalse();
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
        verify(reviewNotificationOutboxRepository, times(2)).enqueue(captor.capture());

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

        verify(reviewNotificationOutboxRepository, never()).enqueue(any());
    }

    @Test
    void semantic_payload가_null이면_예외를_던지고_enqueue하지_않는다() {
        // when & then
        assertThatThrownBy(() -> enqueuer.enqueueReviewNotification("SOURCE", 1L, "T1", "C1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload");

        verify(reviewNotificationOutboxRepository, never()).enqueue(any());
    }

    @Test
    void semantic_payload_직렬화에_실패하면_예외를_던지고_enqueue하지_않는다() throws Exception {
        // given
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ReviewNotificationOutboxEnqueuer failingEnqueuer = new ReviewNotificationOutboxEnqueuer(
                objectMapper,
                reviewNotificationOutboxRepository,
                new ReviewNotificationIdempotencyKeyGenerator(),
                new ReviewNotificationOutboxIdempotencyPayloadEncoder(objectMapper),
                pollingHintPublisher
        );
        ReviewNotificationPayload payload = new ReviewNotificationPayload(
                "repo",
                101L,
                42,
                "Fix bug",
                "https://github.com/pr/1",
                "author-gh",
                List.of("reviewer-gh-1"),
                List.of("reviewer-gh-1")
        );
        doThrow(new JsonMappingException(null, "serialize fail")).when(objectMapper).writeValueAsString(payload);

        // when & then
        assertThatThrownBy(() -> failingEnqueuer.enqueueReviewNotification("SOURCE", 1L, "T1", "C1", payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("직렬화");

        verify(reviewNotificationOutboxRepository, never()).enqueue(any());
        verify(pollingHintPublisher, never()).publish(any());
    }

    @Test
    void 의미있는_outbox_입력이_달라지면_다른_멱등키로_enqueue한다() throws Exception {
        // when
        enqueuer.enqueueChannelBlocks(
                "SOURCE-1",
                "T1",
                "C1",
                new ObjectMapper().readTree("[]"),
                "fallback"
        );
        enqueuer.enqueueChannelBlocks(
                "SOURCE-2",
                "T1",
                "C1",
                new ObjectMapper().readTree("[]"),
                "fallback"
        );

        // then
        ArgumentCaptor<ReviewNotificationOutbox> captor = ArgumentCaptor.forClass(ReviewNotificationOutbox.class);
        verify(reviewNotificationOutboxRepository, times(2)).enqueue(captor.capture());

        assertThat(captor.getAllValues().get(0).getIdempotencyKey())
                .isNotEqualTo(captor.getAllValues().get(1).getIdempotencyKey());
    }

    @Test
    void semantic_projectId가_달라지면_다른_멱등키로_enqueue한다() {
        // given
        ReviewNotificationPayload payload = new ReviewNotificationPayload(
                "repo",
                101L,
                42,
                "Fix bug",
                "https://github.com/pr/1",
                "author-gh",
                List.of("reviewer-gh-1"),
                List.of("reviewer-gh-1")
        );

        // when
        enqueuer.enqueueReviewNotification("SOURCE-1", 1L, "T1", "C1", payload);
        enqueuer.enqueueReviewNotification("SOURCE-1", 2L, "T1", "C1", payload);

        // then
        ArgumentCaptor<ReviewNotificationOutbox> captor = ArgumentCaptor.forClass(ReviewNotificationOutbox.class);
        verify(reviewNotificationOutboxRepository, times(2)).enqueue(captor.capture());

        assertThat(captor.getAllValues().get(0).getIdempotencyKey())
                .isNotEqualTo(captor.getAllValues().get(1).getIdempotencyKey());
    }
}
