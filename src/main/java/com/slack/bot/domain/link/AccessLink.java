package com.slack.bot.domain.link;

import com.slack.bot.domain.common.CreatedAtEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "access_links")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccessLink extends CreatedAtEntity {

    private String linkKey;
    private Long projectMemberId;

    public static AccessLink create(String linkKey, Long projectMemberId) {
        validateLinkKey(linkKey);
        validateProjectMemberId(projectMemberId);

        return new AccessLink(linkKey, projectMemberId);
    }

    private static void validateLinkKey(String linkKey) {
        if (linkKey == null || linkKey.isBlank()) {
            throw new IllegalArgumentException("linkKey는 비어 있을 수 없습니다.");
        }
    }

    private static void validateProjectMemberId(Long projectMemberId) {
        if (projectMemberId == null) {
            throw new IllegalArgumentException("projectMemberId는 비어 있을 수 없습니다.");
        }
    }

    private AccessLink(String linkKey, Long projectMemberId) {
        this.linkKey = linkKey;
        this.projectMemberId = projectMemberId;
    }
}
