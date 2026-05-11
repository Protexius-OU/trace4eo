package com.protexius.trace4eo.provenance.traceability;

import com.protexius.trace4eo.provenance.ProvenanceJsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live test that calls the real Copernicus Traceability API. Disabled by default so the regular
 * test suite stays offline. Enable by exporting {@code CDSE_E2E=1}:
 *
 * <pre>{@code
 *   CDSE_E2E=1 ./gradlew :provenance:test \
 *       --tests com.protexius.trace4eo.provenance.traceability.Sentinel2VerifyTraceLiveTest \
 *       --rerun
 * }</pre>
 */
@EnabledIfEnvironmentVariable(named = "CDSE_E2E", matches = "1")
class Sentinel2VerifyTraceLiveTest {

    @Test
    void verifyTrace_realKnownProduct_returnsOk() throws Exception {
        TracingClient tracingClient = new TracingClient(
            new ProvenanceJsonMapper(),
            HttpClient.newHttpClient()
        );
        TraceabilityService service = new TraceabilityService(tracingClient);

        // A known Sentinel-2 product whose CREATE trace was already published by CDSE. Replace
        // with any real product name to test that one instead.
        String imageId = "S2A_MSIL1C_20230420T100021_N0509_R122_T33UVP_20230420T120027";

        TraceVerificationResult result = service.verifyTrace(imageId);

        assertEquals(TraceVerificationResult.Status.OK, result.status());
        assertTrue(result.trace().isPresent());
        assertEquals("BLAKE3", result.trace().get().hashAlgorithm());
        assertEquals("RSA-SHA256", result.trace().get().signature().algorithm());
        assertEquals(imageId + ".SAFE.zip", result.trace().get().product().name());
    }
}
