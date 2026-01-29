package com.slack.bot.infrastructure.channel.persistence;

import com.slack.bot.domain.channel.Channel;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaChannelRepository extends ListCrudRepository<Channel, Long> {

    List<Channel> findAllByTeamId(String teamId);

    void deleteByTeamId(String teamId);
}
