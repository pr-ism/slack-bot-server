package com.slack.bot.application.command;

import com.slack.bot.application.command.link.AccessLinkKeyGenerator;
import com.slack.bot.domain.link.AccessLink;
import com.slack.bot.domain.link.repository.AccessLinkRepository;
import com.slack.bot.domain.member.ProjectMember;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccessLinker {

    private final AccessLinkRepository accessLinkRepository;
    private final AccessLinkKeyGenerator accessLinkKeyGenerator;

    public String provideLinkKey(Long projectMemberId) {
        return accessLinkRepository.findByProjectMemberId(projectMemberId)
                                   .map(link -> link.getLinkKey())
                                   .orElseGet(
                                           () -> {
                                               AccessLink link = createNew(projectMemberId);

                                               return accessLinkRepository.save(link)
                                                                          .getLinkKey();
                                           }
                                   );
    }

    public Optional<ProjectMember> resolve(String linkKey) {
        return accessLinkRepository.findProjectMemberByLinkKey(linkKey);
    }

    private AccessLink createNew(Long projectMemberId) {
        String key = accessLinkKeyGenerator.generateKey();

        return AccessLink.create(key, projectMemberId);
    }
}
