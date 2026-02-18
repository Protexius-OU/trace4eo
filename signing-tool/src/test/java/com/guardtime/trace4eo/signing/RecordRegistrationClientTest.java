package com.guardtime.trace4eo.signing;

import com.guardtime.trace4eo.provenance.ProvenanceJsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecordRegistrationClientTest {

    private HttpClient mockHttpClient;
    private RecordRegistrationClient client;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        mockHttpClient = mock(HttpClient.class);
        client = new RecordRegistrationClient(mockHttpClient, new ProvenanceJsonMapper());
    }

    @Test
    @SuppressWarnings("unchecked")
    void findMissingPredecessors_returnsEmptyListWhenAllExist() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("[]");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        List<UUID> result = client.findMissingPredecessors(
            List.of(UUID.randomUUID()), "http://localhost:8080/api/provenance", "token");

        assertTrue(result.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void findMissingPredecessors_returnsMissingIds() throws Exception {
        UUID missingId = UUID.randomUUID();
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("[\"" + missingId + "\"]");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        List<UUID> result = client.findMissingPredecessors(
            List.of(missingId), "http://localhost:8080/api/provenance", "token");

        assertEquals(1, result.size());
        assertEquals(missingId, result.getFirst());
    }

    @Test
    @SuppressWarnings("unchecked")
    void findMissingPredecessors_throwsOnNon200Response() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(500);
        when(mockResponse.body()).thenReturn("Internal Server Error");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        RegistrationException exception = assertThrows(RegistrationException.class, () ->
            client.findMissingPredecessors(
                List.of(UUID.randomUUID()), "http://localhost:8080/api/provenance", "token"));

        assertTrue(exception.getMessage().contains("500"));
    }
}
