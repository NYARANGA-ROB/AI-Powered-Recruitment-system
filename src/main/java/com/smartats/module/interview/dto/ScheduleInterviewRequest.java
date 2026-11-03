package com.smartats.module.interview.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Interview scheduling request DTO
 */
@Data
public class ScheduleInterviewRequest {

    /**
     * Associated application ID
     */
    @NotNull(message = "Application ID must not be null")
    private Long applicationId;

    /**
     * Interviewer user ID
     */
    @NotNull(message = "Interviewer ID must not be null")
    private Long interviewerId;

    /**
     * Interview round (auto-incremented by default, can be specified manually)
     */
    @Min(value = 1, message = "Interview round must be at least 1")
    private Integer round;

    /**
     * Interview type
     */
    @NotNull(message = "Interview type must not be null")
    @Pattern(
            regexp = "PHONE|VIDEO|ONSITE|WRITTEN_TEST",
            message = "Invalid interview type; allowed values: PHONE, VIDEO, ONSITE, WRITTEN_TEST"
    )
    private String interviewType;

    /**
     * Scheduled interview time (must be in the future)
     */
    @NotNull(message = "Interview time must not be null")
    @Future(message = "Interview time must be in the future")
    private LocalDateTime scheduledAt;

    /**
     * Interview duration (minutes, defaults to 60)
     */
    @Min(value = 15, message = "Interview duration must be at least 15 minutes")
    private Integer durationMinutes = 60;
}
