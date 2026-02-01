package com.slack.bot.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.domain.channel.Channel;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TeamChannelReaderTest {

    @Autowired
    TeamChannelReader teamChannelReader;

    @Test
    @Sql(scripts = "classpath:sql/fixtures/channel/channels_team1.sql")
    void 팀에_속한_채널을_조회한다() {
        // when
        List<Channel> actualChannels = teamChannelReader.readAll("T1");

        // then
        assertAll(
                () -> assertThat(actualChannels).hasSize(2),
                () -> assertThat(actualChannels).extracting(channel -> channel.getSlackChannelId())
                                               .contains("C1", "C2")
        );
    }
}
