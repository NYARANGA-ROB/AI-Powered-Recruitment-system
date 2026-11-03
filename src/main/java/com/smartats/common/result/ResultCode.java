package com.smartats.common.result;

import lombok.Getter;

/**
 * Unified error code definitions
 *
 * @Getter annotation: Lombok generates the getter methods automatically
 */
@Getter
public enum ResultCode {

    // ========== Success ==========
    SUCCESS(200, "Operation completed successfully"),

    // ========== Generic errors 4xxxx ==========
    BAD_REQUEST(40001, "Parameter validation failed"), UNAUTHORIZED(40101, "Not authenticated, please sign in first"), FORBIDDEN(40301, "Access denied"), NOT_FOUND(40401, "Resource not found"),

    // ========== Business errors 5xxxx ==========
    INTERNAL_ERROR(50001, "Internal server error"), AI_SERVICE_ERROR(50002, "AI service is unavailable"), FILE_UPLOAD_ERROR(50003, "File upload failed"),

    // ========== Auth module errors 10xxx ==========
    // User related
    USER_NOT_FOUND(10001, "User not found"), USER_ALREADY_EXISTS(10002, "User already exists"), USERNAME_ALREADY_EXISTS(10003, "Username already exists"), EMAIL_ALREADY_EXISTS(10004, "Email address is already registered"), PASSWORD_ERROR(10005, "Incorrect password"), INVALID_CREDENTIALS(10006, "Invalid username or password"), USER_DISABLED(10007, "Account is disabled"), ACCOUNT_DISABLED(10008, "Account is disabled, please contact the administrator"), TOKEN_INVALID(10009, "Token is invalid or has expired"), TOKEN_REFRESH_FAILED(10010, "Token refresh failed"),

    // ========== Resume module errors 20xxx ==========
    RESUME_NOT_FOUND(20001, "Resume not found"), RESUME_ALREADY_PARSED(20002, "Resume has already been parsed, please do not submit it again"), FILE_TYPE_NOT_SUPPORTED(20003, "Unsupported file type"), FILE_SIZE_EXCEEDED(20004, "File size exceeds the limit"), RESUME_DUPLICATE(20005, "Resume file already exists"),
    BATCH_UPLOAD_LIMIT_EXCEEDED(20006, "Batch upload file count exceeds the limit (maximum 20)"),
    UPLOAD_RATE_LIMITED(20007, "Uploading too frequently, please try again later"),

    // ========== AI related errors 30xxx ==========
    AI_QUOTA_EXCEEDED(30001, "AI call quota exceeded"),
    AI_PARSE_FAILED(30002, "Resume parsing failed"),

    // ========== Verification code errors 11xxx ==========
    VERIFICATION_CODE_SEND_TOO_FREQUENT(11001, "Verification codes are being sent too frequently, please try again later"),
    VERIFICATION_CODE_SEND_FAILED(11002, "Failed to send the verification code, please try again later"),
    VERIFICATION_CODE_INVALID(11003, "Verification code is incorrect or has expired"),
    VERIFICATION_CODE_REQUIRED(11004, "Please request a verification code first"),

    // ========== Job application module errors 42xxx ==========
    APPLICATION_NOT_FOUND(42001, "Application record not found"),
    APPLICATION_DUPLICATE(42002, "This candidate has already applied for the position"),
    APPLICATION_STATUS_INVALID(42003, "Invalid application status transition"),
    APPLICATION_JOB_NOT_PUBLISHED(42004, "The position is not published and cannot be applied for"),

    // ========== Interview module errors 43xxx ==========
    INTERVIEW_NOT_FOUND(43001, "Interview record not found"),
    INTERVIEW_ALREADY_COMPLETED(43002, "The interview is already completed and can no longer be modified"),
    INTERVIEW_ALREADY_CANCELLED(43003, "The interview has been cancelled"),
    INTERVIEW_TIME_CONFLICT(43004, "Interview time conflict");
    /**
     * Error code
     */
    private final int code;

    /**
     * Error message
     */
    private final String message;

    /**
     * Constructor
     */
    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
