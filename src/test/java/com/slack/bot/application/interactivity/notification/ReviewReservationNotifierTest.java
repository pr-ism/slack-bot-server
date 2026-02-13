package com.slack.bot.application.interactivity.notification;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.bot.application.interactivity.block.ReviewReservationBlockCreator;
import com.slack.bot.application.interactivity.block.ReviewReservationBlockType;
import com.slack.bot.application.interactivity.block.dto.ReviewReservationMessageDto;
import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import com.slack.bot.domain.reservation.ReviewReservation;
import com.slack.bot.domain.reservation.ReservationStatus;
import com.slack.bot.domain.reservation.vo.ReservationPullRequest;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class ReviewReservationNotifierTest {

    private static final Instant FIXED_NOW = Instant.parse("2024-01-01T10:00:00Z");

    @Mock
    NotificationDispatcher notificationDispatcher;

    @Mock
    ReviewReservationBlockCreator reservationBlockCreator;

    @Mock
    ReviewNotificationMessageFormatter messageFormatter;

    ReviewReservationNotifier reviewReservationNotifier;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        reviewReservationNotifier = new ReviewReservationNotifier(
                clock,
                notificationDispatcher,
                reservationBlockCreator,
                messageFormatter
        );
    }

    @Test
    void 리뷰_예약_블록_메시지를_전송한다() {
        // given
        String token = "xoxb-token";
        String teamId = "T1";
        String channelId = "C1";
        String slackUserId = "U1";
        String headerText = "리뷰 예약 완료";
        ReviewReservation reservation = createReservation();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode blocks = mapper.createArrayNode();
        String fallbackText = "리뷰가 예약되었습니다";
        ReviewReservationMessageDto message = ReviewReservationMessageDto.ofBlocks(blocks, fallbackText);

        given(reservationBlockCreator.create(
                reservation,
                headerText,
                ReviewReservationBlockType.RESERVATION
        )).willReturn(message);

        // when
        reviewReservationNotifier.sendReservationBlock(token, teamId, channelId, slackUserId, reservation, headerText);

        // then
        assertAll(
                () -> verify(reservationBlockCreator).create(reservation, headerText, ReviewReservationBlockType.RESERVATION),
                () -> verify(notificationDispatcher).sendBlock(teamId, token, channelId, slackUserId, blocks, fallbackText)
        );
    }

    @Test
    void 리뷰_예약_취소_메시지를_전송한다() {
        // given
        String token = "xoxb-token";
        String channelId = "C1";
        String slackUserId = "U1";
        ReviewReservation reservation = createReservation();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode blocks = mapper.createArrayNode();
        String fallbackText = "리뷰 예약이 취소되었습니다";
        ReviewReservationMessageDto message = ReviewReservationMessageDto.ofBlocks(blocks, fallbackText);

        given(reservationBlockCreator.create(
                reservation,
                "*리뷰 예약을 취소했습니다.*",
                ReviewReservationBlockType.CANCELLATION
        )).willReturn(message);

        // when
        reviewReservationNotifier.sendCancellationMessage(token, channelId, slackUserId, reservation);

        // then
        assertAll(
                () -> verify(reservationBlockCreator).create(reservation, "*리뷰 예약을 취소했습니다.*", ReviewReservationBlockType.CANCELLATION),
                () -> verify(notificationDispatcher).sendBlock(
                        reservation.getTeamId(),
                        token,
                        channelId,
                        slackUserId,
                        blocks,
                        fallbackText
                )
        );
    }

    @Test
    void 즉시_시작_알림을_전송한다() {
        // given
        ReviewScheduleMetaDto meta = createMeta();
        String reviewerId = "U-REVIEWER";
        String token = "xoxb-token";
        String authorSlackId = "U-AUTHOR";
        String formattedText = "<@U-AUTHOR> 지금부터 <@U-REVIEWER> 님이 리뷰를 시작합니다.\nPR #1";

        given(messageFormatter.buildStartNowText(authorSlackId, reviewerId, meta))
                .willReturn(formattedText);

        // when
        reviewReservationNotifier.notifyStartNow(meta, reviewerId, token, authorSlackId);

        // then
        assertAll(
                () -> verify(messageFormatter).buildStartNowText(authorSlackId, reviewerId, meta),
                () -> verify(notificationDispatcher, times(2)).sendDirectMessageIfEnabled(
                        eq(meta.teamId()),
                        eq(token),
                        anyString(),
                        eq(formattedText)
                ),
                () -> verify(notificationDispatcher).sendDirectMessageIfEnabled(meta.teamId(), token, authorSlackId, formattedText),
                () -> verify(notificationDispatcher).sendDirectMessageIfEnabled(meta.teamId(), token, reviewerId, formattedText)
        );
    }

    @Test
    void meta가_null이면_즉시_시작_알림을_전송하지_않는다() {
        // given
        String reviewerId = "U-REVIEWER";
        String token = "xoxb-token";
        String authorSlackId = "U-AUTHOR";

        // when
        reviewReservationNotifier.notifyStartNow(null, reviewerId, token, authorSlackId);

        // then
        assertAll(
                () -> verify(messageFormatter, never()).buildStartNowText(anyString(), anyString(), any()),
                () -> verify(notificationDispatcher, never()).sendDirectMessageIfEnabled(anyString(), anyString(), anyString(), anyString())
        );
    }

    @Test
    void authorSlackId가_null이면_즉시_시작_알림을_전송하지_않는다() {
        // given
        ReviewScheduleMetaDto meta = createMeta();
        String reviewerId = "U-REVIEWER";
        String token = "xoxb-token";

        // when
        reviewReservationNotifier.notifyStartNow(meta, reviewerId, token, null);

        // then
        assertAll(
                () -> verify(messageFormatter, never()).buildStartNowText(anyString(), anyString(), any()),
                () -> verify(notificationDispatcher, never()).sendDirectMessageIfEnabled(anyString(), anyString(), anyString(), anyString())
        );
    }

    @Test
    void authorSlackId가_빈문자열이면_즉시_시작_알림을_전송하지_않는다() {
        // given
        ReviewScheduleMetaDto meta = createMeta();
        String reviewerId = "U-REVIEWER";
        String token = "xoxb-token";

        // when
        reviewReservationNotifier.notifyStartNow(meta, reviewerId, token, "");

        // then
        assertAll(
                () -> verify(messageFormatter, never()).buildStartNowText(anyString(), anyString(), any()),
                () -> verify(notificationDispatcher, never()).sendDirectMessageIfEnabled(anyString(), anyString(), anyString(), anyString())
        );
    }

    @Test
    void reviewerId가_비어_있으면_리뷰_시작_알림을_전송하지_않는다() {
        // given
        ReviewScheduleMetaDto meta = createMeta();
        String token = "xoxb-token";
        String authorSlackId = "U-AUTHOR";

        // when
        reviewReservationNotifier.notifyStartNow(meta, null, token, authorSlackId);

        // then
        assertAll(
                () -> verify(messageFormatter, never()).buildStartNowText(anyString(), anyString(), any()),
                () -> verify(notificationDispatcher, never()).sendDirectMessageIfEnabled(anyString(), anyString(), anyString(), anyString())
        );
    }

    @Test
    void reviewerId가_빈문자열이면_즉시_시작_알림을_전송하지_않는다() {
        // given
        ReviewScheduleMetaDto meta = createMeta();
        String token = "xoxb-token";
        String authorSlackId = "U-AUTHOR";

        // when
        reviewReservationNotifier.notifyStartNow(meta, "", token, authorSlackId);

        // then
        assertAll(
                () -> verify(messageFormatter, never()).buildStartNowText(anyString(), anyString(), any()),
                () -> verify(notificationDispatcher, never()).sendDirectMessageIfEnabled(anyString(), anyString(), anyString(), anyString())
        );
    }

    @Test
    void 예약_알림을_전송한다() {
        // given
        ReviewScheduleMetaDto meta = createMeta();
        String reviewerId = "U-REVIEWER";
        Instant scheduledAt = FIXED_NOW.plusSeconds(3600);
        String token = "xoxb-token";
        String authorSlackId = "U-AUTHOR";
        String formattedText = "<@U-AUTHOR> <@U-REVIEWER> 님이 1시간 뒤에 리뷰를 시작하겠습니다.\nPR #1";

        given(messageFormatter.buildScheduledText(
                eq(authorSlackId),
                eq(reviewerId),
                any(),
                any(),
                eq(meta)
        )).willReturn(formattedText);

        // when
        reviewReservationNotifier.notifyScheduled(meta, reviewerId, scheduledAt, token, authorSlackId);

        // then
        assertAll(
                () -> verify(messageFormatter).buildScheduledText(
                        eq(authorSlackId),
                        eq(reviewerId),
                        any(),
                        any(),
                        eq(meta)
                ),
                () -> verify(notificationDispatcher, times(2)).sendDirectMessageIfEnabled(
                        eq(meta.teamId()),
                        eq(token),
                        anyString(),
                        eq(formattedText)
                ),
                () -> verify(notificationDispatcher).sendDirectMessageIfEnabled(meta.teamId(), token, authorSlackId, formattedText),
                () -> verify(notificationDispatcher).sendDirectMessageIfEnabled(meta.teamId(), token, reviewerId, formattedText)
        );
    }

    @Test
    void meta가_null이면_예약_알림을_전송하지_않는다() {
        // given
        String reviewerId = "U-REVIEWER";
        Instant scheduledAt = FIXED_NOW.plusSeconds(3600);
        String token = "xoxb-token";
        String authorSlackId = "U-AUTHOR";

        // when
        reviewReservationNotifier.notifyScheduled(null, reviewerId, scheduledAt, token, authorSlackId);

        // then
        assertAll(
                () -> verify(messageFormatter, never()).buildScheduledText(anyString(), anyString(), any(), any(), any()),
                () -> verify(notificationDispatcher, never()).sendDirectMessageIfEnabled(anyString(), anyString(), anyString(), anyString())
        );
    }

    @Test
    void authorSlackId가_null이면_예약_알림을_전송하지_않는다() {
        // given
        ReviewScheduleMetaDto meta = createMeta();
        String reviewerId = "U-REVIEWER";
        Instant scheduledAt = FIXED_NOW.plusSeconds(3600);
        String token = "xoxb-token";

        // when
        reviewReservationNotifier.notifyScheduled(meta, reviewerId, scheduledAt, token, null);

        // then
        assertAll(
                () -> verify(messageFormatter, never()).buildScheduledText(anyString(), anyString(), any(), any(), any()),
                () -> verify(notificationDispatcher, never()).sendDirectMessageIfEnabled(anyString(), anyString(), anyString(), anyString())
        );
    }

    @Test
    void authorSlackId가_빈문자열이면_예약_알림을_전송하지_않는다() {
        // given
        ReviewScheduleMetaDto meta = createMeta();
        String reviewerId = "U-REVIEWER";
        Instant scheduledAt = FIXED_NOW.plusSeconds(3600);
        String token = "xoxb-token";

        // when
        reviewReservationNotifier.notifyScheduled(meta, reviewerId, scheduledAt, token, "");

        // then
        assertAll(
                () -> verify(messageFormatter, never()).buildScheduledText(anyString(), anyString(), any(), any(), any()),
                () -> verify(notificationDispatcher, never()).sendDirectMessageIfEnabled(anyString(), anyString(), anyString(), anyString())
        );
    }

    @Test
    void reviewerId가_비어_있으면_리뷰_예약_알림을_전송하지_않는다() {
        // given
        ReviewScheduleMetaDto meta = createMeta();
        Instant scheduledAt = FIXED_NOW.plusSeconds(3600);
        String token = "xoxb-token";
        String authorSlackId = "U-AUTHOR";

        // when
        reviewReservationNotifier.notifyScheduled(meta, null, scheduledAt, token, authorSlackId);

        // then
        assertAll(
                () -> verify(messageFormatter, never()).buildScheduledText(anyString(), anyString(), any(), any(), any()),
                () -> verify(notificationDispatcher, never()).sendDirectMessageIfEnabled(anyString(), anyString(), anyString(), anyString())
        );
    }

    @Test
    void reviewerId가_빈문자열이면_예약_알림을_전송하지_않는다() {
        // given
        ReviewScheduleMetaDto meta = createMeta();
        Instant scheduledAt = FIXED_NOW.plusSeconds(3600);
        String token = "xoxb-token";
        String authorSlackId = "U-AUTHOR";

        // when
        reviewReservationNotifier.notifyScheduled(meta, "", scheduledAt, token, authorSlackId);

        // then
        assertAll(
                () -> verify(messageFormatter, never()).buildScheduledText(anyString(), anyString(), any(), any(), any()),
                () -> verify(notificationDispatcher, never()).sendDirectMessageIfEnabled(anyString(), anyString(), anyString(), anyString())
        );
    }

    @Test
    void scheduledAt이_비어_있으면_예약_알림을_전송하지_않는다() {
        // given
        ReviewScheduleMetaDto meta = createMeta();
        String reviewerId = "U-REVIEWER";
        String token = "xoxb-token";
        String authorSlackId = "U-AUTHOR";

        // when
        reviewReservationNotifier.notifyScheduled(meta, reviewerId, null, token, authorSlackId);

        // then
        assertAll(
                () -> verify(messageFormatter, never()).buildScheduledText(anyString(), anyString(), any(), any(), any()),
                () -> verify(notificationDispatcher, never()).sendDirectMessageIfEnabled(anyString(), anyString(), anyString(), anyString())
        );
    }

    @Test
    void 예약_블록을_DM과_에페메랄_설정에_따라_전송한다() {
        // given
        String token = "xoxb-token";
        String teamId = "T1";
        String channelId = "C1";
        String slackUserId = "U1";
        String headerText = "리뷰 예약 완료";
        ReviewReservation reservation = createReservationWith("Fix bug", 1, FIXED_NOW.plusSeconds(3600));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode blocks = mapper.createArrayNode();
        String fallbackText = "fallback";
        ReviewReservationMessageDto message = ReviewReservationMessageDto.ofBlocks(blocks, fallbackText);

        given(reservationBlockCreator.create(
                reservation,
                headerText,
                ReviewReservationBlockType.RESERVATION
        )).willReturn(message);

        // when
        reviewReservationNotifier.sendReservationBlockToDmAndEphemeral(
                token,
                teamId,
                channelId,
                slackUserId,
                reservation,
                headerText
        );

        // then
        assertAll(
                () -> verify(reservationBlockCreator).create(reservation, headerText, ReviewReservationBlockType.RESERVATION),
                () -> verify(notificationDispatcher).sendReservationBlockBySettingOrDefault(
                        eq(token),
                        eq(teamId),
                        eq(channelId),
                        eq(slackUserId),
                        eq(blocks),
                        eq(fallbackText),
                        eq("리뷰 예약 완료\nFix bug\n리뷰 시작 시간: 2024년 1월 1일 11시 00분")
                )
        );
    }

    @Test
    void 예약_블록을_DM으로만_전송한다() {
        // given
        String token = "xoxb-token";
        String slackUserId = "U1";
        String headerText = "리뷰 예약 완료";
        ReviewReservation reservation = createReservation();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode blocks = mapper.createArrayNode();
        String fallbackText = "fallback";
        ReviewReservationMessageDto message = ReviewReservationMessageDto.ofBlocks(blocks, fallbackText);

        given(reservationBlockCreator.create(
                reservation,
                headerText,
                ReviewReservationBlockType.RESERVATION
        )).willReturn(message);

        // when
        reviewReservationNotifier.sendReservationBlockToDirectMessageOnly(token, slackUserId, reservation, headerText);

        // then
        assertAll(
                () -> verify(reservationBlockCreator).create(reservation, headerText, ReviewReservationBlockType.RESERVATION),
                () -> verify(notificationDispatcher).sendBlockToDirectMessageOnly(token, slackUserId, blocks, fallbackText)
        );
    }

    @Test
    void 중복_예약_안내를_DM과_에페메랄로_전송한다() {
        // given
        String token = "xoxb-token";
        String channelId = "C1";
        String slackUserId = "U1";
        ReviewReservation reservation = createReservationWith("Fix bug", 1, FIXED_NOW.plusSeconds(3600));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode blocks = mapper.createArrayNode();
        String fallbackText = "fallback";
        ReviewReservationMessageDto message = ReviewReservationMessageDto.ofBlocks(blocks, fallbackText);

        given(reservationBlockCreator.create(
                reservation,
                "이미 이 PR 리뷰를 예약했습니다.",
                ReviewReservationBlockType.RESERVATION
        )).willReturn(message);

        // when
        reviewReservationNotifier.sendDuplicateReservationNoticeToDmAndEphemeral(
                token,
                channelId,
                slackUserId,
                reservation
        );

        // then
        assertAll(
                () -> verify(notificationDispatcher).sendEphemeral(
                        token,
                        channelId,
                        slackUserId,
                        "이미 이 PR 리뷰를 예약했습니다."
                ),
                () -> verify(notificationDispatcher).sendBlockToDirectMessageOnly(
                        token,
                        slackUserId,
                        blocks,
                        fallbackText
                )
        );
    }

    @Test
    void 예약_시간이_지났으면_중복_안내에_이미_시작된_문구를_사용한다() {
        // given
        String token = "xoxb-token";
        String channelId = "C1";
        String slackUserId = "U1";
        ReviewReservation reservation = createReservationWith("Fix bug", 1, FIXED_NOW.minusSeconds(60));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode blocks = mapper.createArrayNode();
        String fallbackText = "fallback";
        ReviewReservationMessageDto message = ReviewReservationMessageDto.ofBlocks(blocks, fallbackText);

        given(reservationBlockCreator.create(
                reservation,
                "이미 리뷰 시작 시간이 되어 새로 예약할 수 없습니다.",
                ReviewReservationBlockType.RESERVATION
        )).willReturn(message);

        // when
        reviewReservationNotifier.sendDuplicateReservationNoticeToDmAndEphemeral(
                token,
                channelId,
                slackUserId,
                reservation
        );

        // then
        verify(notificationDispatcher).sendEphemeral(
                token,
                channelId,
                slackUserId,
                "이미 리뷰 시작 시간이 되어 새로 예약할 수 없습니다."
        );
    }

    @Test
    void PR_제목이_비어_있으면_PR_번호로_예약_안내_문구를_구성한다() {
        // given
        String token = "xoxb-token";
        String teamId = "T1";
        String channelId = "C1";
        String slackUserId = "U1";
        String headerText = "리뷰 예약 완료";
        ReviewReservation reservation = createReservationWith("Valid title", 42, FIXED_NOW.plusSeconds(3600));
        setPullRequestTitle(reservation, " ");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode blocks = mapper.createArrayNode();
        String fallbackText = "fallback";
        ReviewReservationMessageDto message = ReviewReservationMessageDto.ofBlocks(blocks, fallbackText);

        given(reservationBlockCreator.create(
                reservation,
                headerText,
                ReviewReservationBlockType.RESERVATION
        )).willReturn(message);

        // when
        reviewReservationNotifier.sendReservationBlockToDmAndEphemeral(
                token,
                teamId,
                channelId,
                slackUserId,
                reservation,
                headerText
        );

        // then
        verify(notificationDispatcher).sendReservationBlockBySettingOrDefault(
                eq(token),
                eq(teamId),
                eq(channelId),
                eq(slackUserId),
                eq(blocks),
                eq(fallbackText),
                eq("리뷰 예약 완료\nPR #42\n리뷰 시작 시간: 2024년 1월 1일 11시 00분")
        );
    }

    private ReviewReservation createReservation() {
        return createReservationWith("Fix bug", 1, FIXED_NOW.plusSeconds(3600));
    }

    private ReviewReservation createReservationWith(String title, int pullRequestNumber, Instant scheduledAt) {
        return ReviewReservation.builder()
                .teamId("T1")
                .channelId("C1")
                .projectId(1L)
                .reservationPullRequest(ReservationPullRequest.builder()
                        .pullRequestId(1L)
                        .pullRequestNumber(pullRequestNumber)
                        .pullRequestTitle(title)
                        .pullRequestUrl("https://github.com/org/repo/pull/1")
                        .build())
                .authorSlackId("U-AUTHOR")
                .reviewerSlackId("U-REVIEWER")
                .scheduledAt(scheduledAt)
                .status(ReservationStatus.ACTIVE)
                .build();
    }

    private ReviewScheduleMetaDto createMeta() {
        return ReviewScheduleMetaDto.builder()
                                    .teamId("T1")
                                    .channelId("C1")
                                    .pullRequestId(1L)
                                    .pullRequestNumber(1)
                                    .pullRequestTitle("Fix bug")
                                    .pullRequestUrl("https://github.com/org/repo/pull/1")
                                    .authorGithubId("author")
                                    .authorSlackId("U-AUTHOR")
                                    .reservationId("100")
                                    .projectId("1")
                                    .build();
    }

    private void setPullRequestTitle(ReviewReservation reservation, String pullRequestTitle) {
        try {
            Field titleField = ReservationPullRequest.class.getDeclaredField("pullRequestTitle");
            titleField.setAccessible(true);
            titleField.set(reservation.getReservationPullRequest(), pullRequestTitle);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("테스트 PR 제목 설정 실패", e);
        }
    }
}
