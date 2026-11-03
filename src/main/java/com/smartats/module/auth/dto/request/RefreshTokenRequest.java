package com.smartats.module.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Refresh token request
 */
@Data
public class RefreshTokenRequest {

    /**
     * The refresh token (returned by the login endpoint)
     */
    @NotBlank(message = "refreshToken is required")
    private String refreshToken;
}
