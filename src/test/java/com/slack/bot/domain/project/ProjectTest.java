package com.slack.bot.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProjectTest {

    @Test
    void 프로젝트를_생성한다() {
        // when & then
        Project actual = assertDoesNotThrow(() -> Project.create("My Project", "test-api-key", 1L));

        assertAll(
                () -> assertThat(actual.getName()).isEqualTo("My Project"),
                () -> assertThat(actual.getApiKey()).isEqualTo("test-api-key"),
                () -> assertThat(actual.getUserId()).isEqualTo(1L)
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 프로젝트_이름이_비어_있다면_프로젝트를_생성할_수_없다(String name) {
        // when & then
        assertThatThrownBy(() -> Project.create(name, "test-api-key", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("프로젝트 이름은 비어 있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void api_key가_비어_있다면_프로젝트를_생성할_수_없다(String apiKey) {
        // when & then
        assertThatThrownBy(() -> Project.create("My Project", apiKey, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("api key는 비어 있을 수 없습니다.");
    }

    @Test
    void 프로젝트를_생성한_회원_식별자가_비어_있다면_프로젝트를_생성할_수_없다() {
        // when & then
        assertThatThrownBy(() -> Project.create("My Project", "test-api-key", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("해당 프로젝트를 생성한 회원의 식별자는 비어 있을 수 없습니다.");
    }

    @Test
    void 프로젝트_이름을_변경한다() {
        // given
        Project project = Project.create("Old Name", "test-api-key", 1L);

        // when
        project.changeName("New Name");

        // then
        assertThat(project.getName()).isEqualTo("New Name");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 변경할_프로젝트_이름이_비어_있다면_변경할_수_없다(String changedName) {
        // given
        Project project = Project.create("Old Name", "test-api-key", 1L);

        // when & then
        assertThatThrownBy(() -> project.changeName(changedName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("프로젝트 이름은 비어 있을 수 없습니다.");
    }

    @Test
    void api_key를_변경한다() {
        // given
        Project project = Project.create("My Project", "old-api-key", 1L);

        // when
        project.changeApiKey("new-api-key");

        // then
        assertThat(project.getApiKey()).isEqualTo("new-api-key");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void 변경할_api_key가_비어_있다면_변경할_수_없다(String changedApiKey) {
        // given
        Project project = Project.create("My Project", "old-api-key", 1L);

        // when & then
        assertThatThrownBy(() -> project.changeApiKey(changedApiKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("api key는 비어 있을 수 없습니다.");
    }
}
