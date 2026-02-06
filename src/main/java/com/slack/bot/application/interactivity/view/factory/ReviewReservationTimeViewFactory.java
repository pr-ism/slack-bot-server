package com.slack.bot.application.interactivity.view.factory;

import com.slack.api.model.block.Blocks;
import com.slack.api.model.block.composition.BlockCompositions;
import com.slack.api.model.block.composition.OptionObject;
import com.slack.api.model.block.element.BlockElements;
import com.slack.api.model.view.View;
import com.slack.api.model.view.Views;
import com.slack.bot.application.interactivity.view.ViewCallbackId;
import com.slack.bot.global.config.properties.ReviewReservationTimeOptionsProperties;
import com.slack.bot.global.config.properties.ReviewReservationTimeOptionsProperties.TimeOption;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewReservationTimeViewFactory {

    private final ReviewReservationTimeOptionsProperties timeOptionsProperties;
    private final ReviewCustomDatetimeModalViewFactory customDatetimeModalViewFactory;

    public View reviewTimeSubmitModal(String metaJson) {
        return Views.view(view -> view
                .type("modal")
                .callbackId(ViewCallbackId.REVIEW_TIME_SUBMIT.value())
                .privateMetadata(metaJson)
                .title(Views.viewTitle(t -> t.type("plain_text").text("리뷰 시간 설정")))
                .submit(Views.viewSubmit(s -> s.type("plain_text").text("확인")))
                .close(Views.viewClose(c -> c.type("plain_text").text("취소")))
                .blocks(Blocks.asBlocks(
                        Blocks.input(input -> input
                                .blockId("time_block")
                                .optional(false)
                                .label(BlockCompositions.plainText("리뷰 시작 시간을 선택하세요"))
                                .element(BlockElements.radioButtons(radio -> radio
                                        .actionId("time_action")
                                        .options(buildTimeOptions())
                                ))
                        )
                ))
        );
    }

    public View customDatetimeModal(String metaJson, String initialDate) {
        return customDatetimeModalViewFactory.create(metaJson, initialDate);
    }

    private OptionObject createOption(String text, String value) {
        return BlockCompositions.option(opt -> opt
                .text(BlockCompositions.plainText(text))
                .value(value)
        );
    }

    private List<OptionObject> buildTimeOptions() {
        List<OptionObject> options = new ArrayList<>();
        options.add(createOption("지금 바로", "now"));
        options.addAll(resolveConfiguredTimeOptions());
        options.add(createOption("시간 직접 선택", "custom"));
        return options;
    }

    private List<OptionObject> resolveConfiguredTimeOptions() {
        List<OptionObject> options = new ArrayList<>();
        List<TimeOption> timeOptions = timeOptionsProperties.options();
        if (timeOptions == null || timeOptions.isEmpty()) {
            return options;
        }
        for (TimeOption option : timeOptions) {
            if (option == null) {
                continue;
            }
            options.add(createOption(option.label(), option.value()));
        }
        return options;
    }
}
