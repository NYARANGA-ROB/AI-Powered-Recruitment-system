package com.smartats.module.job.dto.request;

import lombok.Data;

@Data
public class JobQueryRequest {
    /**
     * Keyword search (title, description, requirements)
     */
    private String keyword;

    /**
     * Department
     */
    private String department;

    /**
     * Job type
     */
    private String jobType;

    /**
     * Education requirement
     */
    private String education;

    /**
     * Minimum experience requirement
     */
    private Integer experienceMin;

    /**
     * Minimum salary
     */
    private Integer salaryMin;

    /**
     * Job status
     */
    private String status;

    /**
     * Page number (starts from 1)
     */
    private Integer pageNum = 1;

    /**
     * Page size
     */
    private Integer pageSize = 10;

    /**
     * Sort field: created_at, salary_max, view_count
     */
    private String orderBy = "created_at";

    /**
     * Sort direction: asc, desc
     */
    private String orderDirection = "desc";
}
