package com.smartats.module.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * Webhook configuration creation request
 */
@Data
public class WebhookCreateRequest {

    @NotBlank(message = "Webhook URL must not be blank")
    @Pattern(regexp = "^https?://[\\w\\-]+(\\.[\\w\\-]+)+([\\w\\-.,@?^=%&:/~+#]*[\\w\\-@?^=%&/~+#])?$", message = "Invalid Webhook URL format")
    private String url;

    @NotNull(message = "Event list must not be null")
    private List<String> events;

    private String description;
}
