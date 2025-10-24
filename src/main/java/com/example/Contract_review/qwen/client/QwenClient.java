package com.example.Contract_review.qwen.client;

import com.example.Contract_review.qwen.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 通义千问 API 客户端
 * 支持非流式和流式两种调用方式
 */
@Slf4j
@Component
public class QwenClient {

    @Value("${qwen.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${qwen.api-key:}")
    private String apiKey;

    @Value("${qwen.model:qwen-max}")
    private String defaultModel;

    @Value("${qwen.timeout:30}")
    private int timeoutSeconds;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    // 重试配置（默认关闭）
    private static final boolean ENABLE_RETRY = false;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    public QwenClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * 非流式聊天（支持 List<ChatMessage> 参数）
     */
    public Mono<ChatResponse> chat(java.util.List<ChatMessage> messages, String model) {
        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .model(model)
                .stream(false)
                .build();
        return chat(request);
    }

    /**
     * 非流式聊天
     */
    public Mono<ChatResponse> chat(ChatRequest request) {
        if (!request.validate()) {
            return Mono.error(new IllegalArgumentException("Invalid chat request: messages or model is empty"));
        }

        if (apiKey == null || apiKey.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("API key not configured. Set DASHSCOPE_API_KEY environment variable"));
        }

        request.setModel(request.getModel() != null ? request.getModel() : defaultModel);
        request.setStream(false);

        // 增加 50% 的超时缓冲以防止 flatMap 内部操作超时
        long totalTimeoutSeconds = Math.round(timeoutSeconds * 1.5);

        Mono<ChatResponse> responseMono = webClient.post()
                .uri(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .flatMap(this::parseNonStreamResponse)
                .timeout(Duration.ofSeconds(totalTimeoutSeconds))  // 为 flatMap 添加额外超时
                .doOnError(e -> logError(e, "Non-stream chat failed"));

        if (ENABLE_RETRY) {
            return responseMono.retry(MAX_RETRIES);
        }
        return responseMono;
    }

    /**
     * 流式聊天
     */
    public Flux<ChatDelta> streamChat(ChatRequest request) {
        if (!request.validate()) {
            return Flux.error(new IllegalArgumentException("Invalid chat request: messages or model is empty"));
        }

        if (apiKey == null || apiKey.trim().isEmpty()) {
            return Flux.error(new IllegalArgumentException("API key not configured. Set DASHSCOPE_API_KEY environment variable"));
        }

        request.setModel(request.getModel() != null ? request.getModel() : defaultModel);
        request.setStream(true);

        return webClient.post()
                .uri(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .doOnNext(line -> log.debug("SSE line: {}", line))
                .flatMap(this::parseStreamLine)
                .doOnError(e -> logError(e, "Stream chat failed"));
    }

    /**
     * 解析非流式响应
     */
    private Mono<ChatResponse> parseNonStreamResponse(String responseBody) {
        try {
            log.debug("Response body: {}", responseBody);
            Map<String, Object> map = objectMapper.readValue(responseBody, Map.class);

            // OpenAI 兼容格式
            if (map.containsKey("choices")) {
                java.util.List<Map<String, Object>> choices =
                        (java.util.List<Map<String, Object>>) map.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    String content = (String) message.get("content");

                    ChatResponse response = ChatResponse.builder()
                            .content(content)
                            .finishReason((String) choice.get("finish_reason"))
                            .model((String) map.get("model"))
                            .id((String) map.get("id"))
                            .build();

                    return Mono.just(response);
                }
            }

            return Mono.error(new RuntimeException("Invalid response format: " + responseBody));
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Failed to parse response: " + e.getMessage(), e));
        }
    }

    /**
     * 解析流式响应行
     */
    private Flux<ChatDelta> parseStreamLine(String line) {
        if (line == null || line.isEmpty() || line.equals("[DONE]")) {
            if (line != null && line.equals("[DONE]")) {
                return Flux.just(ChatDelta.builder()
                        .done(true)
                        .delta("")
                        .finishReason("stop")
                        .build());
            }
            return Flux.empty();
        }

        // 处理 SSE 格式：data: {...}
        if (line.startsWith("data: ")) {
            String jsonStr = line.substring(6).trim();
            if (jsonStr.equals("[DONE]")) {
                return Flux.just(ChatDelta.builder()
                        .done(true)
                        .delta("")
                        .finishReason("stop")
                        .build());
            }

            try {
                Map<String, Object> map = objectMapper.readValue(jsonStr, Map.class);

                if (map.containsKey("choices")) {
                    java.util.List<Map<String, Object>> choices =
                            (java.util.List<Map<String, Object>>) map.get("choices");
                    if (!choices.isEmpty()) {
                        Map<String, Object> choice = choices.get(0);
                        Map<String, Object> delta = (Map<String, Object>) choice.get("delta");

                        String content = delta != null ? (String) delta.get("content") : "";
                        String finishReason = (String) choice.get("finish_reason");

                        return Flux.just(ChatDelta.builder()
                                .delta(content != null ? content : "")
                                .done(finishReason != null && !finishReason.isEmpty())
                                .finishReason(finishReason)
                                .rawData(jsonStr)
                                .build());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse stream line: {}", jsonStr, e);
                return Flux.empty();
            }
        }

        return Flux.empty();
    }

    /**
     * 错误日志
     */
    private void logError(Throwable e, String message) {
        if (e instanceof WebClientResponseException.NotFound) {
            log.error("{} - 404 Not Found. Check base URL: {}", message, baseUrl);
        } else if (e instanceof WebClientResponseException.TooManyRequests) {
            log.error("{} - 429 Too Many Requests. Rate limited.", message);
        } else if (e instanceof WebClientResponseException.InternalServerError) {
            log.error("{} - 500 Server Error. Qwen service issue.", message);
        } else if (e.getMessage() != null && e.getMessage().contains("401")) {
            log.error("{} - 401 Unauthorized. Check API key.", message);
        } else {
            log.error(message, e);
        }
    }

    /**
     * 获取当前配置
     */
    public Map<String, String> getConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("base-url", baseUrl);
        config.put("model", defaultModel);
        config.put("timeout", timeoutSeconds + "s");
        config.put("api-key", apiKey != null ? apiKey : "");
        return config;
    }
}
