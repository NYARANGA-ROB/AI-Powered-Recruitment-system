package com.smartats.common.exception;

import com.smartats.common.result.ResultCode;
import lombok.Getter;

/**
 * Business exception (a known, recoverable error)
 * <p>
 * Usage:
 * throw new
 * BusinessException(ResultCode.USER_NOT_FOUND);
 * <p>
 * Difference from a system exception (RuntimeException):
 * - Business exception: anticipated and handled (user not found, incorrect password)
 * - System exception: unexpected and unrecoverable (database outage, NPE)
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * Error code
     */
    private final int code;

    /**
     * Error message
     */
    private final String message;

    /**
     * Constructor 1: use the error code enum (with its default message)
     * <p>
     * Usage: throw new BusinessException(ResultCode.USER_NOT_FOUND)
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.message = resultCode.getMessage();
    }

    /**
     * Constructor 2: use the error code enum plus a custom message
     * <p>
     * Usage: throw new BusinessException(ResultCode.INVALID_CREDENTIALS, "Invalid username or password")
     * <p>
     * Scenario: reuse the ResultCode error code while returning a custom message (e.g. a generic error hint)
     */
    public BusinessException(ResultCode resultCode, String customMessage) {
        super(customMessage);
        this.code = resultCode.getCode();
        this.message = customMessage;
    }

    /**
     * Constructor 3: fully custom error code and message
     * <p>
     * Usage: throw new BusinessException(50001, "Internal server error")
     * <p>
     * Scenario: an ad hoc error code that is not part of the ResultCode enum
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
}