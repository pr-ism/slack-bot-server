package com.slack.bot.application.command.handler;

import com.slack.bot.application.command.AccessLinker;
import com.slack.bot.application.command.ProjectMemberReader;
import com.slack.bot.application.command.dto.SlackCommandContextDto;
import com.slack.bot.global.config.properties.AppProperties;
import com.slack.bot.global.config.properties.CommandMessageProperties;

public class MyPageCommandHandler implements BotCommandHandler {

    private final AccessLinker accessLinker;
    private final AppProperties appProperties;
    private final ProjectMemberReader projectMemberReader;
    private final CommandMessageProperties messages;

    public static MyPageCommandHandler create(
            ProjectMemberReader projectMemberReader,
            AccessLinker accessLinker,
            AppProperties appProperties,
            CommandMessageProperties messages
    ) {
        return new MyPageCommandHandler(projectMemberReader, accessLinker, appProperties, messages);
    }

    private MyPageCommandHandler(
            ProjectMemberReader projectMemberReader,
            AccessLinker accessLinker,
            AppProperties appProperties,
            CommandMessageProperties messages
    ) {
        this.projectMemberReader = projectMemberReader;
        this.accessLinker = accessLinker;
        this.appProperties = appProperties;
        this.messages = messages;
    }

    @Override
    public String handle(SlackCommandContextDto context) {
        String teamId = context.teamId();
        String slackUserId = context.userId();
        return projectMemberReader.read(teamId, slackUserId)
                                   .map(mapping -> {
                                       Long projectMemberId = mapping.getId();

                                       return buildLinkMessage(projectMemberId);
                                   })
                                   .orElse(messages.myPage().notConnected());
    }

    private String buildLinkMessage(Long projectMemberId) {
        String linkKey = accessLinker.provideLinkKey(projectMemberId);
        String link = buildUrl(linkKey);

        return messages.myPage()
                       .linkMessage()
                       .formatted(link);
    }

    private String buildUrl(String linkKey) {
        String baseUrl = appProperties.publicBaseUrl();
        String normalized = baseUrl;

        if (baseUrl.endsWith("/")) {
            normalized = baseUrl.substring(0, baseUrl.length() - 1);
        }

        return normalized + "/links/" + linkKey;
    }
}
