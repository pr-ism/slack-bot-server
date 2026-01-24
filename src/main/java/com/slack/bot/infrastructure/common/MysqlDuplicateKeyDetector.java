package com.slack.bot.infrastructure.common;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Component;

@Component
public class MysqlDuplicateKeyDetector {

    public boolean isDuplicateKey(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException) {
                return true;
            }
            if (current instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();

                // MySQL 무결성 제약 조건 코드
                if ("23000".equals(sqlState) && sqlException.getErrorCode() == 1062) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    public boolean isNotDuplicateKey(Throwable throwable) {
        return !isDuplicateKey(throwable);
    }
}
