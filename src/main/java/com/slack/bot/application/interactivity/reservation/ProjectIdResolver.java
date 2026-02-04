package com.slack.bot.application.interactivity.reservation;

import com.slack.bot.domain.project.repository.ProjectRepository;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import com.slack.bot.application.interactivity.reservation.exception.DefaultProjectNotFoundException;
import com.slack.bot.application.interactivity.reservation.exception.InvalidProjectIdException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectIdResolver {

    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;

    public Long resolve(String projectIdFromPayload, String teamId) {
        Long parsed = parseProjectIdOrNull(projectIdFromPayload);

        if (parsed != null) {
            return parsed;
        }

        return resolveDefaultProjectId(teamId);
    }

    private Long parseProjectIdOrNull(String rawProjectId) {
        if (rawProjectId == null || rawProjectId.isBlank()) {
            return null;
        }

        try {
            Long projectId = Long.parseLong(rawProjectId);

            if (projectId <= 0) {
                throw new InvalidProjectIdException(rawProjectId);
            }

            if (!projectRepository.existsById(projectId)) {
                throw new InvalidProjectIdException(rawProjectId);
            }

            return projectId;
        } catch (NumberFormatException e) {
            throw new InvalidProjectIdException(rawProjectId, e);
        }
    }

    private Long resolveDefaultProjectId(String teamId) {
        return workspaceRepository.findByTeamId(teamId)
                .flatMap(workspace -> projectRepository.findByApiKey(workspace.getAccessToken()))
                .map(project -> project.getId())
                .orElseThrow(() -> new DefaultProjectNotFoundException(teamId));
    }
}
