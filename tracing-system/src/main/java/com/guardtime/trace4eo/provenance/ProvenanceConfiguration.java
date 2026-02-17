package com.guardtime.trace4eo.provenance;

import com.guardtime.trace4eo.provenance.signing.ProvenanceSigningService;
import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProvenanceConfiguration {

    @Bean
    public ProvenanceJsonMapper provenanceJsonMapper() {
        return new ProvenanceJsonMapper();
    }

    @Bean
    public ProvenanceSigningService provenanceSigningService() {
        return new ProvenanceSigningService();
    }

    @Bean
    public ProvenanceVerificationService provenanceVerificationService() {
        return new ProvenanceVerificationService();
    }
}
