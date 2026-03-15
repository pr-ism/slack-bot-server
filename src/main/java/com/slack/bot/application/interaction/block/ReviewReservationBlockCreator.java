package com.slack.bot.application.interaction.block;

import com.slack.bot.application.interaction.block.dto.ReviewReservationMessageDto;
import com.slack.bot.domain.reservation.ReviewReservation;

public interface ReviewReservationBlockCreator {

    ReviewReservationMessageDto create(
            ReviewReservation reservation,
            String headerText,
            ReviewReservationBlockType type
    );
}
