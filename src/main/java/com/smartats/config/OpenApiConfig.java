package com.smartats.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger documentation configuration
 * <p>
 * Endpoints:
 * <ul>
 *   <li>Swagger UI: http://localhost:8080/api/v1/swagger-ui.html</li>
 *   <li>OpenAPI JSON: http://localhost:8080/api/v1/v3/api-docs</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "Bearer Token";

    @Bean
    public OpenAPI smartAtsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartATS Intelligent Recruitment Management API")
                        .description("""
                                SmartATS is an AI-powered recruitment management system for HR teams, offering resume
                                upload and AI parsing, job management, candidate management, application pipeline
                                management, interview scheduling and feedback, and Webhook event notifications.
                                
                                ## Authentication
                                - The login endpoint returns an `accessToken` with a 2-hour validity
                                - Authenticate by adding `Authorization: Bearer {token}` to the request headers
                                - Once the token expires, refresh it using the `refreshToken`
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("SmartATS Team")
                                .email("support@smartats.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter the JWT token (without the Bearer prefix")));
    }
}
