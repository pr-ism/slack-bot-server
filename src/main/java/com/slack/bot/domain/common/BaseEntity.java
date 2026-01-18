package com.slack.bot.domain.common;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.Objects;
import lombok.Getter;
import org.hibernate.proxy.HibernateProxy;

@Getter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }

        Class<?> oEffectiveClass;
        if (o instanceof HibernateProxy proxy) {
            oEffectiveClass = proxy.getHibernateLazyInitializer().getPersistentClass();
        } else {
            oEffectiveClass = o.getClass();
        }

        Class<?> thisEffectiveClass;
        if (this instanceof HibernateProxy proxy) {
            thisEffectiveClass = proxy.getHibernateLazyInitializer().getPersistentClass();
        } else {
            thisEffectiveClass = this.getClass();
        }

        if (thisEffectiveClass != oEffectiveClass) {
            return false;
        }
        if (!(o instanceof BaseEntity that)) {
            return false;
        }

        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        if (this instanceof HibernateProxy proxy) {
            return proxy.getHibernateLazyInitializer()
                        .getPersistentClass()
                        .hashCode();
        }

        return getClass().hashCode();
    }
}
