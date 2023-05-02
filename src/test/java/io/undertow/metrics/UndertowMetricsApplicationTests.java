package io.undertow.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class UndertowMetricsApplicationTests implements CommandLineRunner {


    @Bean
    public MeterRegistry registry() {
        return new SimpleMeterRegistry();
    }

    public static void main(String[] args) {
        SpringApplication.run(UndertowMetricsApplicationTests.class, args);
    }


    @Override
    public void run(String... args) throws Exception {
        System.err.println("Spring Boot Application（Undertow-Metrics-Application） Started !");
    }

}