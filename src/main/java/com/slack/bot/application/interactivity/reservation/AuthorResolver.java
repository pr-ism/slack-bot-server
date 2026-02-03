package com.slack.bot.application.interactivity.reservation;

import com.slack.bot.application.interactivity.dto.ReviewScheduleMetaDto;
import com.slack.bot.application.interactivity.reservation.dto.ReservationContextDto;
import com.slack.bot.domain.member.repository.ProjectMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthorResolver {

    private final ProjectMemberRepository projectMemberRepository;

    public String resolveAuthorSlackId(ReviewScheduleMetaDto meta) {
        if (hasAuthorSlackId(meta)) {
            return meta.authorSlackId();
        }
        if (!canLookupByGithub(meta)) {
            return ReservationContextDto.UNKNOWN_AUTHOR_SLACK_ID;
        }

        return projectMemberRepository.findByGithubUser(meta.teamId(), meta.authorGithubId())
                                      .map(project -> project.getSlackUserId())
                                      .orElse(ReservationContextDto.UNKNOWN_AUTHOR_SLACK_ID);
    }

    private boolean hasAuthorSlackId(ReviewScheduleMetaDto meta) {
        return meta.authorSlackId() != null && !meta.authorSlackId().isBlank();
    }

    private boolean canLookupByGithub(ReviewScheduleMetaDto meta) {
        return meta.teamId() != null && meta.authorGithubId() != null;
    }
}
