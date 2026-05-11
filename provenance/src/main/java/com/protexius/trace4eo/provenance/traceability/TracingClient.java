package com.protexius.trace4eo.provenance.traceability;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static com.protexius.trace4eo.provenance.traceability.TraceResponse.Product;
import static com.protexius.trace4eo.provenance.traceability.TraceResponse.ProductDto;
import static com.protexius.trace4eo.provenance.traceability.TraceResponse.Trace;
import static com.protexius.trace4eo.provenance.traceability.TraceResponse.TraceDto;
import static com.protexius.trace4eo.provenance.traceability.TraceResponse.TracingSignature;

public class TracingClient {
    private static final String CREATE_EVENT = "CREATE";

    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;
    private final URI apiBaseUrl;

    public TracingClient(JsonMapper jsonMapper, HttpClient httpClient) {
        this(jsonMapper, httpClient, URI.create("https://trace.dataspace.copernicus.eu"));
    }

    public TracingClient(JsonMapper jsonMapper, HttpClient httpClient, URI apiBaseUrl) {
        this.jsonMapper = jsonMapper;
        this.httpClient = httpClient;
        this.apiBaseUrl = apiBaseUrl;
    }

    public Optional<Trace> getProductCreateEventTrace(String productId) throws Exception {
        String productName = productId + ".SAFE.zip";
        URI uri = apiBaseUrl
                .resolve("/api/v1/traces/name/") // slash at end is important here
                .resolve(productName);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        int code = response.statusCode();
        if (code != 200) {
            // TODO - log response body
            throw new IOException("HTTP " + code);
        }


        JsonNode root = jsonMapper.readTree(response.body());

        TraceDto traceDto = null;
        for (JsonNode trace : root) {
            if (CREATE_EVENT.equals(trace.get("event").stringValue())) {
                traceDto = jsonMapper.convertValue(trace, TraceDto.class);
                break;
            }
        }
        if (traceDto == null) {
            return Optional.empty();
        }

        Trace trace = parseTraceDto(traceDto);
        // sanity check
        if (!productName.equals(trace.product().name())) {
            throw new IllegalStateException(
                    "Expected product name " + productName + " in signed message."
                    + "Actual product name in signed message: " + trace.product().name()
            );
        }
        return Optional.of(trace);
    }

    private Trace parseTraceDto(TraceDto traceDto) {
        TracingSignature signature = traceDto.signature();
        // Product contents are listed twice:
        //  - in trace object at ./product/contents
        //  - in trace object at ./signature/message
        // Use product contents from signed message and ignore the other values.
        ProductDto product = jsonMapper.convertValue(jsonMapper.readTree(signature.message()), ProductDto.class);
        // sanity check
        if (!CREATE_EVENT.equals(product.event())) {
            throw new IllegalStateException("Expected 'CREATE' event, actual event in signed message: " + product.event());
        }
        return new Trace(
                traceDto.id(),
                product.event(),
                traceDto.hashAlgorithm(),
                new Product(product.name(), product.hash(), product.contents()),
                signature
        );
    }
}
