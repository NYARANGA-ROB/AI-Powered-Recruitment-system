package com.smartats;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SmartATS application entry point
 * <p>
 * @MapperScan has been moved to {@link com.smartats.config.MyBatisPlusConfig}
 * so that @WebMvcTest slice tests do not load Mapper beans.
 */
@SpringBootApplication(exclude = {
        org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration.class
})
@EnableScheduling
public class SmartAtsApplication {

    public static void main(String[] args) {
        /*
         * What does SpringApplication.run() do?
         * 1. Creates the Spring ApplicationContext (the container)
         * 2. Scans annotations such as @Component, @Service and @Controller
         * 3. Starts the embedded Tomcat server
         * 4. Registers all auto-configured beans
         */
        SpringApplication.run(SmartAtsApplication.class, args);

        System.out.println("""

                ╔════════════════════════════════════════╗
                ║ 🎉 SmartATS started successfully!      ║
                ║                                        ║
                ║  Address:    http://localhost:8080     ║
                ║  Database:   MySQL @ 3307              ║
                ║  Cache:      Redis @ 6379              ║
                ║  Message:    RabbitMQ @ 5672           ║
                ╚════════════════════════════════════════╝

                """);
    }
}
