package com.slack.bot.domain.link.repository;

import com.slack.bot.domain.link.AccessLink;
import java.util.Optional;

public interface AccessLinkRepository {

    void save(AccessLink link);

    Optional<AccessLink> findByLinkKey(String linkKey);

    Optional<AccessLink> findByProjectMemberId(Long projectMemberId);
}
