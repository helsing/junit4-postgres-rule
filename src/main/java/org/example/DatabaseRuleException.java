package org.example;

public class DatabaseRuleException extends RuntimeException {
    public DatabaseRuleException(String message) {
        super(message);
    }

    public DatabaseRuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
