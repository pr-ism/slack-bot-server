package com.slack.bot.global.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("slack.command.messages")
public record CommandMessageProperties(
        String help,
        String unknown,
        Connect connect,
        Channels channels,
        MyPage myPage
) {

    public record Connect(
            String missingGithubId,
            String success,
            String fail
    ) {
    }

    public record Channels(
            String empty,
            String header,
            String lineFormat
    ) {
    }

    public record MyPage(
            String notConnected,
            String linkMessage
    ) {
    }
}
