package com.slack.bot.application.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;

import com.slack.bot.application.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BaseTimeEntityTest {

    @Autowired
    EntityManager entityManager;

    @Autowired
    DateTimeProvider dateTimeProvider;

    @Test
    @Transactional
    void 특정_엔티티_저장_시_auditing이_적용된다() {
        // given
        TestAuditEntity entity = new TestAuditEntity("name");
        LocalDateTime now = LocalDateTime.now();

        given(dateTimeProvider.getNow()).willReturn(Optional.of(now));

        // when
        entityManager.persist(entity);

        // then
        assertAll(
                () -> assertThat(entity.getId()).isPositive(),
                () -> assertThat(entity.getCreatedAt()).isNotNull(),
                () -> assertThat(entity.getUpdatedAt()).isNotNull()
        );
    }

    @Test
    @Transactional
    void 특정_엔티티_값_변경_시_auditing이_적용된다() {
        // given
        TestAuditEntity entity = new TestAuditEntity("before");
        LocalDateTime now = LocalDateTime.now();

        given(dateTimeProvider.getNow()).willReturn(Optional.of(now));

        entityManager.persist(entity);
        entityManager.flush();

        // when
        entity.changeName("change");

        given(dateTimeProvider.getNow()).willReturn(Optional.of(now.plusSeconds(1)));

        entityManager.flush();
        entityManager.clear();

        // then
        TestAuditEntity actual = entityManager.find(TestAuditEntity.class, entity.getId());

        assertThat(actual.getCreatedAt()).isBefore(actual.getUpdatedAt());
    }
}
