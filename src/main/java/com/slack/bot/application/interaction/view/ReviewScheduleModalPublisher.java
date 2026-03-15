package com.slack.bot.application.interaction.view;

import com.slack.api.model.view.View;
import com.slack.bot.application.interaction.reply.dto.response.SlackActionResponse;
import com.slack.bot.application.interaction.view.factory.ReviewReservationTimeViewFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewScheduleModalPublisher {

    private final ReviewReservationTimeViewFactory slackViews;

    public SlackActionResponse pushCustomDatetimeModal(String metaJson, String initialDate) {
        View view = slackViews.customDatetimeModal(metaJson, initialDate);

        return SlackActionResponse.push(view);
    }
}
