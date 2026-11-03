package com.smartats.common.annotation;

import java.lang.annotation.*;

/**
 * Audit log annotation
 * <p>
 * Applied to Controller / Service methods; the AOP aspect records the operation log automatically.
 * <p>
 * Example:
 * <pre>
 * {@code @AuditLog(module = "Job Management", operation = "CREATE", description = "Create a job")}
 * public Result<Long> createJob(...) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /**
     * Business module name
     * <p>
     * For example: "Job Management", "Resume Management", "Candidate Management"
     */
    String module() default "";

    /**
     * Operation type
     * <p>
     * Prefer the standard verbs: CREATE / UPDATE / DELETE / QUERY / UPLOAD / EXPORT / LOGIN / LOGOUT
     */
    String operation() default "";

    /**
     * Operation description (optional, a more specific business explanation)
     */
    String description() default "";

    /**
     * Whether to record the request parameters (default true)
     * <p>
     * For endpoints that carry sensitive information (such as login), setting this to false is recommended
     */
    boolean saveParams() default true;

    /**
     * Whether to record the response result (default false, to avoid oversized logs)
     */
    boolean saveResult() default false;
}
