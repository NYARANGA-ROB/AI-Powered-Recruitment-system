package com.smartats.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus configuration
 * <p>
 * @MapperScan lives here rather than in the application class so that @WebMvcTest slice tests do not load Mapper beans.
 * The WebMvcTypeExcludeFilter used by @WebMvcTest excludes @Configuration classes that are not Web related.
 */
@Configuration
@MapperScan("com.smartats.module.*.mapper")
public class MyBatisPlusConfig {
}
