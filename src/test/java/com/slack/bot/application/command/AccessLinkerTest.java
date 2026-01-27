package com.slack.bot.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.domain.member.ProjectMember;
import com.slack.bot.infrastructure.link.persistence.JpaAccessLinkRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AccessLinkerTest {

    @Autowired
    AccessLinker accessLinker;

    @Autowired
    JpaAccessLinkRepository accessLinkRepository;

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/link/access_link_sequence_initial_seed.sql",
            "classpath:sql/fixtures/link/project_member_team1_user1.sql"
    })
    void 프로젝트_멤버가_개인_설정_링크_키를_받는다() {
        // given
        Long projectMemberId = 1L;

        // when
        String linkKey = accessLinker.provideLinkKey(projectMemberId);

        // then
        assertAll(
                () -> assertThat(linkKey).isNotBlank(),
                () -> assertThat(accessLinkRepository.findByProjectMemberId(projectMemberId))
                        .hasValueSatisfying(link -> assertThat(link.getLinkKey()).isEqualTo(linkKey))
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/link/access_link_sequence_initial_seed.sql",
            "classpath:sql/fixtures/link/project_member_team1_user1.sql"
    })
    void 링크_키로_멤버를_조회한다() {
        // given
        Long projectMemberId = 1L;
        String linkKey = accessLinker.provideLinkKey(projectMemberId);

        // when
        Optional<ProjectMember> resolvedMember = accessLinker.resolve(linkKey);

        // then
        assertAll(
                () -> assertThat(resolvedMember).isPresent(),
                () -> assertThat(resolvedMember.get().getId()).isEqualTo(projectMemberId)
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/link/access_link_sequence_initial_seed.sql",
            "classpath:sql/fixtures/link/project_member_team1_user1.sql",
            "classpath:sql/fixtures/link/access_link_team1_user1.sql"
    })
    void 링크_키로_연결된_멤버를_단_건_조회한다() {
        // given
        String linkKey = "linkKey01";

        // when
        Optional<ProjectMember> resolvedMember = accessLinker.resolve(linkKey);

        // then
        assertAll(
                () -> assertThat(resolvedMember).isPresent(),
                () -> assertThat(resolvedMember.get().getId()).isEqualTo(1L)
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/link/access_link_sequence_initial_seed.sql",
            "classpath:sql/fixtures/link/project_member_team1_user1.sql",
            "classpath:sql/fixtures/link/access_link_team1_user1.sql"
    })
    void 기존_링크_키를_조회한다() {
        // given
        Long projectMemberId = 1L;

        // when
        String linkKey = accessLinker.provideLinkKey(projectMemberId);

        // then
        assertAll(
                () -> assertThat(linkKey).isEqualTo("linkKey01"),
                () -> assertThat(accessLinkRepository.findByProjectMemberId(projectMemberId))
                        .hasValueSatisfying(link -> assertThat(link.getLinkKey()).isEqualTo(linkKey))
        );
    }

    @Test
    @Sql(scripts = {
            "classpath:sql/fixtures/link/access_link_sequence_initial_seed.sql",
            "classpath:sql/fixtures/link/project_member_team1_user1.sql"
    })
    void 동시에_요청해도_링크_키는_단건으로_생성된다() throws Exception {
        // given
        Long projectMemberId = 1L;
        int threadCount = 10;
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        Set<String> linkKeys = new HashSet<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < threadCount; i++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    readyLatch.countDown();
                                    startLatch.await();
                                    // when
                                    return accessLinker.provideLinkKey(projectMemberId);
                                }
                        )
                );
            }

            readyLatch.await();
            startLatch.countDown();

            for (Future<String> future : futures) {
                linkKeys.add(future.get());
            }
        }

        // then
        assertAll(
                () -> assertThat(linkKeys).hasSize(1),
                () -> assertThat(accessLinkRepository.findByProjectMemberId(projectMemberId))
                        .hasValueSatisfying(link -> assertThat(link.getLinkKey()).isEqualTo(linkKeys.iterator().next()))
        );
    }
}
