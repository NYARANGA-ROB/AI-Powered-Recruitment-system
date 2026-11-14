package com.smartaps;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SmartAPS application entry point
 * <p>
 * @MapperScan has been moved to {@link com.smartaps.config.MyBatisPlusConfig}
 * so that @WebMvcTest slice tests do not load Mapper beans.
 */
@SpringBootApplication(exclude = {
        org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration.class
})
@EnableScheduling
public class SmartApsApplication {

    public static void main(String[] args) {
        /*
         * What does SpringApplication.run() do?
         * 1. Creates the Spring ApplicationContext (the container)
         * 2. Scans annotations such as @Component, @Service and @Controller
         * 3. Starts the embedded Tomcat server
         * 4. Registers all auto-configured beans
         */
        SpringApplication.run(SmartApsApplication.class, args);

        System.out.println("""

                ╔════════════════════════════════════════╗
                ║ 🎉 SmartAPS started successfully!      ║
                ║                                        ║
                ║  Address:    http://localhost:8080     ║
                ║  Database:   MySQL @ 3307              ║
                ║  Cache:      Redis @ 6379              ║
                ║  Message:    RabbitMQ @ 5672           ║
                ╚════════════════════════════════════════╝

                """);
    }
}
