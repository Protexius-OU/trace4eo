package com.guardtime.trace4eo.verification;

import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import com.guardtime.trace4eo.provenance.verification.ProvenanceVerificationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VerificationToolConfiguration {

    @Bean
    public ProvenanceJsonMapper provenanceJsonMapper() {
        return new ProvenanceJsonMapper();
    }

    @Bean
    public ProvenanceVerificationService provenanceVerificationService() {
        return new ProvenanceVerificationService();
    }
}
