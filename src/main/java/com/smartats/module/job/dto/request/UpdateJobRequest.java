package com.smartats.module.job.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Update job request
 */
@Data
public class UpdateJobRequest {

    /**
     * Job ID
     */
    @NotNull(message = "Job ID must not be empty")
    private Long id;

    /**
     * Job title
     */
    private String title;

    /**
     * Department
     */
    private String department;

    /**
     * Job description
     */
    private String description;

    /**
     * Job requirements
     */
    private String requirements;

    /**
     * Required skill tags
     */
    private java.util.List<String> requiredSkills;

    /**
     * Minimum salary (K)
     * If this field is provided, it must be >= 0
     */
    @Min(value = 0, message = "Minimum salary must not be less than 0")
    private Integer salaryMin;

    /**
     * Maximum salary (K)
     * If this field is provided, it must be >= 0
     */
    @Min(value = 0, message = "Maximum salary must not be less than 0")
    private Integer salaryMax;

    /**
     * Minimum years of experience
     */
    @Min(value = 0, message = "Years of experience must not be less than 0")
    private Integer experienceMin;

    /**
     * Maximum years of experience
     */
    @Min(value = 0, message = "Years of experience must not be less than 0")
    private Integer experienceMax;

    /**
     * Education requirement
     */
    private String education;

    /**
     * Job type
     */
    private String jobType;
}