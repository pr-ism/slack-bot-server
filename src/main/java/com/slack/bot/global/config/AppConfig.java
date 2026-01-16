package com.slack.bot.global.config;

import com.slack.bot.global.config.properties.SlackProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SlackProperties.class)
public class AppConfig {
}
