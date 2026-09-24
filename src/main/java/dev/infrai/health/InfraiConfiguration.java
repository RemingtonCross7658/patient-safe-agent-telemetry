package dev.infrai.health;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
class InfraiConfiguration {
    @Bean
    OpenAIClient openAIClient(@Value("${infrai.api-key}") String apiKey,
                              @Value("${infrai.base-url}") String baseUrl) {
        return OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    InfraiTelemetryClient infraiTelemetryClient(
            @Value("${infrai.api-key}") String apiKey,
            @Value("${infrai.base-url}") String baseUrl,
            ObjectMapper objectMapper) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        return new InfraiTelemetryClient(http, objectMapper, apiKey, baseUrl);
    }
}
