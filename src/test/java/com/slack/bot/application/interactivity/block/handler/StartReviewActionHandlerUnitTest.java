package com.slack.bot.application.interactivity.block.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.interactivity.block.BlockActionType;
import com.slack.bot.application.interactivity.block.dto.BlockActionCommandDto;
import com.slack.bot.application.interactivity.block.dto.BlockActionOutcomeDto;
import com.slack.bot.application.interactivity.block.handler.store.StartReviewMarkStore;
import com.slack.bot.application.interactivity.client.exception.SlackBotMessageDispatchException;
import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import com.slack.bot.application.interactivity.notification.NotificationDispatcher;
import com.slack.bot.application.interactivity.notification.ReviewReservationNotifier;
import com.slack.bot.application.interactivity.publisher.ReviewInteractionEventPublisher;
import com.slack.bot.application.interactivity.reply.SlackActionErrorNotifier;
import com.slack.bot.application.interactivity.reservation.AuthorResolver;
import com.slack.bot.application.interactivity.reservation.ReservationMetaResolver;
import com.slack.bot.application.interactivity.reservation.ReviewReservationCoordinator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StartReviewActionHandlerUnitTest {

    @Mock
    AuthorResolver authorResolver;

    @Mock
    StartReviewMarkStore startReviewMarkStore;

    @Mock
    NotificationDispatcher notificationDispatcher;

    @Mock
    ReservationMetaResolver reservationMetaResolver;

    @Mock
    SlackActionErrorNotifier errorNotifier;

    @Mock
    ReviewReservationNotifier reviewReservationNotifier;

    @Mock
    ReviewReservationCoordinator reviewReservationCoordinator;

    @Mock
    ReviewInteractionEventPublisher reviewInteractionEventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 예약_취소_실패시_시작_마킹을_롤백한다() {
        // given
        Instant now = Instant.parse("2026-02-13T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        StartReviewActionHandler handler = new StartReviewActionHandler(
                clock,
                authorResolver,
                startReviewMarkStore,
                notificationDispatcher,
                reservationMetaResolver,
                errorNotifier,
                reviewReservationNotifier,
                reviewReservationCoordinator,
                reviewInteractionEventPublisher
        );

        ReviewScheduleMetaDto meta = ReviewScheduleMetaDto.builder()
                                                          .teamId("T1")
                                                          .channelId("C1")
                                                          .pullRequestId(10L)
                                                          .pullRequestNumber(10)
                                                          .pullRequestTitle("PR")
                                                          .pullRequestUrl("https://example.com/pr/10")
                                                          .authorGithubId("author-gh")
                                                          .authorSlackId("U1")
                                                          .reservationId("R1")
                                                          .projectId("123")
                                                          .build();
        BlockActionCommandDto command = commandWithMeta("meta-json", "U2");

        given(reservationMetaResolver.parseMeta("meta-json")).willReturn(meta);
        given(authorResolver.resolveAuthorSlackId(meta)).willReturn("U1");
        given(startReviewMarkStore.putIfAbsent("T1:123:10:U2", now)).willReturn(null);
        given(reviewReservationCoordinator.cancelActive("T1", 123L, "U2", 10L))
                .willThrow(new RuntimeException("cancel failed"));

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> handler.handle(command))
                        .isInstanceOf(RuntimeException.class)
                        .hasMessage("cancel failed"),
                () -> verify(startReviewMarkStore).remove("T1:123:10:U2"),
                () -> verify(reviewReservationNotifier, never()).notifyStartNowToParticipants(any(), any(), any()),
                () -> verify(notificationDispatcher, never()).sendEphemeral(any(), any(), any(), any())
        );
    }

    @Test
    void 시작_알림_전송이_SlackBotMessageDispatchException이면_예외를_삼키고_종료한다() {
        // given
        Instant now = Instant.parse("2026-02-13T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        StartReviewActionHandler handler = new StartReviewActionHandler(
                clock,
                authorResolver,
                startReviewMarkStore,
                notificationDispatcher,
                reservationMetaResolver,
                errorNotifier,
                reviewReservationNotifier,
                reviewReservationCoordinator,
                reviewInteractionEventPublisher
        );
        ReviewScheduleMetaDto meta = validMeta();
        BlockActionCommandDto command = commandWithMeta("meta-json", "U2");

        given(reservationMetaResolver.parseMeta("meta-json")).willReturn(meta);
        given(authorResolver.resolveAuthorSlackId(meta)).willReturn("U1");
        given(startReviewMarkStore.putIfAbsent("T1:123:10:U2", now)).willReturn(null);
        given(reviewReservationCoordinator.cancelActive("T1", 123L, "U2", 10L)).willReturn(Optional.empty());
        willThrow(new SlackBotMessageDispatchException("dispatch failed"))
                .given(reviewReservationNotifier)
                .notifyStartNowToParticipants(meta, "U2", "xoxb-test-token");

        // when
        BlockActionOutcomeDto actual = handler.handle(command);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(BlockActionOutcomeDto.empty()),
                () -> verify(notificationDispatcher, never()).sendEphemeral(any(), any(), any(), any()),
                () -> verify(reviewInteractionEventPublisher, never()).publish(any()),
                () -> verify(startReviewMarkStore, never()).remove("T1:123:10:U2")
        );
    }

    @Test
    void 시작_알림_전송이_일반_예외이면_예외를_삼키고_종료한다() {
        // given
        Instant now = Instant.parse("2026-02-13T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        StartReviewActionHandler handler = new StartReviewActionHandler(
                clock,
                authorResolver,
                startReviewMarkStore,
                notificationDispatcher,
                reservationMetaResolver,
                errorNotifier,
                reviewReservationNotifier,
                reviewReservationCoordinator,
                reviewInteractionEventPublisher
        );
        ReviewScheduleMetaDto meta = validMeta();
        BlockActionCommandDto command = commandWithMeta("meta-json", "U2");

        given(reservationMetaResolver.parseMeta("meta-json")).willReturn(meta);
        given(authorResolver.resolveAuthorSlackId(meta)).willReturn("U1");
        given(startReviewMarkStore.putIfAbsent("T1:123:10:U2", now)).willReturn(null);
        given(reviewReservationCoordinator.cancelActive("T1", 123L, "U2", 10L)).willReturn(Optional.empty());
        willThrow(new RuntimeException("notify failed"))
                .given(reviewReservationNotifier)
                .notifyStartNowToParticipants(meta, "U2", "xoxb-test-token");

        // when
        BlockActionOutcomeDto actual = handler.handle(command);

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo(BlockActionOutcomeDto.empty()),
                () -> verify(notificationDispatcher, never()).sendEphemeral(any(), any(), any(), any()),
                () -> verify(reviewInteractionEventPublisher, never()).publish(any()),
                () -> verify(startReviewMarkStore, never()).remove("T1:123:10:U2")
        );
    }

    private ReviewScheduleMetaDto validMeta() {
        return ReviewScheduleMetaDto.builder()
                                    .teamId("T1")
                                    .channelId("C1")
                                    .pullRequestId(10L)
                                    .pullRequestNumber(10)
                                    .pullRequestTitle("PR")
                                    .pullRequestUrl("https://example.com/pr/10")
                                    .authorGithubId("author-gh")
                                    .authorSlackId("U1")
                                    .reservationId("R1")
                                    .projectId("123")
                                    .build();
    }

    private BlockActionCommandDto commandWithMeta(String metaJson, String reviewerSlackId) {
        ObjectNode action = objectMapper.createObjectNode().put("value", metaJson);

        return new BlockActionCommandDto(
                objectMapper.createObjectNode(),
                action,
                BlockActionType.START_REVIEW.value(),
                BlockActionType.START_REVIEW,
                "T1",
                "C1",
                reviewerSlackId,
                "xoxb-test-token"
        );
    }
}
