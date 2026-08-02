package com.smartwork;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaAuditing
// The weekly-report outbox relay (com.smartwork.report.infrastructure.ReportOutboxRelay)
// is the first @Scheduled job in the platform - see ADR-006 for the outbox design it
// implements.
@EnableScheduling
public class SmartWorkApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartWorkApplication.class, args);
    }
}
