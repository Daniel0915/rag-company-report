package com.ismsp.chatbot.gemini;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Google Gemini(generateContent) REST API를 직접 호출하는 클라이언트.
 * Spring AI의 Google GenAI 스타터는 spring-ai 1.1.0부터 존재해서, 지금 프로젝트가 쓰는
 * 1.0.0 BOM으로는 못 받는다. BOM을 올리면 Ollama/Neo4j 벡터스토어 쪽 API가 같이 흔들릴
 * 위험이 있어서, DartApiClient와 같은 방식으로 REST 호출만 직접 한다.
 */
@Component
public class GeminiApiClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public GeminiApiClient(
            RestClient.Builder restClientBuilder,
            @Value("${gemini.api.base-url}") String baseUrl,
            @Value("${gemini.api.key}") String apiKey,
            @Value("${gemini.api.model}") String model
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());

        this.restClient = restClientBuilder.baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    public String generate(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY가 설정되어 있지 않습니다");
        }

        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt))))
        );

        GenerateContentResponse response = restClient.post()
                .uri("/models/{model}:generateContent?key={key}", model, apiKey)
                .body(body)
                .retrieve()
                .body(GenerateContentResponse.class);

        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new IllegalStateException("Gemini 응답이 비어 있습니다");
        }
        return response.candidates().get(0).content().parts().get(0).text();
    }

    private record GenerateContentResponse(List<Candidate> candidates) {
    }

    private record Candidate(Content content) {
    }

    private record Content(List<Part> parts) {
    }

    private record Part(String text) {
    }
}
