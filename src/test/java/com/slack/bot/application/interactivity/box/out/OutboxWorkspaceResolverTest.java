package com.slack.bot.application.interactivity.box.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.slack.bot.application.interactivity.box.out.exception.OutboxWorkspaceNotFoundException;
import com.slack.bot.domain.workspace.Workspace;
import com.slack.bot.domain.workspace.repository.WorkspaceRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OutboxWorkspaceResolverTest {

    WorkspaceRepository workspaceRepository;

    OutboxWorkspaceResolver resolver;

    @BeforeEach
    void setUp() {
        workspaceRepository = mock(WorkspaceRepository.class);
        resolver = new OutboxWorkspaceResolver(workspaceRepository);
    }

    @Test
    void 토큰에_해당하는_workspace가_있으면_team_id를_반환한다() {
        // given
        Workspace workspace = mock(Workspace.class);
        given(workspace.getTeamId()).willReturn("T1");
        given(workspaceRepository.findByAccessToken("token")).willReturn(Optional.of(workspace));

        // when
        String actual = resolver.resolve("token");

        // then
        assertAll(
                () -> assertThat(actual).isEqualTo("T1"),
                () -> verify(workspaceRepository).findByAccessToken("token")
        );
    }

    @Test
    void 토큰에_해당하는_workspace가_없으면_예외를_던진다() {
        // given
        given(workspaceRepository.findByAccessToken("missing-token")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> resolver.resolve("missing-token"))
                .isInstanceOf(OutboxWorkspaceNotFoundException.class)
                .hasMessageContaining("outbox 적재 대상 워크스페이스를 찾을 수 없습니다.");
    }

    @Test
    void null_토큰으로_조회하면_예외_메시지에_tokenPrefix를_포함한다() {
        // given
        given(workspaceRepository.findByAccessToken(null)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(OutboxWorkspaceNotFoundException.class)
                .hasMessageContaining("tokenPrefix=empty");
    }

    @Test
    void 빈_토큰으로_조회하면_예외_메시지에_tokenPrefix를_포함한다() {
        // given
        given(workspaceRepository.findByAccessToken("")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> resolver.resolve(""))
                .isInstanceOf(OutboxWorkspaceNotFoundException.class)
                .hasMessageContaining("tokenPrefix=empty");
    }

    @Test
    void 정상_토큰_미존재_시_예외_메시지에_마스킹된_토큰을_포함한다() {
        // given
        given(workspaceRepository.findByAccessToken("missing-token-123")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> resolver.resolve("missing-token-123"))
                .isInstanceOf(OutboxWorkspaceNotFoundException.class)
                .hasMessageContaining("tokenPrefix=missing-***");
    }
}
