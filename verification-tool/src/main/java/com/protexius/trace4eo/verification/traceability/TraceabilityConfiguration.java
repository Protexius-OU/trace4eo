package com.protexius.trace4eo.verification.traceability;

import com.protexius.trace4eo.provenance.ProvenanceJsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
public class TraceabilityConfiguration {

    @Bean
    public TracingClient tracingClient(HttpClient httpClient, ProvenanceJsonMapper provenanceJsonMapper) {
        return new TracingClient(provenanceJsonMapper, httpClient);
    }

    @Bean
    public TraceabilityService traceabilityService(
            TracingClient tracingClient
    ) {
        return new TraceabilityService(tracingClient);
    }
}

