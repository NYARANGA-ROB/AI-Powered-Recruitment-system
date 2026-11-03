package com.smartats.module.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartats.common.exception.BusinessException;
import com.smartats.common.result.ResultCode;
import com.smartats.infrastructure.email.EmailService;
import com.smartats.module.auth.entity.User;
import com.smartats.module.auth.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Verification code service
 *
 * Responsibilities:
 * 1. Generate a 6-digit random verification code
 * 2. Send the code to the email address
 * 3. Verify that the code is correct
 * 4. Anti-abuse throttling (only one send per 60 seconds)
 * 5. Check whether the email address is already registered
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    private final EmailService emailService;
    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;

    // Redis key prefixes
    private static final String CODE_KEY_PREFIX = "verification_code:";
    private static final String LIMIT_KEY_PREFIX = "verification_code_limit:";

    // Code length
    private static final int CODE_LENGTH = 6;

    // Expiry times
    private static final long CODE_EXPIRE_SECONDS = 300;        // 5 minutes
    private static final long LIMIT_EXPIRE_SECONDS = 60;         // 60 seconds

    /**
     * Send a verification code
     *
     * @param email the email address
     */
    public void sendVerificationCode(String email) {
        log.info("Verification code request: email={}", email);

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 1: check whether the email address is already registered
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        Long emailCount = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (emailCount > 0) {
            log.warn("Failed to send the verification code: the email is already registered - email={}", email);
            throw new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS);
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 2: check the anti-abuse throttling
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        String limitKey = LIMIT_KEY_PREFIX + email;
        Boolean isLimited = redisTemplate.hasKey(limitKey);

        if (Boolean.TRUE.equals(isLimited)) {
            log.warn("Verification codes are being sent too frequently: email={}", email);
            throw new BusinessException(ResultCode.VERIFICATION_CODE_SEND_TOO_FREQUENT);
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 3: generate a 6-digit random code
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        String code = generateCode();
        log.info("Verification code generated: email={}, code={}", email, code);

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 4: store the code in Redis (expires after 5 minutes)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        String codeKey = CODE_KEY_PREFIX + email;
        redisTemplate.opsForValue().set(codeKey, code, CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 5: store the anti-abuse marker (expires after 60 seconds)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        redisTemplate.opsForValue().set(limitKey, "1", LIMIT_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Step 6: send the email
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

        boolean success = emailService.sendVerificationCode(email, code);

        if (!success) {
            log.error("Failed to send the verification code email: email={}", email);
            throw new BusinessException(ResultCode.VERIFICATION_CODE_SEND_FAILED);
        }

        log.info("Verification code sent successfully: email={}", email);
    }

    /**
     * Verify a code
     *
     * @param email the email address
     * @param code the code entered by the user
     * @return whether verification succeeded
     */
    public boolean verifyCode(String email, String code) {
        log.info("Verifying the verification code: email={}, code={}", email, code);

        String codeKey = CODE_KEY_PREFIX + email;
        String storedCode = redisTemplate.opsForValue().get(codeKey);

        if (storedCode == null) {
            log.warn("The verification code does not exist or has expired: email={}", email);
            return false;
        }

        boolean isValid = storedCode.equals(code);

        if (isValid) {
            log.info("Verification code accepted: email={}", email);

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // Delete the code after a successful verification (single use)
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

            redisTemplate.delete(codeKey);
        } else {
            log.warn("Verification code rejected: email={}, input={}, stored={}", email, code, storedCode);
        }

        return isValid;
    }

    /**
     * Generate a 6-digit random numeric code
     *
     * @return a 6-digit numeric string
     */
    private String generateCode() {
        Random random = new Random();
        StringBuilder code = new StringBuilder();

        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(random.nextInt(10));  // a random digit from 0-9
        }

        return code.toString();
    }
}
