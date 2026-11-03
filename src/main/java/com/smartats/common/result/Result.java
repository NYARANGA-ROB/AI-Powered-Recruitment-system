package com.smartats.common.result;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Unified response result wrapper
 *
 * @Data annotation: generates getter/setter/toString/equals automatically
 * @NoArgsConstructor: generates the no-arg constructor
 * @AllArgsConstructor: generates the all-arg constructor
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /**
     * Response code (200 = success, others = error code)
     */
    private int code;

    /**
     * Response message
     */
    private String message;

    /**
     * Response data (present on success, null on failure)
     */
    private T data;

    /**
     * Timestamp
     */
    private Long timestamp;

    // ==================== Quick builder methods

    /**
     * Success response (with data)
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data, System.currentTimeMillis());
    }

    /**
     * Success response (without data)
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * Failure response (using the error code enum)
     */
    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null, System.currentTimeMillis());
    }

    /**
     * Failure response (custom error)
     */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null, System.currentTimeMillis());
    }
}