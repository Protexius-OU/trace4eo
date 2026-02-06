package com.guardtime.trace4eo.config;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class BrokerTokenException extends RuntimeException {
    public BrokerTokenException(String message) {
        super(message);
    }
}
