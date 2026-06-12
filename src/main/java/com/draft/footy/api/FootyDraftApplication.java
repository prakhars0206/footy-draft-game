package com.draft.footy.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.draft.footy")
public class FootyDraftApplication {
    public static void main(String[] args) {
        SpringApplication.run(FootyDraftApplication.class, args);
    }
}
