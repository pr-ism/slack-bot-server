package com.slack.bot.application.interactivity.view.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.api.model.block.ActionsBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.bot.domain.reservation.ReservationStatus;
import com.slack.bot.domain.reservation.ReviewReservation;
import com.slack.bot.domain.reservation.vo.ReservationPullRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewReservationBlocksViewFactoryTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant SCHEDULED_AT = Instant.parse("2024-01-02T01:30:00Z");

    private final Clock clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), KST);
    private final ReviewReservationBlocksViewFactory factory = new ReviewReservationBlocksViewFactory(clock);

    @Test
    void 예약_블록을_생성한다() {
        // given
        ReviewReservation reservation = createReservation();
        String headerText = "리뷰 예약 완료";

        // when
        List<LayoutBlock> blocks = factory.buildReservationBlocks(reservation, headerText);

        // then
        String expectedTime = "2024년 1월 2일 10시 30분";
        String expectedPullRequestLine = "<https://github.com/repo/pull/1|PR 제목>";

        assertAll(
                () -> assertThat(blocks).hasSize(2),
                () -> assertThat(blocks.getFirst())
                        .isInstanceOf(SectionBlock.class)
                        .extracting(block -> ((SectionBlock) block).getText().getText())
                        .asString()
                        .contains(headerText, expectedPullRequestLine, "리뷰 시작 시간: " + expectedTime),
                () -> assertThat(blocks.get(1))
                        .isInstanceOf(ActionsBlock.class)
                        .extracting(block -> ((ActionsBlock) block).getElements().size())
                        .isEqualTo(2)
        );
    }

    @Test
    void 취소_블록을_생성한다() {
        // given
        ReviewReservation reservation = createReservation();
        String headerText = "리뷰 예약 취소";

        // when
        List<LayoutBlock> blocks = factory.buildCancellationBlocks(reservation, headerText);

        // then
        String expectedTime = "2024년 1월 2일 10시 30분";

        assertAll(
                () -> assertThat(blocks).hasSize(2),
                () -> assertThat(blocks.getFirst())
                        .isInstanceOf(SectionBlock.class)
                        .extracting(block -> ((SectionBlock) block).getText().getText())
                        .asString()
                        .contains(headerText),
                () -> assertThat(blocks.get(1))
                        .isInstanceOf(SectionBlock.class)
                        .extracting(block -> ((SectionBlock) block).getText().getText())
                        .asString()
                        .contains("예약 시간: " + expectedTime)
        );
    }

    private ReviewReservation createReservation() {
        ReservationPullRequest pr = ReservationPullRequest.builder()
                .pullRequestId(1L)
                .pullRequestNumber(1)
                .pullRequestTitle("PR 제목")
                .pullRequestUrl("https://github.com/repo/pull/1")
                .build();

        return ReviewReservation.builder()
                .teamId("T1")
                .channelId("C1")
                .projectId(1L)
                .reservationPullRequest(pr)
                .authorSlackId("U1")
                .reviewerSlackId("U2")
                .scheduledAt(SCHEDULED_AT)
                .status(ReservationStatus.ACTIVE)
                .build();
    }
}
