package com.slack.bot.application.event.handler.spy;

import com.slack.bot.domain.channel.Channel;
import com.slack.bot.domain.channel.repository.ChannelRepository;
import java.util.List;
import java.util.Optional;
import lombok.Getter;

@Getter
public class SpyChannelRepository implements ChannelRepository {

    private int deleteByTeamIdCallCount = 0;
    private String lastDeletedTeamId = null;

    @Override
    public void deleteByTeamId(String teamId) {
        this.deleteByTeamIdCallCount++;
        this.lastDeletedTeamId = teamId;
    }

    @Override
    public void save(Channel channel) {
    }

    @Override
    public Optional<Channel> findChannelInTeam(String teamId, String slackChannelId) {
        return Optional.empty();
    }

    @Override
    public List<Channel> findAllByTeamId(String teamId) {
        return List.of();
    }
}
