package com.slack.bot.application.interactivity.view.factory;

import com.slack.api.model.block.Blocks;
import com.slack.api.model.block.composition.BlockCompositions;
import com.slack.api.model.block.element.BlockElements;
import com.slack.api.model.view.View;
import com.slack.api.model.view.Views;
import com.slack.bot.application.interactivity.view.ViewCallbackId;
import java.time.Clock;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewCustomDatetimeModalViewFactory {

    private final Clock clock;

    public View create(String metaJson, String initialDate) {
        String currentTimeStr = LocalTime.now(clock).format(DateTimeFormatter.ofPattern("HH:mm"));

        return Views.view(view -> view
                .type("modal")
                .callbackId(ViewCallbackId.REVIEW_TIME_CUSTOM_SUBMIT.value())
                .privateMetadata(metaJson)
                .title(Views.viewTitle(t -> t.type("plain_text").text("시간 직접 입력")))
                .submit(Views.viewSubmit(s -> s.type("plain_text").text("확인")))
                .close(Views.viewClose(c -> c.type("plain_text").text("취소")))
                .blocks(Blocks.asBlocks(
                        Blocks.input(input -> input
                                .blockId("date_block")
                                .label(BlockCompositions.plainText("날짜"))
                                .element(BlockElements.datePicker(date -> date
                                        .actionId("date_action")
                                        .initialDate(initialDate)
                                        .placeholder(BlockCompositions.plainText("YYYY-MM-DD"))
                                ))
                        ),
                        Blocks.input(input -> input
                                .blockId("time_block")
                                .label(BlockCompositions.plainText("시간(HH:mm)"))
                                .element(BlockElements.plainTextInput(text -> text
                                        .actionId("time_action")
                                        .initialValue(currentTimeStr)
                                        .placeholder(BlockCompositions.plainText("예: 13:25"))
                                ))
                        )
                ))
        );
    }
}
