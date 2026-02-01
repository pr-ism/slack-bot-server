package com.slack.bot.domain.channel;

import com.slack.bot.domain.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "channels")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Channel extends BaseEntity {

    private String teamId;

    private String slackChannelId;

    private String channelName;

    @Builder
    private Channel(String teamId, String slackChannelId, String channelName) {
        validateTeamId(teamId);
        validateSlackChannelId(slackChannelId);
        validateChannelName(channelName);

        this.teamId = teamId;
        this.slackChannelId = slackChannelId;
        this.channelName = channelName;
    }

    private static void validateTeamId(String teamId) {
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("채널의 team ID는 비어 있을 수 없습니다.");
        }
    }

    private static void validateSlackChannelId(String slackChannelId) {
        if (slackChannelId == null || slackChannelId.isBlank()) {
            throw new IllegalArgumentException("슬랙 채널 ID는 비어 있을 수 없습니다.");
        }
    }

    private static void validateChannelName(String channelName) {
        if (channelName == null || channelName.isBlank()) {
            throw new IllegalArgumentException("채널 이름은 비어 있을 수 없습니다.");
        }
    }

    public void updateChannelName(String channelName) {
        validateChannelName(channelName);

        this.channelName = channelName;
    }
}
