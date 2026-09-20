package com.globo.fintech_backend.exception;

import com.globo.fintech_backend.Auth.exception.InvalidCredentialsException;
import com.globo.fintech_backend.OpenFinance.exception.OpenFinanceException;
import com.globo.fintech_backend.OpenFinance.exception.OpenFinanceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<String> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(401).body(ex.getMessage());
    }

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<String> handleUnauthenticated(UnauthenticatedException ex) {
        return ResponseEntity.status(401).body(ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<String> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(404).body(ex.getMessage());
    }

    @ExceptionHandler(OpenFinanceUnavailableException.class)
    public ResponseEntity<String> handleOpenFinanceUnavailable(OpenFinanceUnavailableException ex) {
        return ResponseEntity.status(503).body(ex.getMessage());
    }

    @ExceptionHandler(OpenFinanceException.class)
    public ResponseEntity<String> handleOpenFinance(OpenFinanceException ex) {
        log.error("Open Finance provider error", ex);
        return ResponseEntity.status(502).body(ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(500).body("Erro inesperado, tente novamente");
    }
}
