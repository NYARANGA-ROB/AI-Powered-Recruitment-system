package com.smartats.module.interview.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Interview record entity
 * <p>
 * Maps to the interview_records database table; many-to-one with job applications (job_applications) —
 * a single application can have multiple interview rounds.
 */
@Data
@TableName("interview_records")
public class InterviewRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * Associated application ID (job_applications.id)
     */
    private Long applicationId;

    /**
     * Interviewer user ID (users.id)
     */
    private Long interviewerId;

    /**
     * Interview round (1 = first round, 2 = second round, ...)
     */
    private Integer round;

    /**
     * Interview type: PHONE / VIDEO / ONSITE / WRITTEN_TEST
     */
    private String interviewType;

    /**
     * Scheduled interview time
     */
    private LocalDateTime scheduledAt;

    /**
     * Interview duration (minutes)
     */
    private Integer durationMinutes;

    /**
     * Interview status: SCHEDULED / COMPLETED / CANCELLED / NO_SHOW
     */
    private String status;

    /**
     * Interview feedback comments
     */
    private String feedback;

    /**
     * Interview score (1-10)
     */
    private Integer score;

    /**
     * Interview recommendation: STRONG_YES / YES / NEUTRAL / NO / STRONG_NO
     */
    private String recommendation;

    /**
     * Creation time
     */
    private LocalDateTime createdAt;

    /**
     * Update time
     */
    private LocalDateTime updatedAt;
}
