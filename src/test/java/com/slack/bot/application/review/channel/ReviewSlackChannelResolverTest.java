package com.slack.bot.application.review.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.review.channel.dto.SlackChannelDto;
import com.slack.bot.application.review.channel.exception.ReviewChannelResolveException;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReviewSlackChannelResolverTest {

    @Autowired
    ReviewSlackChannelResolver channelResolver;

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/review/project_t1.sql",
            "classpath:sql/fixtures/review/workspace_t1.sql",
            "classpath:sql/fixtures/review/channel_t1.sql"
    })
    void 유효한_API_Key로_채널_정보를_조회한다() {
        // when
        SlackChannelDto actual = channelResolver.resolve("test-api-key");

        // then
        assertAll(
                () -> assertThat(actual.teamId()).isEqualTo("T1"),
                () -> assertThat(actual.channelId()).isEqualTo("C1"),
                () -> assertThat(actual.accessToken()).isEqualTo("xoxb-test-token")
        );
    }

    @Test
    @Sql(scripts = "classpath:sql/fixtures/review/project_t1.sql")
    void 워크스페이스가_없으면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> channelResolver.resolve("test-api-key"))
                .isInstanceOf(ReviewChannelResolveException.class)
                .hasMessageContaining("워크스페이스 정보가 없습니다.");
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/review/project_t1.sql",
            "classpath:sql/fixtures/review/workspace_t1.sql"
    })
    void 채널이_없으면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> channelResolver.resolve("test-api-key"))
                .isInstanceOf(ReviewChannelResolveException.class)
                .hasMessageContaining("채널 정보가 없습니다.");
    }

    @Test
    void 프로젝트가_없으면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> channelResolver.resolve("non-existent-key"))
                .isInstanceOf(RuntimeException.class);
    }
}
