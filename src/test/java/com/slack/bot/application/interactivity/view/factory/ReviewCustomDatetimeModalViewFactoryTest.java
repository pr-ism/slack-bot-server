package com.slack.bot.application.interactivity.view.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.api.model.block.InputBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.element.DatePickerElement;
import com.slack.api.model.block.element.PlainTextInputElement;
import com.slack.api.model.view.View;
import com.slack.bot.application.interactivity.view.ViewCallbackId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewCustomDatetimeModalViewFactoryTest {

    @Test
    void 현재_시간과_초기_날짜가_입력된_모달을_생성한다() {
        // given
        Clock clock = Clock.fixed(Instant.parse("2024-01-01T01:05:00Z"), ZoneId.of("Asia/Seoul"));
        ReviewCustomDatetimeModalViewFactory factory = new ReviewCustomDatetimeModalViewFactory(clock);

        // when
        View view = factory.create("{\"teamId\":\"T1\"}", "2024-01-10");

        // then
        List<LayoutBlock> blocks = view.getBlocks();

        assertAll(
                () -> assertThat(view.getCallbackId()).isEqualTo(ViewCallbackId.REVIEW_TIME_CUSTOM_SUBMIT.value()),
                () -> assertThat(blocks)
                        .element(0)
                        .extracting(block -> ((InputBlock) block).getElement())
                        .isInstanceOf(DatePickerElement.class)
                        .extracting(element -> ((DatePickerElement) element).getInitialDate())
                        .isEqualTo("2024-01-10"),
                () -> assertThat(blocks)
                        .element(1)
                        .extracting(block -> ((InputBlock) block).getElement())
                        .isInstanceOf(PlainTextInputElement.class)
                        .extracting(element -> ((PlainTextInputElement) element).getInitialValue())
                        .isEqualTo("10:05")
        );
    }
}
