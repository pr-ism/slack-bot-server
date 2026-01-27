package com.slack.bot.domain.link;

import com.slack.bot.domain.link.dto.AccessLinkSequenceBlockDto;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "access_link_sequences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccessLinkSequence {

    public static final Long DEFAULT_ID = 1L;

    @Id
    private Long id;

    private Long nextValue;

    public static AccessLinkSequence create(Long id, Long nextValue) {
        validateId(id);
        validateNextValue(nextValue);

        return new AccessLinkSequence(id, nextValue);
    }

    private AccessLinkSequence(Long id, Long nextValue) {
        this.id = id;
        this.nextValue = nextValue;
    }

    public AccessLinkSequenceBlockDto allocateBlock(Long size) {
        if (size == null || size <= 0L) {
            throw new IllegalStateException("블록 크기는 0보다 커야 합니다.");
        }
        long start = addExact(nextValue, 1L);
        long end = addExact(nextValue, size);
        validateBlockRange(start, end);
        nextValue = end;

        return new AccessLinkSequenceBlockDto(start, end);
    }

    private static void validateId(Long id) {
        if (!DEFAULT_ID.equals(id)) {
            throw new IllegalStateException("시퀀스 ID는 기본값과 일치해야 합니다.");
        }
    }

    private static void validateNextValue(Long nextValue) {
        if (nextValue == null || nextValue < 0L) {
            throw new IllegalStateException("다음 값은 0 이상이어야 합니다.");
        }
    }

    private static void validateBlockRange(long start, long end) {
        if (start <= 0L || end < start) {
            throw new IllegalStateException("블록 범위가 올바르지 않습니다.");
        }
    }

    private static long addExact(Long left, Long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ex) {
            throw new IllegalStateException("블록 범위가 올바르지 않습니다.");
        }
    }
}
