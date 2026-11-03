package com.smartats.module.interview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Interview feedback submission request DTO
 */
@Data
public class SubmitFeedbackRequest {

    /**
     * Interview score (1-10)
     */
    @Min(value = 1, message = "Score must be at least 1")
    @Max(value = 10, message = "Score must be at most 10")
    private Integer score;

    /**
     * Interview feedback comments
     */
    @NotBlank(message = "Interview feedback must not be blank")
    private String feedback;

    /**
     * Recommendation
     */
    @NotBlank(message = "Recommendation must not be blank")
    @Pattern(
            regexp = "STRONG_YES|YES|NEUTRAL|NO|STRONG_NO",
            message = "Invalid recommendation; allowed values: STRONG_YES, YES, NEUTRAL, NO, STRONG_NO"
    )
    private String recommendation;
}
