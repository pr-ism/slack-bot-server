package com.slack.bot.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.slack.bot.application.command.AccessLinker;
import com.slack.bot.application.command.MemberConnector;
import com.slack.bot.application.command.ProjectMemberReader;
import com.slack.bot.application.command.handler.CommandHandlerRegistry;
import com.slack.bot.application.event.handler.SlackEventHandler;
import com.slack.bot.application.event.handler.SlackEventHandlerRegistry;
import com.slack.bot.application.setting.strategy.NotificationSettingsUpdater;
import com.slack.bot.global.config.properties.AccessLinkKeyProperties;
import com.slack.bot.global.config.properties.AppProperties;
import com.slack.bot.global.config.properties.CommandMessageProperties;
import com.slack.bot.global.config.properties.EventMessageProperties;
import com.slack.bot.global.config.properties.SlackEventAsyncProperties;
import com.slack.bot.global.config.properties.SlackProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({
        SlackProperties.class, AccessLinkKeyProperties.class, CommandMessageProperties.class, AppProperties.class,
        SlackEventAsyncProperties.class, EventMessageProperties.class
})
public class AppConfig {

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    @Bean
    public RestClient.Builder slackRestClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(Duration.ofSeconds(1L));
        factory.setReadTimeout(Duration.ofSeconds(3L));
        return RestClient.builder()
                         .requestFactory(factory)
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

    @Bean
    public CommandHandlerRegistry slackCommandHandlerRegistry(
            MemberConnector memberConnector,
            ProjectMemberReader projectMemberReader,
            AccessLinker accessLinker,
            AppProperties appProperties,
            CommandMessageProperties commandMessageProperties
    ) {
        return CommandHandlerRegistry.create(
                memberConnector,
                projectMemberReader,
                accessLinker,
                appProperties,
                commandMessageProperties
        );
    }

    @Bean
    public SlackEventHandlerRegistry slackEventHandlerRegistry(
            @Qualifier("memberJoinedEventHandler") SlackEventHandler memberJoinedHandler,
            @Qualifier("appUninstallEventHandler") SlackEventHandler appUninstalledHandler
    ) {
        Map<String, SlackEventHandler> handlerMap = new HashMap<>();

        handlerMap.put("member_joined_channel", memberJoinedHandler);
        handlerMap.put("app_uninstalled", appUninstalledHandler);
        return SlackEventHandlerRegistry.of(handlerMap);
    }

    @Bean
    public NotificationSettingsUpdater notificationSettingsUpdater() {
        return NotificationSettingsUpdater.create();
    }
}
