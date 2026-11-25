package com.miayang.ecommerce.common.web;

import org.springframework.http.HttpStatus;

/**
 * A business error with an HTTP status, e.g. 409 "Email is already registered".
 * GlobalExceptionHandler turns it into a JSON error response.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
