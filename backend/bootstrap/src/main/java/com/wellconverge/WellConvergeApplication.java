package com.wellconverge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Composition root. Scans the whole com.wellconverge tree for adapters and configuration, and points
 * JPA at the adapter packages where entities/repositories live.
 */
@SpringBootApplication(scanBasePackages = "com.wellconverge")
@EntityScan(basePackages = "com.wellconverge.membership.adapters.persistence")
@EnableJpaRepositories(basePackages = "com.wellconverge.membership.adapters.persistence")
public class WellConvergeApplication {

    public static void main(String[] args) {
        SpringApplication.run(WellConvergeApplication.class, args);
    }
}
