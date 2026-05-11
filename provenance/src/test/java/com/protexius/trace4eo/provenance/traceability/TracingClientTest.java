package com.protexius.trace4eo.provenance.traceability;

import com.protexius.trace4eo.provenance.ProvenanceJsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class TracingClientTest {

    private final JsonMapper jsonMapper = new ProvenanceJsonMapper();
    private HttpClient httpClient;
    private HttpResponse<InputStream> httpResponse;
    private TracingClient tracingClient;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        httpResponse = mock(HttpResponse.class);
        tracingClient = new TracingClient(jsonMapper, httpClient);
    }

    @Test
    void getProductCreateEventTrace_realCopernicusResponse_parsesAndAssertsUri() throws Exception {
        String productId = "S2A_MSIL1C_20230420T100021_N0509_R122_T33UVP_20230420T120027";
        String productName = productId + ".SAFE.zip";
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(Files.newInputStream(
            Path.of("src/test/resources/sentinel2-trace-create-event.json")));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(httpResponse);

        TraceResponse.Trace trace = tracingClient.getProductCreateEventTrace(productId).orElseThrow();

        assertEquals("7374c9be-84c5-4ea4-8b2b-b1e9b1a539d7", trace.id());
        assertEquals("CREATE", trace.event());
        assertEquals("BLAKE3", trace.hashAlgorithm());
        assertEquals("RSA-SHA256", trace.signature().algorithm());
        assertEquals(productName, trace.product().name());
        // 46 file entries in the signed message of this real CDSE trace.
        assertEquals(46, trace.product().contents().size());

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals(
            "https://trace.dataspace.copernicus.eu/api/v1/traces/name/" + productName,
            captor.getValue().uri().toString()
        );
        assertEquals("application/json", captor.getValue().headers().firstValue("Accept").orElse(null));
    }

    @Test
    void getProductCreateEventTrace_noCreateEvent_returnsEmpty() throws Exception {
        String body = jsonMapper.writeValueAsString(List.of(
            traceJson("trace-copied", "COPIED", "msg"),
            traceJson("trace-deleted", "DELETED", "msg")
        ));
        respondWith(200, body);

        assertTrue(tracingClient.getProductCreateEventTrace("S2A_NO_CREATE").isEmpty());
    }

    @Test
    void getProductCreateEventTrace_signedMessageNameMismatch_throws() throws Exception {
        String productId = "S2A_EXPECTED";
        String productName = productId + ".SAFE.zip";
        String mismatchedName = "S2A_OTHER.SAFE.zip";
        String body = jsonMapper.writeValueAsString(List.of(
            traceJson("trace-create", "CREATE", signedMessage(mismatchedName, "CREATE"))
        ));
        respondWith(200, body);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> tracingClient.getProductCreateEventTrace(productId));
        assertTrue(ex.getMessage().contains(productName), () -> "got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains(mismatchedName), () -> "got: " + ex.getMessage());
    }

    @Test
    void getProductCreateEventTrace_signedMessageEventMismatch_throws() throws Exception {
        String productId = "S2A_EVENT_MISMATCH";
        String productName = productId + ".SAFE.zip";
        // Outer event says CREATE so the trace gets parsed, but the signed message event differs.
        String body = jsonMapper.writeValueAsString(List.of(
            traceJson("trace-create", "CREATE", signedMessage(productName, "COPIED"))
        ));
        respondWith(200, body);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> tracingClient.getProductCreateEventTrace(productId));
        assertTrue(ex.getMessage().contains("COPIED"), () -> "got: " + ex.getMessage());
    }

    @Test
    void getProductCreateEventTrace_non200Status_throws() throws Exception {
        respondWith(503, "Service Unavailable");

        IOException ex = assertThrows(IOException.class,
            () -> tracingClient.getProductCreateEventTrace("S2A_DOWN"));
        assertTrue(ex.getMessage().contains("503"), () -> "got: " + ex.getMessage());
    }

    private void respondWith(int status, String body) throws IOException, InterruptedException {
        when(httpResponse.statusCode()).thenReturn(status);
        when(httpResponse.body())
            .thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(httpResponse);
    }

    private Map<String, Object> traceJson(String id, String event, String message) {
        return Map.of(
            "id", id,
            "event", event,
            "hash_algorithm", "BLAKE3",
            "signature", Map.of(
                "signature", "AAA=",
                "algorithm", "RSA-SHA256",
                "certificate", "BBB=",
                "message", message
            )
        );
    }

    private String signedMessage(String productName, String event) {
        return jsonMapper.writeValueAsString(Map.of(
            "name", productName,
            "event", event,
            "contents", List.of(Map.of(
                "path", "manifest.safe",
                "hash", "deadbeef"
            ))
        ));
    }
}
