package com.slack.bot.application.event.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.IntegrationTestConfig;
import com.slack.bot.application.event.client.SlackEventApiClient;
import com.slack.bot.application.event.handler.exception.BotUserIdMissingException;
import com.slack.bot.application.event.handler.exception.UnregisteredWorkspaceException;
import com.slack.bot.application.event.parser.MemberJoinedEventParser;
import com.slack.bot.domain.channel.Channel;
import com.slack.bot.domain.channel.repository.ChannelRepository;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import com.slack.bot.global.config.properties.EventMessageProperties;
import java.util.Optional;
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

    @Autowired
    PlatformTransactionManager transactionManager;

    private MemberJoinedChannelEventHandler memberJoinedChannelEventHandler;

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
    void 봇이_채널에_초대되면_Slack_API로_조회한_채널명으로_DB에_저장된다() {
        // given
        String expectedChannelName = "integration-test-channel";

        JsonNode payload = createPayload(
                "workspace-id",
                "bot-user-id",
                "channel-id"
        );

        // when
        memberJoinedChannelEventHandler.handle(payload);

        // then
        Optional<Channel> actualChannel = channelRepository.findByTeamId("workspace-id");

        assertAll(
                () -> assertThat(actualChannel).isPresent(),
                () -> assertThat(actualChannel.get().getChannelName()).isEqualTo(expectedChannelName)
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/event/workspace_team1.sql",
            "classpath:sql/fixtures/event/channel_existing_for_handler.sql"
    })
    void 기존_채널이_존재하면_Slack_API로_조회한_채널명으로_업데이트된다() {
        // given
        String expectedUpdatedName = "integration-test-channel";
        JsonNode payload = createPayload(
                "workspace-id",
                "bot-user-id",
                "channel-id"
        );

        // when
        memberJoinedChannelEventHandler.handle(payload);

        // then
        Optional<Channel> actual = channelRepository.findByTeamId("workspace-id");

        assertAll(
                () -> assertThat(actual).isPresent(),
                () -> assertThat(actual.get().getChannelName()).isEqualTo(expectedUpdatedName)
        );
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/event/workspace_team1.sql")
    void Slack_API_호출_중_예외가_발생하면_Fallback_로직으로_채널명을_저장한다() {
        // given
        String errorChannelId = IntegrationTestConfig.ERROR_CHANNEL_NAME; // 예외를 강제로 발생시키는 채널 ID
        JsonNode payload = createPayload(
                "workspace-id",
                "bot-user-id",
                errorChannelId
        );

        // when
        memberJoinedChannelEventHandler.handle(payload);

        // then
        Optional<Channel> actualChannel = channelRepository.findByTeamId("workspace-id");

        assertAll(
                () -> assertThat(actualChannel).isPresent(),
                () -> assertThat(actualChannel.get().getChannelName()).isEqualTo("channel-" + errorChannelId)
        );
    }

    @Test
    void 등록되지_않은_워크스페이스면_예외가_발생한다() {
        // given
        JsonNode payload = createPayload(
                "unknown-workspace-id",
                "bot-user-id",
                "channel-id"
        );

        // when & then
        assertThatThrownBy(() -> memberJoinedChannelEventHandler.handle(payload))
                .isInstanceOf(UnregisteredWorkspaceException.class);
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/event/workspace_team1_no_bot_user.sql")
    void 봇_User_id가_누락되면_예외가_발생한다() {
        // given
        JsonNode payload = createPayload(
                "workspace-id",
                "bot-user-id",
                "channel-id"
        );

        // when & then
        assertThatThrownBy(() -> memberJoinedChannelEventHandler.handle(payload))
                .isInstanceOf(BotUserIdMissingException.class)
                .hasMessageContaining("workspace-id");
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/event/workspace_team1.sql")
    void 봇이_아닌_사용자가_채널에_참여하면_DB에_저장되지_않는다() {
        // given
        JsonNode payload = createPayload(
                "workspace-id",
                "member-user-id",
                "channel-id"
        );

        // when
        memberJoinedChannelEventHandler.handle(payload);

        // then
        assertThat(channelRepository.findByTeamId("workspace-id")).isEmpty();
    }

    private JsonNode createPayload(String teamId, String joinedUserId, String channelId) {
        ObjectNode event = objectMapper.createObjectNode();

        event.put("user", joinedUserId);
        event.put("channel", channelId);
        event.put("inviter", "inviter-user-id");

        ObjectNode payload = objectMapper.createObjectNode();

        payload.put("team_id", teamId);
        payload.set("event", event);
        return payload;
    }
}
