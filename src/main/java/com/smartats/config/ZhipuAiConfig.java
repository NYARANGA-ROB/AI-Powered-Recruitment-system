package com.smartats.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Zhipu AI configuration (using the OpenAI-compatible mode)
 * <p>
 * Note: the Zhipu API path differs from OpenAI's:
 * - OpenAI default path: /v1/chat/completions
 * - Zhipu AI path: /chat/completions (the baseUrl already includes /v4)
 * <p>
 * Final request URL = baseUrl + completionsPath
 *   = https://open.bigmodel.cn/api/paas/v4/chat/completions
 * <p>
 * The embedding model is Zhipu embedding-3, with 1024 dimensions and a maximum of 8192 tokens.
 */
@Configuration
public class ZhipuAiConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://open.bigmodel.cn/api/paas/v4}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model:glm-4-flash-250414}")
    private String model;

    @Value("${spring.ai.openai.chat.options.temperature:0.3}")
    private Double temperature;

    @Value("${smartats.ai.embedding.model:embedding-3}")
    private String embeddingModel;

    /**
     * Create the shared OpenAiApi instance (Chat and Embedding reuse the same API client)
     * <p>
     * Set sensible timeouts: AI resume parsing usually takes 15-45 seconds,
     * and the default timeout is too short and causes "Request timed out" errors.
     */
    @Bean
    public OpenAiApi openAiApi() {
        // Configure HTTP timeouts: 30 seconds to connect, 120 seconds to read (AI generation takes a while)
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(30));
        requestFactory.setReadTimeout(Duration.ofSeconds(120));

        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory);

        return new OpenAiApi(
                baseUrl,
                apiKey,
                "/chat/completions",
                "/embeddings",
                restClientBuilder,
                WebClient.builder(),
                new DefaultResponseErrorHandler()
        );
    }

    @Bean
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi) {
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(model)
                .withTemperature(temperature)
                .withMaxTokens(4000)
                .build();

        // Disable Spring AI's internal retries (by default maxAttempts=10, exponentialBackoff 2s~180s)
        // Instead we implement a more sensible exponential backoff at the MQ consumer layer via the delay queue (10s/30s/60s)
        RetryTemplate noRetry = RetryTemplate.builder()
                .maxAttempts(1)
                .build();

        return new OpenAiChatModel(openAiApi, options, null, noRetry);
    }

    /**
     * Zhipu embedding model (embedding-3)
     * <p>
     * Output dimensions: 1024
     * Maximum input tokens: 8192
     * Used to vectorise candidate resume text and enable semantic search
     */
    @Bean
    public OpenAiEmbeddingModel openAiEmbeddingModel(OpenAiApi openAiApi) {
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .withModel(embeddingModel)
                .withDimensions(1024)  // embedding-3 defaults to 2048 dimensions; 1024 keeps it aligned with the Milvus schema
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);
    }
}