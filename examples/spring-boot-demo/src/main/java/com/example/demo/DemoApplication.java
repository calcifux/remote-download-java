package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application that demonstrates how to consume
 * {@code remote-download-java} from JitPack and proxy a remote file to the
 * end-user browser without persisting it to disk.
 *
 * <p>Run it with:
 *
 * <pre>{@code
 * mvn spring-boot:run
 * }</pre>
 *
 * <p>Then open in a browser:
 *
 * <pre>
 *   http://localhost:8080/download
 *   http://localhost:8080/preview
 *   http://localhost:8080/health
 * </pre>
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
