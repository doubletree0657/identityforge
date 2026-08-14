package io.github.doubletree.iam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IdentityForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityForgeApplication.class, args);
    }

}
