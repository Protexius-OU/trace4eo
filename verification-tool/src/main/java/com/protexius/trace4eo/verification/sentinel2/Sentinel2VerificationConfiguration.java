package com.protexius.trace4eo.verification.sentinel2;

import com.protexius.trace4eo.provenance.ProvenanceJsonMapper;
import com.protexius.trace4eo.provenance.sentinel2.Sentinel2TraceabilityService;
import com.protexius.trace4eo.provenance.sentinel2.Sentinel2TracingClient;
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
    public Sentinel2TracingClient sentinel2TracingClient(
        HttpClient sentinel2TraceHttpClient, ProvenanceJsonMapper provenanceJsonMapper
    ) {
        return new Sentinel2TracingClient(provenanceJsonMapper, sentinel2TraceHttpClient);
    }

    @Bean
    public Sentinel2TraceabilityService sentinel2TraceabilityService(Sentinel2TracingClient sentinel2TracingClient) {
        return new Sentinel2TraceabilityService(sentinel2TracingClient);
    }
}
