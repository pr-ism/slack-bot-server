package com.slack.bot.application.event.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.event.client.SlackEventApiClient;
import com.slack.bot.application.event.handler.exception.BotUserIdMissingException;
import com.slack.bot.application.event.handler.exception.UnregisteredWorkspaceException;
import com.slack.bot.application.event.parser.MemberJoinedEventParser;
import com.slack.bot.domain.channel.Channel;
import com.slack.bot.domain.channel.repository.ChannelRepository;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import com.slack.bot.global.config.properties.EventMessageProperties;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MemberJoinedChannelEventHandlerTest {

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ChannelRepository channelRepository;

    @Autowired
    WorkspaceRepository workspaceRepository;

    @Autowired
    MemberJoinedEventParser memberJoinedEventParser;

    @Autowired
    SlackEventApiClient slackEventApiClient;

    @Autowired
    EventMessageProperties eventMessageProperties;

    private MemberJoinedChannelEventHandler memberJoinedChannelEventHandler;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        memberJoinedChannelEventHandler = new MemberJoinedChannelEventHandler(
                channelRepository,
                workspaceRepository,
                slackEventApiClient,
                memberJoinedEventParser,
                eventMessageProperties,
                new TransactionTemplate(transactionManager)
        );
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/event/workspace_team1.sql")
    void 봇이_채널에_초대되면_채널을_등록하고_환영_메시지를_전송한다() {
        // given
        JsonNode payload = createPayload(
                "workspace-id",
                "bot-user-id",
                "channel-id",
                "general-channel"
        );

        // when
        memberJoinedChannelEventHandler.handle(payload);

        // then
        Optional<Channel> actualChannel = channelRepository.findChannelInTeam("workspace-id", "channel-id");

        assertAll(
                () -> assertThat(actualChannel).isPresent(),
                () -> assertThat(actualChannel)
                        .map(channel -> channel.getChannelName())
                        .hasValue("general-channel"),
                () -> verify(slackEventApiClient).sendMessage(
                        "xoxb-bot-token",
                        "channel-id",
                        eventMessageProperties.welcome()
                )
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/event/workspace_team1.sql",
            "classpath:sql/fixtures/event/channel_existing_for_handler.sql"
    })
    void 기존_채널이_존재하면_채널이름을_업데이트하고_메시지를_전송한다() {
        // given
        JsonNode payload = createPayload(
                "workspace-id",
                "bot-user-id",
                "channel-id",
                "general-channel"
        );

        // when
        memberJoinedChannelEventHandler.handle(payload);

        // then
        List<Channel> actual = channelRepository.findAllByTeamId("workspace-id");

        assertAll(
                () -> assertThat(actual).hasSize(1),
                () -> assertThat(actual.get(0).getChannelName()).isEqualTo("general-channel"),
                () -> assertThat(channelRepository.findChannelInTeam("workspace-id", "channel-id"))
                        .map(channel -> channel.getChannelName())
                        .hasValue("general-channel"),
                () -> verify(slackEventApiClient).sendMessage(
                        "xoxb-bot-token",
                        "channel-id",
                        eventMessageProperties.welcome()
                )
        );
    }

    @Test
    void 등록되지_않은_워크스페이스면_메시지를_전송하지_못한다() {
        // given
        JsonNode payload = createPayload(
                "unknown-workspace-id",
                "bot-user-id",
                "channel-id",
                "general-channel"
        );

        // when & then
        assertThatThrownBy(() -> memberJoinedChannelEventHandler.handle(payload))
                .isInstanceOf(UnregisteredWorkspaceException.class);
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/event/workspace_team1_no_bot_user.sql")
    void 봇_User_id가_누락되면_메시지를_전송하지_못한다() {
        // given
        JsonNode payload = createPayload(
                "workspace-id",
                "bot-user-id",
                "channel-id",
                "general-channel"
        );

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> memberJoinedChannelEventHandler.handle(payload))
                        .isInstanceOf(BotUserIdMissingException.class)
                        .hasMessageContaining("workspace-id"),
                () -> verify(slackEventApiClient, never()).sendMessage(anyString(), anyString(), anyString())
        );
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/event/workspace_team1.sql")
    void 봇이_아닌_사용자가_채널에_참여하면_아무_동작도_하지_않는다() {
        // given
        JsonNode payload = createPayload(
                "workspace-id",
                "member-user-id",
                "channel-id",
                "general-channel"
        );

        // when
        memberJoinedChannelEventHandler.handle(payload);

        // then
        assertAll(
                () -> assertThat(channelRepository.findChannelInTeam("workspace-id", "channel-id")).isEmpty(),
                () -> verify(slackEventApiClient, never()).sendMessage(anyString(), anyString(), anyString())
        );
    }

    private JsonNode createPayload(String teamId, String joinedUserId, String channelId, String channelName) {
        ObjectNode event = objectMapper.createObjectNode();

        event.put("user", joinedUserId);
        event.put("channel", channelId);
        event.put("channel_name", channelName);
        event.put("inviter", "inviter-user-id");

        ObjectNode payload = objectMapper.createObjectNode();

        payload.put("team_id", teamId);
        payload.set("event", event);
        return payload;
    }
}
