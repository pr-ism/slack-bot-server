package com.slack.bot.application.interactivity.block.handler;

import com.slack.bot.application.interactivity.block.BlockActionHandler;
import com.slack.bot.application.interactivity.block.dto.BlockActionCommandDto;
import com.slack.bot.application.interactivity.block.dto.BlockActionOutcomeDto;
import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import com.slack.bot.application.interactivity.notification.ReviewReservationNotifier;
import com.slack.bot.application.interactivity.reservation.ReservationMetaResolver;
import com.slack.bot.application.interactivity.reservation.exception.ReservationMetaInvalidException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartReviewActionHandler implements BlockActionHandler {

    private final ReservationMetaResolver reservationMetaResolver;
    private final ReviewReservationNotifier reviewReservationNotifier;

    @Override
    public BlockActionOutcomeDto handle(BlockActionCommandDto command) {
        String metaJson = command.action().path("value").asText(null);

        if (metaJson == null || metaJson.isBlank()) {
            return BlockActionOutcomeDto.empty();
        }

        try {
            ReviewScheduleMetaDto meta = reservationMetaResolver.parseMeta(metaJson);

            reviewReservationNotifier.notifyStartNowToReviewee(
                    meta,
                    command.slackUserId(),
                    command.botToken()
            );
        } catch (ReservationMetaInvalidException e) {
            log.warn("리뷰 바로 시작 메타 파싱 실패", e);
        }

        return BlockActionOutcomeDto.empty();
    }
}
