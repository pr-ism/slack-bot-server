package com.slack.bot.application.interactivity.view.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.view.View;
import com.slack.api.model.view.Views;
import com.slack.bot.application.interactivity.view.ViewCallbackId;
import com.slack.bot.global.config.properties.ReviewReservationTimeOptionsProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ExtendWith(MockitoExtension.class)
class ReviewReservationTimeViewFactoryTest {

    @Mock
    ReviewCustomDatetimeModalViewFactory customFactory;

    @Test
    void 리뷰_시간_선택_모달을_생성한다() {
        // given
        ReviewReservationTimeViewFactory factory = new ReviewReservationTimeViewFactory(
                new ReviewReservationTimeOptionsProperties(List.of()),
                customFactory
        );

        // when
        View view = factory.reviewTimeSubmitModal("{\"teamId\":\"T1\"}");

        // then
        List<LayoutBlock> blocks = view.getBlocks();
        assertAll(
                () -> assertThat(view.getCallbackId()).isEqualTo(ViewCallbackId.REVIEW_TIME_SUBMIT.value()),
                () -> assertThat(view.getTitle().getText()).isEqualTo("리뷰 시간 설정"),
                () -> assertThat(blocks).hasSize(1)
        );
    }

    @Test
    void 커스텀_모달은_팩토리에_위임한다() {
        // given
        ReviewReservationTimeViewFactory factory = new ReviewReservationTimeViewFactory(
                new ReviewReservationTimeOptionsProperties(List.of()),
                customFactory
        );
        String metaJson = "{}";
        String initialDate = "2024-01-01";
        View modal = Views.view(v -> v.type("modal").callbackId("cb"));

        given(customFactory.create(metaJson, initialDate)).willReturn(modal);

        // when
        View actual = factory.customDatetimeModal(metaJson, initialDate);

        // then
        assertAll(
                () -> verify(customFactory).create(metaJson, initialDate),
                () -> assertThat(actual).isSameAs(modal)
        );
    }
}
