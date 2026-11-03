package com.smartats.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus vector database configuration
 * <p>
 * Milvus Standalone is connected over gRPC, on the default port 19530.
 * Uses MilvusClientV2 (the SDK v2 API), which matches Milvus Server 2.4.x.
 */
@Slf4j
@Configuration
public class MilvusConfig {

    @Value("${milvus.host:localhost}")
    private String host;

    @Value("${milvus.port:19530}")
    private int port;

    @Value("${milvus.database:default}")
    private String database;

    @Value("${milvus.connect-timeout:10000}")
    private long connectTimeout;

    @Bean(destroyMethod = "close")
    public MilvusClientV2 milvusClient() {
        String uri = "http://" + host + ":" + port;
        log.info("Initialising the Milvus client: uri={}, database={}", uri, database);

        ConnectConfig config = ConnectConfig.builder()
                .uri(uri)
                .dbName(database)
                .connectTimeoutMs(connectTimeout)
                .build();

        MilvusClientV2 client = new MilvusClientV2(config);
        log.info("Milvus client initialised successfully");
        return client;
    }
}
