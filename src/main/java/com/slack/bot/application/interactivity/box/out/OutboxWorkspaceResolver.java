package com.slack.bot.application.interactivity.box.out;

import com.slack.bot.application.interactivity.box.out.exception.OutboxWorkspaceNotFoundException;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxWorkspaceResolver {

    private final WorkspaceRepository workspaceRepository;

    public String resolve(String token) {
        return workspaceRepository.findByAccessToken(token)
                                  .orElseThrow(() -> OutboxWorkspaceNotFoundException.forToken(token))
                                  .getTeamId();
    }
}
