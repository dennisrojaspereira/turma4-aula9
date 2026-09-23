package com.techpix.shared;

import org.springframework.http.HttpStatus;

/** Erro de negócio com o status HTTP que deve ser exposto ao cliente. */
public class DomainException extends RuntimeException {

    private final HttpStatus status;

    public DomainException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static DomainException notFound(String message) {
        return new DomainException(HttpStatus.NOT_FOUND, message);
    }

    public static DomainException unprocessable(String message) {
        return new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
