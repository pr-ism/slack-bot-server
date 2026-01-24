package com.slack.bot.infrastructure.link.persistence;

import com.slack.bot.domain.link.AccessLink;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface JpaAccessLinkRepository extends CrudRepository<AccessLink, Long> {

    Optional<AccessLink> findByProjectMemberId(Long projectMemberId);
}
