package com.smartats.module.auth.dto.request;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

/**
 * User login request
 */
@Data
public class LoginRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}
