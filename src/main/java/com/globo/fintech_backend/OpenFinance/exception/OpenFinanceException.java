package com.globo.fintech_backend.OpenFinance.exception;

public class OpenFinanceException extends RuntimeException {
    public OpenFinanceException(String message) {
        super(message);
    }

    public OpenFinanceException(String message, Throwable cause) {
        super(message, cause);
    }
}
