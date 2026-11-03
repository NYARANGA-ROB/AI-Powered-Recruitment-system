package com.smartats.module.job.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateJobRequest {
    /**
     * Job title
     */
    @NotBlank(message = "Job title must not be empty")
    private String title;

    /**
     * Department
     */
    private String department;

    /**
     * Job description
     */
    @NotBlank(message = "Job description must not be empty")
    private String description;

    /**
     * Job requirements
     */
    @NotBlank(message = "Job requirements must not be empty")
    private String requirements;

    /**
     * Required skill tags
     */
    private List<String> requiredSkills;

    /**
     * Minimum salary (K)
     */
    @NotNull(message = "Minimum salary must not be empty")
    @Min(value = 0, message = "Minimum salary must not be less than 0")
    private Integer salaryMin;

    /**
     * Maximum salary (K)
     */
    @NotNull(message = "Maximum salary must not be empty")
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
