package com.slack.bot.infrastructure.channel.persistence;

import com.slack.bot.domain.channel.Channel;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaChannelRepository extends ListCrudRepository<Channel, Long> {

    void deleteByTeamId(String teamId);

    Optional<Channel> findByTeamId(String teamId);
}
