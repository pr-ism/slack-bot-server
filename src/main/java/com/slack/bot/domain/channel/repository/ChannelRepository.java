package com.slack.bot.domain.channel.repository;

import com.slack.bot.domain.channel.Channel;
import java.util.Optional;

public interface ChannelRepository {

    void save(Channel channel);

    Optional<Channel> findChannelInTeam(String teamId, String slackChannelId);

    Optional<Channel> findByTeamId(String teamId);

    void deleteByTeamId(String teamId);
}
