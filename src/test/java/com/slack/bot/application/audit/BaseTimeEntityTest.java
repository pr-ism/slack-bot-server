package com.slack.bot.application.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@SuppressWarnings("NonAsciiCharacters")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BaseTimeEntityTest {

    @Autowired
    EntityManager entityManager;

    @Test
    @Transactional
    void 특정_엔티티_저장_시_auditing이_적용된다() {
        // given
        TestAuditEntity entity = new TestAuditEntity("name");

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

        entityManager.persist(entity);
        entityManager.flush();

        // when
        entity.changeName("change");

        entityManager.flush();
        entityManager.clear();

        // then
        TestAuditEntity actual = entityManager.find(TestAuditEntity.class, entity.getId());

        assertThat(actual.getCreatedAt()).isBefore(actual.getUpdatedAt());
    }
}
