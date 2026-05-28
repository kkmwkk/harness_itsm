package com.nkia.itg.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ITGException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    public ITGException(String errorCode, String message) {
        this(errorCode, message, HttpStatus.BAD_REQUEST);
    }

    public ITGException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
