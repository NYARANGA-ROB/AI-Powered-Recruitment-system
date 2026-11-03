package com.smartats.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartats.common.constants.RedisKeyConstants;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.module.auth.dto.request.LoginRequest;
import com.smartats.module.auth.dto.request.RegisterRequest;
import com.smartats.module.auth.dto.response.LoginResponse;
import com.smartats.module.auth.entity.User;
import com.smartats.module.auth.mapper.UserMapper;
import com.smartats.module.auth.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final VerificationCodeService verificationCodeService;
    private final StringRedisTemplate redisTemplate;

    /**
     * User registration
     */
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest request) {
        log.info("User registration request: username={}, email={}", request.getUsername(), request.getEmail());
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 0: verify the email verification code
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        boolean codeValid = verificationCodeService.verifyCode(request.getEmail(), request.getVerificationCode());

        if (!codeValid) {
            log.warn("Registration failed: incorrect verification code - email={}", request.getEmail());
            throw new BusinessException(ResultCode.VERIFICATION_CODE_INVALID);
        }
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 1: check whether the username already exists
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        Long usernameCount = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (usernameCount > 0) {
            log.warn("Registration failed: the username already exists - {}", request.getUsername());
            throw new BusinessException(ResultCode.USERNAME_ALREADY_EXISTS);
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 2: check whether the email already exists
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        Long emailCount = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail()));
        if (emailCount > 0) {
            log.warn("Registration failed: the email already exists - {}", request.getEmail());
            throw new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS);
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 3: encrypt the password (BCrypt)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        log.debug("The password has been encrypted");

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 4: create the user object
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(encodedPassword);
        user.setEmail(request.getEmail());
        user.setRole(request.getRole() != null ? request.getRole() : "HR");
        user.setDailyAiQuota(100);
        user.setStatus(1);

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 5: save to the database
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        int insertResult = userMapper.insert(user);
        if (insertResult <= 0) {
            log.error("Registration failed: the database insert failed - username={}", request.getUsername());
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "Registration failed, please try again later");
        }

        log.info("Registration succeeded: userId={}, username={}", user.getId(), user.getUsername());
    }

    /**
     * User login
     */
    public LoginResponse login(LoginRequest request) {
        log.info("User login request: username={}", request.getUsername());

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 1: look up the user by username
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 2: check that the user exists
        // ⚠️ Security note: always return "Invalid username or password" to prevent username enumeration attacks
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        if (user == null) {
            log.warn("Login failed: the user does not exist - {}", request.getUsername());
            throw new BusinessException(ResultCode.INVALID_CREDENTIALS, "Invalid username or password");
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 3: verify the password
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        boolean passwordMatch = passwordEncoder.matches(request.getPassword(), user.getPassword());
        if (!passwordMatch) {
            log.warn("Login failed: incorrect password - username={}", request.getUsername());
            throw new BusinessException(ResultCode.INVALID_CREDENTIALS, "Invalid username or password");
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 4: check the account status
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        if (user.getStatus() == 0) {
            log.warn("Login failed: the account is disabled - username={}", request.getUsername());
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED, "Account is disabled, please contact the administrator");
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 5: generate the tokens
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        String accessToken = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        log.info("Tokens generated successfully: userId={}, username={}", user.getId(), user.getUsername());

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 6: build the response object
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        int todayAiUsed = getTodayAiUsed(user.getId());
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                user.getId(), user.getUsername(), user.getEmail(),
                user.getRole(), user.getDailyAiQuota(), todayAiUsed
        );

        LoginResponse response = new LoginResponse(accessToken, refreshToken, 7200L,  // expiresIn (seconds)
                userInfo);

        log.info("Login succeeded: userId={}, username={}", user.getId(), user.getUsername());

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 6: store the tokens in Redis (supports token revocation)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        // Store the accessToken in Redis (Key: jwt:token:{userId})
        String accessTokenKey = RedisKeyConstants.JWT_TOKEN_KEY_PREFIX + user.getId();
        redisTemplate.opsForValue().set(
                accessTokenKey,
                accessToken,
                jwtUtil.getExpiration(),
                TimeUnit.SECONDS
        );

        // Store the refreshToken in Redis (Key: jwt:refresh:{userId})
        String refreshTokenKey = RedisKeyConstants.JWT_REFRESH_TOKEN_KEY_PREFIX + user.getId();
        redisTemplate.opsForValue().set(
                refreshTokenKey,
                refreshToken,
                jwtUtil.getRefreshExpiration(),
                TimeUnit.SECONDS
        );

        log.info("Tokens stored in Redis: userId={}, accessTokenExpire={}s, refreshTokenExpire={}s",
                user.getId(), jwtUtil.getExpiration(), jwtUtil.getRefreshExpiration());
        return response;
    }

    /**
     * Exchange a refreshToken for a new accessToken
     *
     * @param refreshToken the refresh token held by the client
     * @return the login response containing the new accessToken
     */
    public LoginResponse refreshToken(String refreshToken) {
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 1: parse the refreshToken to obtain the username
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        String username;
        try {
            username = jwtUtil.getUsernameFromToken(refreshToken);
        } catch (Exception e) {
            log.warn("Failed to parse the refreshToken: {}", e.getMessage());
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Invalid refreshToken");
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 2: look up the user information
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "User not found");
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 3: compare against the refreshToken stored in Redis
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        String storedRefreshToken = redisTemplate.opsForValue()
                .get(RedisKeyConstants.JWT_REFRESH_TOKEN_KEY_PREFIX + user.getId());
        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            log.warn("The refreshToken does not match or is no longer valid: userId={}", user.getId());
            throw new BusinessException(ResultCode.UNAUTHORIZED, "The refreshToken has expired, please sign in again");
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 4: issue a new accessToken and rotate the refreshToken
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        String newAccessToken = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getUsername());

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 5: update Redis
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        redisTemplate.opsForValue().set(
                RedisKeyConstants.JWT_TOKEN_KEY_PREFIX + user.getId(),
                newAccessToken,
                jwtUtil.getExpiration(),
                java.util.concurrent.TimeUnit.SECONDS
        );
        redisTemplate.opsForValue().set(
                RedisKeyConstants.JWT_REFRESH_TOKEN_KEY_PREFIX + user.getId(),
                newRefreshToken,
                jwtUtil.getRefreshExpiration(),
                java.util.concurrent.TimeUnit.SECONDS
        );

        log.info("Token refreshed successfully: userId={}, username={}", user.getId(), user.getUsername());

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 6: build the response
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        int refreshTodayAiUsed = getTodayAiUsed(user.getId());
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                user.getId(), user.getUsername(), user.getEmail(),
                user.getRole(), user.getDailyAiQuota(), refreshTodayAiUsed);

        return new LoginResponse(newAccessToken, newRefreshToken, jwtUtil.getExpiration(), userInfo);
    }

    /**
     * Get the user's AI call count for today from Redis
     * <p>
     * Key format: rate:ai:{userId}:{date} (e.g. rate:ai:1:2026-02-24)
     *
     * @param userId the user ID
     * @return the number of calls used today, or 0 when there is no record
     */
    private int getTodayAiUsed(Long userId) {
        try {
            String date = java.time.LocalDate.now().toString();
            String key = RedisKeyConstants.AI_QUOTA_KEY_PREFIX + userId + ":" + date;
            String value = redisTemplate.opsForValue().get(key);
            return value != null ? Integer.parseInt(value) : 0;
        } catch (Exception e) {
            log.warn("Failed to get today's AI usage count (falling back to 0): userId={}", userId, e);
            return 0;
        }
    }
}
