package com.draft.footy.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// Engine ("com.draft.footy") stays framework-free; entities + repositories live in the persistence package,
// which is a sibling of this app package — so point component, entity, and repository scanning at the root.
@SpringBootApplication(scanBasePackages = "com.draft.footy")
@EntityScan("com.draft.footy.persistence")
@EnableJpaRepositories("com.draft.footy.persistence")
public class FootyDraftApplication {
    public static void main(String[] args) {
        SpringApplication.run(FootyDraftApplication.class, args);
    }
}
