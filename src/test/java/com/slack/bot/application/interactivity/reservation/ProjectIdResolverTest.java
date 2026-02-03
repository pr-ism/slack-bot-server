package com.slack.bot.application.interactivity.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.slack.bot.application.IntegrationTest;
import com.slack.bot.application.interactivity.reservation.exception.DefaultProjectNotFoundException;
import com.slack.bot.application.interactivity.reservation.exception.InvalidProjectIdException;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProjectIdResolverTest {

    @Autowired
    ProjectIdResolver resolver;

    @Test
    void 유효한_프로젝트_ID가_제공되면_파싱된_값을_반환한다() {
        // when
        Long actual = resolver.resolve("123", "T1");

        // then
        assertThat(actual).isEqualTo(123L);
    }

    @Test
    @Sql("classpath:sql/fixtures/reservation/workspace_t1_with_project.sql")
    void 프로젝트_ID가_null이면_기본_프로젝트_ID를_조회한다() {
        // when
        Long actual = resolver.resolve(null, "T1");

        // then
        assertThat(actual).isEqualTo(1L);
    }

    @Test
    @Sql("classpath:sql/fixtures/reservation/workspace_t1_with_project.sql")
    void 프로젝트_ID가_빈_문자열이면_기본_프로젝트_ID를_조회한다() {
        // when
        Long actual = resolver.resolve("  ", "T1");

        // then
        assertThat(actual).isEqualTo(1L);
    }

    @Test
    void 잘못된_형식의_프로젝트_ID면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> resolver.resolve("invalid", "T1"))
                .isInstanceOf(InvalidProjectIdException.class)
                .hasMessageContaining("잘못된 project_id 값입니다: invalid");
    }

    @Test
    void 워크스페이스를_찾을_수_없으면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> resolver.resolve(null, "T_UNKNOWN"))
                .isInstanceOf(DefaultProjectNotFoundException.class)
                .hasMessageContaining("teamId로 기본 프로젝트를 찾을 수 없습니다: T_UNKNOWN");
    }

    @Test
    @Sql("classpath:sql/fixtures/reservation/workspace_team1.sql")
    void 워크스페이스는_있지만_프로젝트를_찾을_수_없으면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> resolver.resolve(null, "T1"))
                .isInstanceOf(DefaultProjectNotFoundException.class)
                .hasMessageContaining("teamId로 기본 프로젝트를 찾을 수 없습니다: T1");
    }

    @Test
    void 프로젝트_ID가_0이면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> resolver.resolve("0", "T1"))
                .isInstanceOf(InvalidProjectIdException.class)
                .hasMessageContaining("잘못된 project_id 값입니다: 0");
    }

    @Test
    void 프로젝트_ID가_음수면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> resolver.resolve("-1", "T1"))
                .isInstanceOf(InvalidProjectIdException.class)
                .hasMessageContaining("잘못된 project_id 값입니다: -1");
    }
}
