package com.smartats.module.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Update application status request DTO
 */
@Data
public class UpdateApplicationStatusRequest {

    /**
     * Target status
     * <p>
     * Valid values: SCREENING / INTERVIEW / OFFER / REJECTED / WITHDRAWN
     */
    @NotBlank(message = "Target status must not be empty")
    @Pattern(
            regexp = "SCREENING|INTERVIEW|OFFER|REJECTED|WITHDRAWN",
            message = "Invalid status value; allowed values: SCREENING, INTERVIEW, OFFER, REJECTED, WITHDRAWN"
    )
    private String status;

    /**
     * HR notes (optional, used to record the reason for the status change)
     */
    private String hrNotes;
}