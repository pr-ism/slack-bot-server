package com.slack.bot.global.config.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("slack.review.reservation.time-options")
public record ReviewReservationTimeOptionsProperties(List<TimeOption> options) {

    public ReviewReservationTimeOptionsProperties {
        if (options == null || options.isEmpty()) {
            options = List.of(
                    new TimeOption("5분 뒤", "5"),
                    new TimeOption("10분 뒤", "10"),
                    new TimeOption("15분 뒤", "15"),
                    new TimeOption("30분 뒤", "30"),
                    new TimeOption("45분 뒤", "45"),
                    new TimeOption("1시간 뒤", "60")
            );
        }
    }

    public record TimeOption(String label, String value) {
    }
}
