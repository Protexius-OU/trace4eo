package com.protexius.trace4eo.verification.traceability;

import com.protexius.trace4eo.provenance.ProvenanceJsonMapper;
import com.protexius.trace4eo.provenance.traceability.TraceabilityService;
import com.protexius.trace4eo.provenance.traceability.TracingClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
public class TraceabilityConfiguration {

    @Bean
    public HttpClient traceabilityHttpClient() {
        return HttpClient.newHttpClient();
    }

    @Bean
    public TracingClient tracingClient(HttpClient traceabilityHttpClient, ProvenanceJsonMapper provenanceJsonMapper) {
        return new TracingClient(provenanceJsonMapper, traceabilityHttpClient);
    }

    @Bean
    public TraceabilityService traceabilityService(
            TracingClient tracingClient
    ) {
        return new TraceabilityService(tracingClient);
    }
}
