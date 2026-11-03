package com.smartats.common.constants;

/**
 * Redis key constant definitions
 * <p>
 * Centralises all Redis key prefixes to avoid hard-coding and typos
 * <p>
 * Naming conventions:
 * - Upper case, separated by underscores
 * - Ends with _KEY_PREFIX or _KEY
 * - Levels separated by colons (e.g. jwt:token:{userId})
 */
public class RedisKeyConstants {

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // JWT token related
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * JWT access token key prefix
     * <p>
     * Full format: jwt:token:{userId}
     * <p>
     * Value: the accessToken string
     * <p>
     * TTL: 2 hours (matches the accessToken expiry)
     */
    public static final String JWT_TOKEN_KEY_PREFIX = "jwt:token:";

    /**
     * JWT refresh token key prefix
     * <p>
     * Full format: jwt:refresh:{userId}
     * <p>
     * Value: the refreshToken string
     * <p>
     * TTL: 7 days (matches the refreshToken expiry)
     */
    public static final String JWT_REFRESH_TOKEN_KEY_PREFIX = "jwt:refresh:";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Verification code related
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Email verification code key prefix
     * <p>
     * Full format: verify:code:{email}
     * <p>
     * Value: the verification code (6 digits)
     * <p>
     * TTL: 5 minutes
     */
    public static final String VERIFICATION_CODE_KEY_PREFIX = "verify:code:";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // AI quota related
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * AI call rate limit key prefix
     * <p>
     * Full format: rate:ai:{userId}:{date}
     * <p>
     * Value: number of calls made today
     * <p>
     * TTL: 24 hours (expires at the end of the calendar day)
     */
    public static final String AI_QUOTA_KEY_PREFIX = "rate:ai:";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Task status related
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Resume parsing task status key prefix
     * <p>
     * Full format: task:resume:{taskId}
     * <p>
     * Value: task status in JSON format (contains status, progress, message, etc.)
     * <p>
     * TTL: 24 hours
     */
    public static final String RESUME_TASK_KEY_PREFIX = "task:resume:";

    /**
     * Resume parsing distributed lock key prefix
     * <p>
     * Full format: lock:resume:{fileHash}
     * <p>
     * Value: lock identifier
     * <p>
     * TTL: 10 minutes
     */
    public static final String RESUME_LOCK_KEY_PREFIX = "lock:resume:";

    /**
     * Resume parsing idempotency check key prefix
     * <p>
     * Full format: idempotent:resume:{resumeId}
     * <p>
     * Value: processed marker
     * <p>
     * TTL: 1 hour
     */
    public static final String RESUME_IDEMPOTENT_KEY_PREFIX = "idempotent:resume:";

    /**
     * Resume file de-duplication key prefix
     * <p>
     * Full format: dedup:resume:{fileHash}
     * <p>
     * Value: already-exists marker
     * <p>
     * TTL: 7 days
     */
    public static final String RESUME_DEDUP_KEY_PREFIX = "dedup:resume:";

    /**
     * Batch upload rate limit key prefix
     * <p>
     * Full format: rate:upload:{userId}
     * <p>
     * Value: number of batch uploads in the current minute
     * <p>
     * TTL: 60 seconds
     */
    public static final String UPLOAD_RATE_LIMIT_KEY_PREFIX = "rate:upload:";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Job cache related
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Job detail cache key prefix
     * <p>
     * Full format: cache:job:{jobId}
     * <p>
     * Value: job detail object in JSON format
     * <p>
     * TTL: 30 minutes
     */
    public static final String CACHE_JOB_KEY_PREFIX = "cache:job:";

    /**
     * Hot job ranking key
     * <p>
     * Full format: cache:job:hot
     * <p>
     * Type: ZSet (sorted set)
     * <p>
     * Score: view count
     * <p>
     * TTL: 10 minutes
     */
    public static final String CACHE_JOB_HOT_KEY = "cache:job:hot";

    /**
     * Job view counter key prefix
     * <p>
     * Full format: counter:job:view:{jobId}
     * <p>
     * Value: view count (plain number)
     * <p>
     * TTL: never expires (deleted manually once the scheduled task has synced it to the database)
     */
    public static final String COUNTER_JOB_VIEW_PREFIX = "counter:job:view:";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Candidate cache related
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Candidate detail cache key prefix
     * <p>
     * Full format: cache:candidate:{candidateId}
     * <p>
     * Value: candidate detail object in JSON format
     * <p>
     * TTL: 30 minutes
     */
    public static final String CACHE_CANDIDATE_KEY_PREFIX = "cache:candidate:";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Job application cache related
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Job application detail cache key prefix
     * Full format: cache:application:{applicationId}
     * Value: application detail in JSON format
     * TTL: 30 minutes
     */
    public static final String CACHE_APPLICATION_KEY_PREFIX = "cache:application:";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Interview record cache related
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Interview record detail cache key prefix
     * Full format: cache:interview:{interviewId}
     * Value: interview record detail in JSON format
     * TTL: 30 minutes
     */
    public static final String CACHE_INTERVIEW_KEY_PREFIX = "cache:interview:";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Recruitment analytics cache related
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Recruitment analytics overview cache key prefix
     * <p>
     * Full format: cache:analytics:overview:{jobId}:{startDate}:{endDate}
     * <p>
     * Value: RecruitmentOverviewDTO in JSON format
     * <p>
     * TTL: 5 minutes
     */
    public static final String CACHE_ANALYTICS_KEY_PREFIX = "cache:analytics:overview:";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Role prefix
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Spring Security role prefix
     * <p>
     * Format: ROLE_{role}
     * <p>
     * Examples: ROLE_ADMIN, ROLE_HR, ROLE_INTERVIEWER
     */
    public static final String ROLE_PREFIX = "ROLE_";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Constructor
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Private constructor to prevent instantiation
     *
     * @throws UnsupportedOperationException if instantiation is attempted
     */
    private RedisKeyConstants() {
        throw new UnsupportedOperationException("Constant classes cannot be instantiated");
    }
}
