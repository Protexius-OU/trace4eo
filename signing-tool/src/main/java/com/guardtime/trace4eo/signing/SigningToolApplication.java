package com.guardtime.trace4eo.signing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class SigningToolApplication {

    public static void main(String[] args) {
        SpringApplication.run(SigningToolApplication.class, args);
    }
}
