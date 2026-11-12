package com.imaia.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Shared plumbing for the JDK-based AI endpoint clients: one client, one header set
 * (explicit User-Agent — WAF edge challenges target header-less Java clients), and a
 * status classification where 403 challenge pages surface as transient failures.
 */
abstract class AbstractAiHttpClient {

    static final String USER_AGENT = "imaia/0.1 (AI endpoint client)";

    final Logger log = LoggerFactory.getLogger(getClass());
    final JsonMapper mapper = JsonMapper.builder().build();

    /**
     * A fresh client per call. Long-lived pooled connections to the AI edge get
     * flagged by its WAF after a while (every request on them answers 403 while
     * brand-new connections pass), so pooling is deliberately avoided; call
     * frequency is capped by the rate limiter anyway.
     */
    HttpClient client() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    HttpRequest.Builder baseRequest(URI uri, String apiKey, Duration timeout) {
        return HttpRequest.newBuilder()
                .uri(uri)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("User-Agent", USER_AGENT);
    }

    JsonNode sendJson(HttpRequest request) {
        try {
            HttpResponse<byte[]> response = client().send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 403) {
                throw new TransientGatewayException(
                        "Endpoint edge returned HTTP 403 (challenge page); will keep retrying with backoff.");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Endpoint returned HTTP " + response.statusCode()
                        + ": " + new String(response.body(), java.nio.charset.StandardCharsets.UTF_8));
            }
            return mapper.readTree(response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Endpoint call interrupted.", interrupted);
        } catch (IOException failure) {
            throw new IllegalStateException("Endpoint call failed: " + failure.getMessage(), failure);
        }
    }

    byte[] sendBytes(HttpRequest request) {
        try {
            HttpResponse<byte[]> response = client().send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 403) {
                throw new TransientGatewayException(
                        "Endpoint edge returned HTTP 403 (challenge page); will keep retrying with backoff.");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Endpoint returned HTTP " + response.statusCode() + ".");
            }
            return response.body();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Endpoint download interrupted.", interrupted);
        } catch (IOException failure) {
            throw new IllegalStateException("Endpoint download failed: " + failure.getMessage(), failure);
        }
    }
}
