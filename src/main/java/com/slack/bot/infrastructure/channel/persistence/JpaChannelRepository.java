package com.slack.bot.infrastructure.channel.persistence;

import com.slack.bot.domain.channel.Channel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaChannelRepository extends ListCrudRepository<Channel, Long> {

    Optional<Channel> findByApiKey(String apiKey);

    List<Channel> findAllByTeamId(String teamId);

    void deleteByTeamId(String teamId);
}
