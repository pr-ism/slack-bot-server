package com.slack.bot.infrastructure.link.persistence;

import com.slack.bot.domain.link.AccessLink;
import com.slack.bot.domain.link.repository.AccessLinkRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class AccessLinkRepositoryAdapter implements AccessLinkRepository {

    private final JpaAccessLinkRepository accessLinkRepository;

    @Override
    @Transactional
    public void save(AccessLink link) {
        accessLinkRepository.save(link);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccessLink> findByLinkKey(String linkKey) {
        return accessLinkRepository.findByLinkKey(linkKey);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccessLink> findByProjectMemberId(Long projectMemberId) {
        return accessLinkRepository.findByProjectMemberId(projectMemberId);
    }
}
