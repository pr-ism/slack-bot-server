package com.slack.bot.infrastructure.interactivity.block;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.slack.api.model.block.Blocks;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.composition.BlockCompositions;
import com.slack.api.model.block.element.BlockElements;
import com.slack.api.util.json.GsonFactory;
import com.slack.bot.application.interactivity.block.BlockActionType;
import com.slack.bot.application.interactivity.block.ReviewReservationBlockCreator;
import com.slack.bot.application.interactivity.block.ReviewReservationBlockType;
import com.slack.bot.application.interactivity.block.dto.ReviewReservationMessageDto;
import com.slack.bot.domain.reservation.ReviewReservation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SlackSdkReviewReservationBlockCreator implements ReviewReservationBlockCreator {

    private static final Gson SNAKE_CASE_GSON = GsonFactory.createSnakeCase();

    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Override
    public ReviewReservationMessageDto create(
            ReviewReservation reservation,
            String headerText,
            ReviewReservationBlockType type
    ) {
        if (type.isReservation()) {
            return createReservationMessage(reservation, headerText);
        }
        if (type.isCancellation()) {
            return createCancellationMessage(reservation, headerText);
        }

        throw new IllegalArgumentException("지원하지 않는 리뷰 예약 블록 타입입니다.");
    }

    private List<LayoutBlock> buildReservationBlocks(ReviewReservation reservation, String headerText) {
        String scheduledAtText = formatReservationTime(reservation.getScheduledAt());
        String pullRequestLine = formatPullRequestLine(reservation);
        String body = buildReservationBody(headerText, pullRequestLine, scheduledAtText);

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

    private List<LayoutBlock> buildCancellationBlocks(ReviewReservation reservation, String headerText) {
        String scheduledAtText = formatReservationTime(reservation.getScheduledAt());
        String pullRequestLine = formatPullRequestLine(reservation);
        String body = buildCancellationBody(pullRequestLine, scheduledAtText);

        return Blocks.asBlocks(
                Blocks.section(section -> section.text(BlockCompositions.markdownText(headerText))),
                Blocks.section(section -> section.text(BlockCompositions.markdownText(body)))
        );
    }

    private String formatReservationTime(Instant scheduledAt) {
        ZonedDateTime zonedDateTime = scheduledAt.atZone(clock.getZone());
        return String.format(
                "%d년 %d월 %d일 %02d시 %02d분",
                zonedDateTime.getYear(),
                zonedDateTime.getMonthValue(),
                zonedDateTime.getDayOfMonth(),
                zonedDateTime.getHour(),
                zonedDateTime.getMinute()
        );
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

    private String buildReservationBody(String headerText, String pullRequestLine, String scheduledAtText) {
        return headerText + "\n" + pullRequestLine + "\n리뷰 시작 시간: " + scheduledAtText +
                "\n취소/변경은 아래 버튼으로만 가능합니다.";
    }

    private String buildCancellationBody(String pullRequestLine, String scheduledAtText) {
        return pullRequestLine + "\n예약 시간: " + scheduledAtText;
    }

    private ReviewReservationMessageDto createReservationMessage(
            ReviewReservation reservation,
            String headerText
    ) {
        List<LayoutBlock> blocks = buildReservationBlocks(reservation, headerText);
        JsonNode blockNodes = toJsonNode(blocks);

        return ReviewReservationMessageDto.ofBlocks(blockNodes, "리뷰 예약 안내");
    }

    private ReviewReservationMessageDto createCancellationMessage(
            ReviewReservation reservation,
            String headerText
    ) {
        List<LayoutBlock> blocks = buildCancellationBlocks(reservation, headerText);
        return ReviewReservationMessageDto.ofBlocks(
                toJsonNode(blocks),
                "리뷰 예약 취소 안내"
        );
    }

    private JsonNode toJsonNode(Object value) {
        String json = SNAKE_CASE_GSON.toJson(value);

        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Slack 블록 JSON 변환 실패", e);
        }
    }
}
