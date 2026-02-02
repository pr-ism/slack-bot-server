package com.slack.bot.infrastructure.channel.persistence;

import com.slack.bot.domain.channel.Channel;
import com.slack.bot.domain.channel.repository.ChannelRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ChannelRepositoryAdapter implements ChannelRepository {

    private final JpaChannelRepository jpaChannelRepository;

    @Override
    @Transactional
    public void save(Channel channel) {
        jpaChannelRepository.save(channel);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Channel> findByTeamId(String teamId) {
        return jpaChannelRepository.findByTeamId(teamId);
    }

    @Override
    @Transactional
    public void deleteByTeamId(String teamId) {
        jpaChannelRepository.deleteByTeamId(teamId);
    }
}
