package dev.infrai.health;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

final class InfraiTelemetryClient {
    private static final int MAX_ATTEMPTS = 3;
    private final HttpClient http;
    private final ObjectMapper json;
    private final String apiKey;
    private final String baseUrl;

    InfraiTelemetryClient(HttpClient http, ObjectMapper json, String apiKey, String baseUrl) {
        this.http = http;
        this.json = json;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    void reportTokenUsage(String appointmentId, long tokenCount) {
        post("/metrics/report", Map.of(
                "type", "counter",
                "name", "health.agent.tokens",
                "value", tokenCount,
                "tags", Map.of("appointment_id", appointmentId)),
                appointmentId + ":tokens");
    }

    void capture(String appointmentId, RuntimeException failure) {
        post("/errors/capture", Map.of(
                "message", failure.getMessage(),
                "level", "error",
                "fingerprint", "appointment-notification-policy",
                "exception", Map.of("type", failure.getClass().getSimpleName(), "message", failure.getMessage()),
                "context", Map.of("appointment_id", appointmentId)),
                appointmentId + ":policy-error");
    }

    private JsonNode post(String path, Object payload, String idempotencyKey) {
        String body;
        try {
            body = json.writeValueAsString(payload);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot encode telemetry payload", e);
        }

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .method("POST", HttpRequest.BodyPublishers.ofString(body))
                    .build();
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode envelope = json.readTree(response.body());
                if (!envelope.path("ok").asBoolean(false)) {
                    if (response.statusCode() == 429 && attempt < MAX_ATTEMPTS - 1) {
                        pause(retryDelay(response, attempt));
                        continue;
                    }
                    JsonNode error = envelope.path("error");
                    throw new InfraiRejectedException(error.path("code").asText("REJECTED"),
                            error.path("message").asText("Request rejected"), response.statusCode());
                }
                if (response.statusCode() == 429) {
                    pause(retryDelay(response, attempt));
                    continue;
                }
                if (response.statusCode() >= 500) {
                    throw new IllegalStateException("Telemetry transport status " + response.statusCode());
                }
                return envelope.path("data");
            } catch (InfraiRejectedException e) {
                throw e;
            } catch (IOException e) {
                throw new IllegalStateException("Telemetry response could not be decoded", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Telemetry request interrupted", e);
            }
        }
        throw new IllegalStateException("Telemetry retry budget exhausted");
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> Duration.ofSeconds(Long.parseLong(value)))
                .orElse(Duration.ofSeconds(1L << attempt));
    }

    private static void pause(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", e);
        }
    }

    static final class InfraiRejectedException extends RuntimeException {
        private final String code;
        private final int statusCode;

        InfraiRejectedException(String code, String message, int statusCode) {
            super(message);
            this.code = code;
            this.statusCode = statusCode;
        }

        String code() { return code; }
        int statusCode() { return statusCode; }
    }
}
