package com.slack.bot.application.interactivity.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewTimeValidatorTest {

    private final ZoneId zone = ZoneId.of("Asia/Seoul");
    private final Clock clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), zone);
    private final ReviewTimeValidator validator = new ReviewTimeValidator(clock);

    @Test
    void 정상_시간이면_에러가_없다() {
        // when
        Map<String, String> errors = validator.validateCustomDateTime("2024-01-01", "09:00");

        // then
        assertThat(errors).isEmpty();
    }

    @Test
    void 날짜_공백을_제거해_정상_처리한다() {
        // when
        Map<String, String> errors = validator.validateCustomDateTime(" 2024-01-01 ", "09:00");

        // then
        assertThat(errors).isEmpty();
    }

    @Test
    void 과거_시간이면_에러를_반환한다() {
        // when
        Map<String, String> errors = validator.validateCustomDateTime("2024-01-01", "08:59");

        // then
        assertThat(errors).containsEntry("time_block", "과거 시간은 선택할 수 없습니다. 현재 이후 시간으로 입력해주세요.");
    }

    @Test
    void 날짜가_없으면_에러를_반환한다() {
        // when
        Map<String, String> errors = validator.validateCustomDateTime(null, "09:00");

        // then
        assertThat(errors).containsEntry("date_block", "날짜를 선택해주세요.");
    }

    @Test
    void 시간이_없으면_에러를_반환한다() {
        // when
        Map<String, String> errors = validator.validateCustomDateTime("2024-01-01", "");

        // then
        assertThat(errors).containsEntry("time_block", "시간을 입력해주세요. 예: 21:35");
    }

    @Test
    void 시간_형식이_잘못되면_에러를_반환한다() {
        // when
        Map<String, String> errors = validator.validateCustomDateTime("2024-01-01", "9:00");

        // then
        assertThat(errors).containsEntry("time_block", "시간은 00:00~23:59 범위의 HH:mm 형식이어야 합니다. 예: 21:35");
    }

    @Test
    void normalizeHhMmOrNull은_공백을_제거하고_정상_형식만_반환한다() {
        // when & then
        assertAll(
                () -> assertThat(validator.normalizeTimeOrNull(" 09:05 ")).isEqualTo("09:05"),
                () -> assertThat(validator.normalizeTimeOrNull("9:05")).isNull(),
                () -> assertThat(validator.normalizeTimeOrNull("24:00")).isNull(),
                () -> assertThat(validator.normalizeTimeOrNull(null)).isNull()
        );
    }

    @Test
    void parseScheduledAtInstant는_Clock_Zone을_기준으로_Instant를_생성한다() {
        // when
        Instant actual = validator.parseScheduledAtInstant("2024-01-02", "10:30");

        // then
        assertThat(actual).isEqualTo(Instant.parse("2024-01-02T01:30:00Z"));
    }
}
