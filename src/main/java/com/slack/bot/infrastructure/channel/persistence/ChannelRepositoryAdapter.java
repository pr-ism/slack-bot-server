package com.slack.bot.infrastructure.channel.persistence;

import static com.slack.bot.domain.channel.QChannel.channel;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.slack.bot.domain.channel.Channel;
import com.slack.bot.domain.channel.repository.ChannelRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ChannelRepositoryAdapter implements ChannelRepository {

    private final JPAQueryFactory queryFactory;
    private final JpaChannelRepository jpaChannelRepository;

    @Override
    @Transactional
    public void save(Channel channel) {
        jpaChannelRepository.save(channel);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Channel> findChannelInTeam(String teamId, String slackChannelId) {
        Channel result = queryFactory.selectFrom(channel)
                                     .where(
                                             channel.teamId.eq(teamId),
                                             channel.slackChannelId.eq(slackChannelId)
                                     )
                                     .fetchOne();

        return Optional.ofNullable(result);
    }

    @Override
    public Optional<Channel> findByTeamId(String teamId) {
        return jpaChannelRepository.findByTeamId(teamId);
    }

    @Override
    @Transactional
    public void deleteByTeamId(String teamId) {
        jpaChannelRepository.deleteByTeamId(teamId);
    }
}
