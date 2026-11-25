package com.fieldward;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FieldwardApplication {
    public static void main(String[] args) {
        SpringApplication.run(FieldwardApplication.class, args);
    }
}
