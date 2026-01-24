package com.slack.bot.infrastructure.link.persistence;

import com.slack.bot.domain.link.AccessLink;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;

public interface JpaAccessLinkRepository extends ListCrudRepository<AccessLink, Long> {

    Optional<AccessLink> findByLinkKey(String linkKey);

    Optional<AccessLink> findByProjectMemberId(Long projectMemberId);
}
