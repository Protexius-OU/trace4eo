package com.guardtime.trace4eo;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Trace4EoApp {

    protected Trace4EoApp() {
        // for checkstyle
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Trace4EoApp.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.run(args);
    }
}
