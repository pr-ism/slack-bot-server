package com.slack.bot.infrastructure.common;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Component;

@Component
public class MysqlDuplicateKeyDetector {

    public boolean isDuplicateKey(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException cve && isMysqlDuplicate(cve.getSQLException())) {
                return true;
            }
            if (current instanceof SQLException sqlException && isMysqlDuplicate(sqlException)) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    public boolean isNotDuplicateKey(Throwable throwable) {
        return !isDuplicateKey(throwable);
    }

    private boolean isMysqlDuplicate(SQLException sqlException) {
        if (sqlException == null) {
            return false;
        }

        // MySQL 무결성 제약 조건 코드
        return "23000".equals(sqlException.getSQLState()) && sqlException.getErrorCode() == 1062;
    }
}
