package com.slack.bot.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.slack.bot.global.config.properties.AccessLinkKeyProperties;
import com.slack.bot.global.config.properties.SlackProperties;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({SlackProperties.class, AccessLinkKeyProperties.class})
public class AppConfig {

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    @Bean
    public RestClient.Builder slackRestClientBuilder() {
        return RestClient.builder()
                         .baseUrl("https://slack.com/api/");
    }

    @Bean
    public RestClient slackClient() {
        return slackRestClientBuilder().build();
    }

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        return builder.simpleDateFormat(DATE_TIME_FORMAT)
                      .serializers(new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DATE_TIME_FORMAT)))
                      .build();
    }
}
