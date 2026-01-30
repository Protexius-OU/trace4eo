package com.guardtime.trace4eo.signing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

import java.nio.file.Path;

@Configuration
public class PathConverter {

    @Bean
    public ConfigurableConversionService conversionService() {
        DefaultConversionService service = new DefaultConversionService();
        service.addConverter(String.class, Path.class, Path::of);
        return service;
    }
}
