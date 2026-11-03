package com.smartats.module.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * Access token (valid for 2 hours)
     */
    private String accessToken;

    /**
     * Refresh token (valid for 7 days)
     */
    private String refreshToken;

    /**
     * Expiry time (seconds)
     */
    private Long expiresIn;

    /**
     * User information
     */
    private UserInfo userInfo;

    /**
     * User information (nested object)
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private Long userId;
        private String username;
        private String email;
        private String role;
        private Integer dailyAiQuota;
        private Integer todayAiUsed;
    }
}