package com.slack.bot.application.interactivity.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ViewCallbackIdTest {

    @Test
    void 리뷰_시간_제출_콜백을_찾는다() {
        // when
        ViewCallbackId actual = ViewCallbackId.from("review_time_submit");

        // then
        assertThat(actual).isEqualTo(ViewCallbackId.REVIEW_TIME_SUBMIT);
    }

    @Test
    void 리뷰_시간_직접_입력_콜백을_찾는다() {
        // when
        ViewCallbackId actual = ViewCallbackId.from("review_time_custom_submit");

        // then
        assertThat(actual).isEqualTo(ViewCallbackId.REVIEW_TIME_CUSTOM_SUBMIT);
    }

    @Test
    void 정의되지_않은_콜백은_UNKNOWN으로_처리한다() {
        // when
        ViewCallbackId actual = ViewCallbackId.from("unknown_callback");

        // then
        assertThat(actual).isEqualTo(ViewCallbackId.UNKNOWN);
    }

    @Test
    void 빈_콜백값은_UNKNOWN으로_처리한다() {
        // when
        ViewCallbackId actual = ViewCallbackId.from("");

        // then
        assertThat(actual).isEqualTo(ViewCallbackId.UNKNOWN);
    }

    @Test
    void null_콜백값은_UNKNOWN으로_처리한다() {
        // when
        ViewCallbackId actual = ViewCallbackId.from(null);

        // then
        assertThat(actual).isEqualTo(ViewCallbackId.UNKNOWN);
    }

    @Test
    void 콜백_타입_여부를_확인할_수_있다() {
        // when & then
        assertAll(
                () -> assertThat(ViewCallbackId.REVIEW_TIME_SUBMIT.isReviewTimeSubmit()).isTrue(),
                () -> assertThat(ViewCallbackId.REVIEW_TIME_CUSTOM_SUBMIT.isReviewTimeCustomSubmit()).isTrue(),
                () -> assertThat(ViewCallbackId.UNKNOWN.isReviewTimeSubmit()).isFalse(),
                () -> assertThat(ViewCallbackId.UNKNOWN.isReviewTimeCustomSubmit()).isFalse()
        );
    }

    @Test
    void 콜백_문자열_값을_조회한다() {
        // when & then
        assertAll(
                () -> assertThat(ViewCallbackId.REVIEW_TIME_SUBMIT.value()).isEqualTo("review_time_submit"),
                () -> assertThat(ViewCallbackId.REVIEW_TIME_CUSTOM_SUBMIT.value()).isEqualTo("review_time_custom_submit"),
                () -> assertThat(ViewCallbackId.UNKNOWN.value()).isNull()
        );
    }
}
