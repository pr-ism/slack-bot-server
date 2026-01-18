package com.slack.bot.application.audit;

import com.slack.bot.domain.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "test_audit_entity")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TestAuditEntity extends BaseTimeEntity {

    private String name;

    public TestAuditEntity(String name) {
        this.name = name;
    }

    public void changeName(String name) {
        this.name = name;
    }
}
