package com.smartats.module.job.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("jobs")
public class Job {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String title;

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
     * Required skill tags (JSON format)
     */
    private String requiredSkills;

    /**
     * Minimum salary (K)
     */
    private Integer salaryMin;

    /**
     * Maximum salary (K)
     */
    private Integer salaryMax;

    /**
     * Minimum years of experience
     */
    private Integer experienceMin;

    /**
     * Maximum years of experience
     */
    private Integer experienceMax;

    /**
     * Education requirement
     */
    private String education;

    /**
     * Job type
     */
    private String jobType;

    /**
     * Status: DRAFT - draft, PUBLISHED - published, CLOSED - closed
     */
    private String status;

    /**
     * Creator ID
     */
    private Long creatorId;

    /**
     * View count
     */
    private Integer viewCount;

    /**
     * Logical delete flag
     */
    @TableLogic
    private Integer deleted;

    /**
     * Created at
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * Updated at
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}