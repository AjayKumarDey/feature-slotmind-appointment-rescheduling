package com.slotmind.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Component
public class HttpLlmGateway implements LlmGateway {
    private final ObjectMapper mapper;
    private final String provider, url, model, apiKey;
    private final Duration timeout;
    private final HttpClient client;

    public HttpLlmGateway(ObjectMapper mapper, @Value("${llm.provider:disabled}") String provider,
                          @Value("${llm.url:http://localhost:11434/api/chat}") String url,
                          @Value("${llm.model:llama3.2}") String model,
                          @Value("${llm.api-key:}") String apiKey, @Value("${llm.timeout-seconds:15}") long seconds) {
        this.mapper = mapper;
        this.provider = provider;
        this.url = url;
        this.model = model;
        this.apiKey = apiKey;
        timeout = Duration.ofSeconds(seconds);
        client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public String callLLM(String prompt) throws Exception {
        if (provider.equals("disabled")) throw new IllegalStateException("LLM is not configured");
        var body = new HashMap<String, Object>();
        body.put("model", model);
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        body.put("stream", false);
        if (provider.equals("ollama")) {
            body.put("format", "json");
            body.put("options", Map.of("temperature", 0));
        } else if (provider.equals("openai-compatible")) {
            if (apiKey.isBlank()) throw new IllegalStateException("LLM_API_KEY is required");
            body.put("temperature", 0);
            body.put("response_format", Map.of("type", "json_object"));
        } else throw new IllegalStateException("Unknown LLM_PROVIDER");
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .header("Content-Type", "application/json");
        if (provider.equals("openai-compatible")) request.header("Authorization", "Bearer " + apiKey);
        var response = client.send(request.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("LLM HTTP " + response.statusCode());
        var root = mapper.readTree(response.body());
        String content = provider.equals("ollama") ? root.path("message").path("content").asText()
                : root.path("choices").path(0).path("message").path("content").asText();
        if (content.isBlank()) throw new IllegalStateException("Empty LLM response");
        return content;
    }
}
