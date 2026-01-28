package com.slack.bot.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.domain.member.ProjectMember;
import com.slack.bot.domain.member.repository.ProjectMemberRepository;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MemberConnectionWriterTest {

    @Autowired
    MemberConnectionWriter memberConnectionWriter;

    @Autowired
    ProjectMemberRepository projectMemberRepository;

    @Test
    @Sql(scripts = "classpath:sql/fixtures/member/project_member_team1_user2.sql")
    void 이미_존재하는_멤버는_표시_이름을_유지한_채_Github_ID를_갱신한다() {
        // when
        String actualConnectedName = memberConnectionWriter.saveOrUpdateMember(
                "T1",
                "U2",
                "무시될_이름",
                "gildong"
        );

        // then
        Optional<ProjectMember> actualUpdatedMember = projectMemberRepository.findBySlackUser("T1", "U2");

        assertAll(
                () -> assertThat(actualConnectedName).isEqualTo("홍길동"),
                () -> assertThat(actualUpdatedMember).isPresent(),
                () -> assertThat(actualUpdatedMember.get().getDisplayName()).isEqualTo("홍길동"),
                () -> assertThat(actualUpdatedMember.get().getGithubId().getValue()).isEqualTo("gildong")
        );
    }

    @Test
    void 멤버가_없으면_표시_이름과_Github_ID를_설정해_생성한다() {
        // when
        String actualConnectedName = memberConnectionWriter.saveOrUpdateMember(
                "T1",
                "U3",
                "신규 사용자",
                "gildong"
        );

        // then
        Optional<ProjectMember> actualSavedMember = projectMemberRepository.findBySlackUser("T1", "U3");

        assertAll(
                () -> assertThat(actualConnectedName).isEqualTo("신규 사용자"),
                () -> assertThat(actualSavedMember).isPresent(),
                () -> assertThat(actualSavedMember.get().getDisplayName()).isEqualTo("신규 사용자"),
                () -> assertThat(actualSavedMember.get().getGithubId().getValue()).isEqualTo("gildong")
        );
    }

    @Test
    void 동시에_요청해도_중복_멤버가_생성되지_않는다() throws Exception {
        // given
        int threadCount = 10;
        String teamId = "T2";
        String slackUserId = "U9";
        String displayName = "동시 사용자";
        String githubId = "gildong";
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        // when
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < threadCount; i++) {
                executor.execute(
                        () -> {
                            ready.countDown();
                            try {
                                start.await();
                                memberConnectionWriter.saveOrUpdateMember(
                                        teamId,
                                        slackUserId,
                                        displayName,
                                        githubId
                                );
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                            } finally {
                                done.countDown();
                            }
                        }
                );
            }
            ready.await();
            start.countDown();
            boolean finished = done.await(3, TimeUnit.SECONDS);
            assertThat(finished).isTrue();
        }

        Optional<ProjectMember> actualSavedMember = projectMemberRepository.findBySlackUser(teamId, slackUserId);

        // then
        assertAll(
                () -> assertThat(actualSavedMember).isPresent(),
                () -> assertThat(actualSavedMember.get().getDisplayName()).isEqualTo(displayName),
                () -> assertThat(actualSavedMember.get().getGithubId().getValue()).isEqualTo(githubId)
        );
    }
}
