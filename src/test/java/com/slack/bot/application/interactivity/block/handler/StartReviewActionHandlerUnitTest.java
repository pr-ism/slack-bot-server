package com.slack.bot.application.interactivity.block.handler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.interactivity.block.BlockActionType;
import com.slack.bot.application.interactivity.block.dto.BlockActionCommandDto;
import com.slack.bot.application.interactivity.block.handler.store.StartReviewMarkStore;
import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import com.slack.bot.application.interactivity.notification.NotificationDispatcher;
import com.slack.bot.application.interactivity.notification.ReviewReservationNotifier;
import com.slack.bot.application.interactivity.reply.SlackActionErrorNotifier;
import com.slack.bot.application.interactivity.reservation.AuthorResolver;
import com.slack.bot.application.interactivity.reservation.ReservationMetaResolver;
import com.slack.bot.application.interactivity.reservation.ReviewReservationCoordinator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
                reviewReservationCoordinator
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
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("cancel failed");

        verify(startReviewMarkStore).remove("T1:123:10:U2");
        verify(reviewReservationNotifier, never()).notifyStartNowToParticipants(any(), any(), any());
        verify(notificationDispatcher, never()).sendEphemeral(any(), any(), any(), any());
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
