package com.smartats.module.job.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Job response
 */
@Data
public class JobResponse {

    /**
     * Job ID
     */
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
    private List<String> requiredSkills;

    /**
     * Minimum salary (K)
     */
    private Integer salaryMin;

    /**
     * Maximum salary (K)
     */
    private Integer salaryMax;

    /**
     * Salary range (formatted for display)
     */
    private String salaryRange;

    /**
     * Minimum years of experience
     */
    private Integer experienceMin;

    /**
     * Maximum years of experience
     */
    private Integer experienceMax;

    /**
     * Experience requirement (formatted for display)
     */
    private String experienceRange;

    /**
     * Education requirement
     */
    private String education;

    /**
     * Job type
     */
    private String jobType;

    /**
     * Status
     */
    private String status;

    /**
     * Status description
     */
    private String statusDesc;

    /**
     * Creator ID
     */
    private Long creatorId;

    /**
     * View count
     */
    private Integer viewCount;

    /**
     * Created at
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * Updated at
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}