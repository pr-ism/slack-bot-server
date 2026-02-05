package com.slack.bot.application.interactivity.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.slack.api.model.view.View;
import com.slack.api.model.view.Views;
import com.slack.bot.application.interactivity.reply.dto.response.SlackActionResponse;
import com.slack.bot.application.interactivity.view.factory.ReviewReservationTimeViewFactory;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class ReviewScheduleModalPublisherTest {

    @Mock
    ReviewReservationTimeViewFactory viewFactory;

    @Test
    void 커스텀_시간_모달을_push_응답으로_반환한다() {
        // given
        ReviewScheduleModalPublisher publisher = new ReviewScheduleModalPublisher(viewFactory);
        String metaJson = "{\"teamId\":\"T1\"}";
        String initialDate = "2024-01-01";
        View modal = Views.view(v -> v.type("modal").callbackId("cb"));

        given(viewFactory.customDatetimeModal(metaJson, initialDate)).willReturn(modal);

        // when
        Object response = publisher.pushCustomDatetimeModal(metaJson, initialDate);

        // then
        assertAll(
                () -> verify(viewFactory).customDatetimeModal(metaJson, initialDate),
                () -> assertThat(response).isInstanceOf(SlackActionResponse.class),
                () -> assertThat(((SlackActionResponse) response).response_action()).isEqualTo("push"),
                () -> assertThat(((SlackActionResponse) response).view()).isSameAs(modal)
        );
    }
}
