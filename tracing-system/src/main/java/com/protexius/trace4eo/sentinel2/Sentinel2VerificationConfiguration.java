package com.protexius.trace4eo.sentinel2;

import com.protexius.trace4eo.provenance.ProvenanceJsonMapper;
import com.protexius.trace4eo.provenance.traceability.TraceabilityService;
import com.protexius.trace4eo.provenance.traceability.TracingClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
public class Sentinel2VerificationConfiguration {

    @Bean
    public HttpClient sentinel2TraceHttpClient() {
        return HttpClient.newHttpClient();
    }

    @Bean
    public TracingClient sentinel2TracingClient(HttpClient sentinel2TraceHttpClient, ProvenanceJsonMapper provenanceJsonMapper) {
        return new TracingClient(provenanceJsonMapper, sentinel2TraceHttpClient);
    }

    @Bean
    public TraceabilityService sentinel2TraceabilityService(TracingClient sentinel2TracingClient) {
        return new TraceabilityService(sentinel2TracingClient);
    }
}
