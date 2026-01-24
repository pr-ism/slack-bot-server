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

    private String apiKey;
    private String teamId;
    private String channelId;

    @Builder
    private Channel(String apiKey, String teamId, String channelId) {
        validateApiKey(apiKey);
        validateTeamId(teamId);
        validateChannelId(channelId);

        this.apiKey = apiKey;
        this.teamId = teamId;
        this.channelId = channelId;
    }

    private static void validateApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("채널 API 키는 비어 있을 수 없습니다.");
        }
    }

    private static void validateTeamId(String teamId) {
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("채널의 team ID는 비어 있을 수 없습니다.");
        }
    }

    private static void validateChannelId(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("채널 ID는 비어 있을 수 없습니다.");
        }
    }

    public void regenerateApiKey(String apiKey) {
        validateApiKey(apiKey);
        this.apiKey = apiKey;
    }
}
