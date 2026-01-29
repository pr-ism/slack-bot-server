package com.slack.bot.infrastructure.channel.persistence;

import static com.slack.bot.domain.channel.QChannel.channel;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.domain.channel.Channel;
import com.slack.bot.domain.channel.repository.ChannelRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ChannelRepositoryAdapter implements ChannelRepository {

    private final JPAQueryFactory queryFactory;
    private final JpaChannelRepository channelRepository;

    @Override
    @Transactional
    public void save(Channel channel) {
        channelRepository.save(channel);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Channel> findChannelInTeam(String teamId, String channelId) {
        Channel result = queryFactory.selectFrom(channel)
                                     .where(
                                             channel.teamId.eq(teamId),
                                             channel.channelId.eq(channelId)
                                     )
                                     .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Channel> findAllByTeamId(String teamId) {
        return channelRepository.findAllByTeamId(teamId);
    }

    @Override
    @Transactional
    public void deleteByTeamId(String teamId) {
        channelRepository.deleteByTeamId(teamId);
    }
}
