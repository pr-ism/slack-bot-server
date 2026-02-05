package com.slack.bot.application.interactivity.view.factory;

import com.slack.api.model.block.Blocks;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.composition.BlockCompositions;
import com.slack.api.model.block.element.BlockElements;
import com.slack.bot.application.interactivity.block.BlockActionType;
import com.slack.bot.domain.reservation.ReviewReservation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewReservationBlocksViewFactory {

    private final Clock clock;

    public List<LayoutBlock> buildReservationBlocks(
            ReviewReservation reservation,
            String headerText
    ) {
        String scheduledAtText = formatReservationTime(reservation.getScheduledAt());
        String pullRequestLine = formatPullRequestLine(reservation);
        String body = headerText + "\n" + pullRequestLine + "\n리뷰 시작 시간: " + scheduledAtText +
                "\n취소/변경은 아래 버튼으로만 가능합니다.";

        return Blocks.asBlocks(
                Blocks.section(section -> section.text(BlockCompositions.markdownText(body))),
                Blocks.actions(actions -> actions.elements(List.of(
                        BlockElements.button(button -> button
                                .text(BlockCompositions.plainText("리뷰 예약 취소"))
                                .style("danger")
                                .actionId(BlockActionType.CANCEL_REVIEW_RESERVATION.value())
                                .value(String.valueOf(reservation.getId()))),
                        BlockElements.button(button -> button
                                .text(BlockCompositions.plainText("리뷰 예약 시간 변경"))
                                .actionId(BlockActionType.CHANGE_REVIEW_RESERVATION.value())
                                .value(String.valueOf(reservation.getId())))
                )))
        );
    }

    public List<LayoutBlock> buildCancellationBlocks(
            ReviewReservation reservation,
            String headerText
    ) {
        String scheduledAtText = formatReservationTime(reservation.getScheduledAt());
        String pullRequestLine = formatPullRequestLine(reservation);
        String body = pullRequestLine + "\n예약 시간: " + scheduledAtText;

        return Blocks.asBlocks(
                Blocks.section(section -> section.text(BlockCompositions.markdownText(headerText))),
                Blocks.section(section -> section.text(BlockCompositions.markdownText(body)))
        );
    }

    private String formatReservationTime(Instant when) {
        ZonedDateTime whenKst = when.atZone(clock.getZone());
        return String.format(
                "%d년 %d월 %d일 %02d시 %02d분",
                whenKst.getYear(),
                whenKst.getMonthValue(),
                whenKst.getDayOfMonth(),
                whenKst.getHour(),
                whenKst.getMinute());
    }

    private String formatPullRequestLine(ReviewReservation reservation) {
        String title = reservation.getReservationPullRequest().getPullRequestTitle();
        String url = reservation.getReservationPullRequest().getPullRequestUrl();
        if (url != null && !url.isBlank() && title != null && !title.isBlank()) {
            return "<" + url + "|" + title + ">";
        }
        if (url != null && !url.isBlank()) {
            return url;
        }
        int number = reservation.getReservationPullRequest().getPullRequestNumber();

        return "PR #" + number;
    }
}
