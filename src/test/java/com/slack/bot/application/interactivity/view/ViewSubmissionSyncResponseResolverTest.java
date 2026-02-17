package com.slack.bot.application.interactivity.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.interactivity.reply.dto.response.SlackActionResponse;
import com.slack.bot.application.interactivity.view.dto.ViewSubmissionSyncResultDto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("NonAsciiCharacters")
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ViewSubmissionSyncResponseResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    ReviewTimeValidator reviewTimeValidator;

    @Mock
    ReviewScheduleModalPublisher reviewScheduleModalPublisher;

    ViewSubmissionSyncResponseResolver resolver;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-02-15T00:00:00Z"), ZoneId.of("Asia/Seoul"));

        resolver = new ViewSubmissionSyncResponseResolver(
                fixedClock,
                reviewTimeValidator,
                reviewScheduleModalPublisher
        );
    }

    @Test
    void 알_수_없는_callback이면_빈_응답을_반환하고_enqueue하지_않는다() {
        // given
        ObjectNode payload = payloadWithCallback("unknown");

        // when
        ViewSubmissionSyncResultDto actual = resolver.resolve(payload);

        // then
        assertAll(
                () -> assertThat(actual.response()).isEqualTo(SlackActionResponse.empty()),
                () -> assertThat(actual.shouldEnqueue()).isFalse()
        );
    }

    @Test
    void 기본_제출에서_custom_선택이면_push_응답을_반환하고_enqueue하지_않는다() {
        // given
        ObjectNode payload = defaultSubmitPayload("custom");
        SlackActionResponse push = SlackActionResponse.push(Map.of("dummy", "view"));
        given(reviewScheduleModalPublisher.pushCustomDatetimeModal("meta-json", "2026-02-15"))
                .willReturn(push);

        // when
        ViewSubmissionSyncResultDto actual = resolver.resolve(payload);

        // then
        assertAll(
                () -> assertThat(actual.response()).isEqualTo(push),
                () -> assertThat(actual.shouldEnqueue()).isFalse(),
                () -> verify(reviewScheduleModalPublisher).pushCustomDatetimeModal(
                        "meta-json",
                        "2026-02-15"
                )
        );
    }

    @Test
    void 기본_제출에서_now_선택이면_clear_응답으로_enqueue한다() {
        // given
        ObjectNode payload = defaultSubmitPayload("now");

        // when
        ViewSubmissionSyncResultDto actual = resolver.resolve(payload);

        // then
        assertAll(
                () -> assertThat(actual.response()).isEqualTo(SlackActionResponse.clear()),
                () -> assertThat(actual.shouldEnqueue()).isTrue()
        );
    }

    @Test
    void 기본_제출에서_분_옵션_선택이면_clear_응답으로_enqueue한다() {
        // given
        ObjectNode payload = defaultSubmitPayload("30");

        // when
        ViewSubmissionSyncResultDto actual = resolver.resolve(payload);

        // then
        assertAll(
                () -> assertThat(actual.response()).isEqualTo(SlackActionResponse.clear()),
                () -> assertThat(actual.shouldEnqueue()).isTrue()
        );
    }

    @Test
    void 기본_제출에서_시간_옵션이_잘못되면_errors_응답을_반환한다() {
        // given
        ObjectNode payload = defaultSubmitPayload("wrong-value");

        // when
        ViewSubmissionSyncResultDto actual = resolver.resolve(payload);

        // then
        assertAll(
                () -> assertThat(actual.response().responseAction()).isEqualTo("errors"),
                () -> assertThat(actual.response().errors()).containsKey("time_block"),
                () -> assertThat(actual.shouldEnqueue()).isFalse()
        );
    }

    @Test
    void 커스텀_제출에서_검증에_실패한_경우_errors_응답을_반환한다() {
        // given
        ObjectNode payload = customSubmitPayload("2026-02-16", "99:99");
        given(reviewTimeValidator.validateCustomDateTime("2026-02-16", "99:99"))
                .willReturn(Map.of("time_block", "리뷰 시작 시간 값이 올바르지 않습니다."));

        // when
        ViewSubmissionSyncResultDto actual = resolver.resolve(payload);

        // then
        assertAll(
                () -> assertThat(actual.response().responseAction()).isEqualTo("errors"),
                () -> assertThat(actual.response().errors()).containsKey("time_block"),
                () -> assertThat(actual.shouldEnqueue()).isFalse()
        );
    }

    @Test
    void 커스텀_제출에서_검증_성공이면_clear_응답으로_enqueue한다() {
        // given
        ObjectNode payload = customSubmitPayload("2026-02-16", "10:30");
        given(reviewTimeValidator.validateCustomDateTime("2026-02-16", "10:30"))
                .willReturn(Map.of());

        // when
        ViewSubmissionSyncResultDto actual = resolver.resolve(payload);

        // then
        assertAll(
                () -> assertThat(actual.response()).isEqualTo(SlackActionResponse.clear()),
                () -> assertThat(actual.shouldEnqueue()).isTrue()
        );
    }

    private ObjectNode payloadWithCallback(String callbackId) {
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode view = objectMapper.createObjectNode();
        view.put("callback_id", callbackId);
        payload.set("view", view);
        return payload;
    }

    private ObjectNode defaultSubmitPayload(String selectedOption) {
        ObjectNode payload = payloadWithCallback(ViewCallbackId.REVIEW_TIME_SUBMIT.value());
        ObjectNode view = (ObjectNode) payload.get("view");
        view.put("private_metadata", "meta-json");

        ObjectNode state = objectMapper.createObjectNode();
        ObjectNode values = objectMapper.createObjectNode();
        ObjectNode timeBlock = objectMapper.createObjectNode();
        ObjectNode timeAction = objectMapper.createObjectNode();
        ObjectNode selected = objectMapper.createObjectNode();
        selected.put("value", selectedOption);
        timeAction.set("selected_option", selected);
        timeBlock.set("time_action", timeAction);
        values.set("time_block", timeBlock);
        state.set("values", values);
        view.set("state", state);
        return payload;
    }

    private ObjectNode customSubmitPayload(String date, String time) {
        ObjectNode payload = payloadWithCallback(ViewCallbackId.REVIEW_TIME_CUSTOM_SUBMIT.value());
        ObjectNode view = (ObjectNode) payload.get("view");
        ObjectNode state = objectMapper.createObjectNode();
        ObjectNode values = objectMapper.createObjectNode();

        ObjectNode dateBlock = objectMapper.createObjectNode();
        dateBlock.set("date_action", objectMapper.createObjectNode().put("selected_date", date));

        ObjectNode timeBlock = objectMapper.createObjectNode();
        timeBlock.set("time_action", objectMapper.createObjectNode().put("value", time));

        values.set("date_block", dateBlock);
        values.set("time_block", timeBlock);
        state.set("values", values);
        view.set("state", state);

        return payload;
    }
}
