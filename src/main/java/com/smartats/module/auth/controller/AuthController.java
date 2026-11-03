package com.smartats.module.auth.controller;

import com.smartats.common.annotation.AuditLog;
import com.smartats.common.result.Result;
import com.smartats.module.auth.dto.request.LoginRequest;
import com.smartats.module.auth.dto.request.RefreshTokenRequest;
import com.smartats.module.auth.dto.request.RegisterRequest;
import com.smartats.module.auth.dto.request.SendVerificationCodeRequest;
import com.smartats.module.auth.dto.response.LoginResponse;
import com.smartats.module.auth.service.UserService;
import com.smartats.module.auth.service.VerificationCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller
 */
@Tag(name = "Auth Management", description = "User registration, login, token refresh and email verification codes")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final VerificationCodeService verificationCodeService;
    /**
     * User registration
     * POST /api/v1/auth/register
     */
    @Operation(summary = "Register a user", description = "Register a new user; an email verification code is required and the password is stored BCrypt-encrypted")
    @AuditLog(module = "Auth Management", operation = "REGISTER", description = "Register a user", saveParams = false)
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
        return Result.success();
    }

    /**
     * User login
     * POST /api/v1/auth/login
     */
    @Operation(summary = "Log in", description = "Returns accessToken(2h) + refreshToken(7d)")
    @AuditLog(module = "Auth Management", operation = "LOGIN", description = "User login", saveParams = false)
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return Result.success(response);
    }

    /**
     * Send a verification code
     * <p>
     * POST /api/v1/auth/send-verification-code
     */
    @Operation(summary = "Send an email verification code", description = "Rate limited to one send per 60 seconds; the code is valid for 5 minutes")
    @PostMapping("/send-verification-code")
    public Result<Void> sendVerificationCode(@Valid @RequestBody SendVerificationCodeRequest request) {
        verificationCodeService.sendVerificationCode(request.getEmail());
        return Result.success();
    }

    /**
     * Refresh the access token
     * <p>
     * POST /api/v1/auth/refresh
     * <p>
     * Exchange the refreshToken returned at login for a new accessToken
     */
    @Operation(summary = "Refresh the token", description = "Exchange the refreshToken for a new accessToken")
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse response = userService.refreshToken(request.getRefreshToken());
        return Result.success(response);
    }

    /**
     * Test the JWT authentication endpoint
     * <p>
     * GET /api/v1/auth/test
     * <p>
     * Requires a valid JWT token to access
     * <p>
     * Used to verify that the JWT authentication filter works as expected
     */
    @Operation(summary = "Test JWT authentication", description = "Check whether the JWT token is valid")
    @GetMapping("/test")
    public Result<Object> testAuthentication(Authentication authentication) {
        return Result.success(new TestAuthResponse(
                (Long) authentication.getPrincipal(),
                authentication.getAuthorities(),
                "JWT authentication succeeded!"
        ));
    }

    /**
     * Test authentication response object
     */
    private record TestAuthResponse(
            Long userId,
            Object authorities,
            String message
    ) {}
}
