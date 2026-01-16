package com.slack.bot.global.config;

import com.slack.bot.global.config.properties.SlackProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SlackProperties.class)
public class AppConfig {

    @Bean
    public RestClient.Builder slackRestClientBuilder() {
        return RestClient.builder()
                         .baseUrl("https://slack.com/api/");
    }

    @Bean
    public RestClient slackClient() {
        return slackRestClientBuilder().build();
    }
}
