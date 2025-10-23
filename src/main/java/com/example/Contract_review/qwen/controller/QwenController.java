package com.example.Contract_review.qwen.controller;

import com.example.Contract_review.qwen.dto.*;
import com.example.Contract_review.qwen.service.QwenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * 通义千问 API 控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/qwen")
public class QwenController {

    private final QwenService qwenService;

    public QwenController(QwenService qwenService) {
        this.qwenService = qwenService;
    }

    /**
     * 非流式聊天
     * POST /api/qwen/chat
     * {
     *   "messages": [
     *     {"role": "user", "content": "你好"}
     *   ],
     *   "model": "qwen-max",
     *   "stream": false
     * }
     */
    @PostMapping("/chat")
    public Mono<ResponseEntity<ChatResponse>> chat(@RequestBody ChatRequest request) {
        if (!request.validate()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        log.info("Chat request: model={}, messages={}", request.getModel(), request.getMessages().size());

        return qwenService.chat(request.getMessages(), request.getModel())
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Chat error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    /**
     * 流式聊天
     * POST /api/qwen/stream
     * {
     *   "messages": [
     *     {"role": "user", "content": "你好"}
     *   ],
     *   "model": "qwen-max"
     * }
     */
    @PostMapping("/stream")
    public Flux<String> streamChat(@RequestBody ChatRequest request) {
        if (!request.validate()) {
            return Flux.error(new IllegalArgumentException("Invalid request"));
        }

        log.info("Stream chat request: model={}, messages={}", request.getModel(), request.getMessages().size());

        return qwenService.streamChat(request.getMessages(), request.getModel())
                .map(delta -> "data: " + toJsonString(delta) + "\n\n")
                .onErrorResume(e -> {
                    log.error("Stream error: {}", e.getMessage());
                    return Flux.just("data: {\"error\": \"" + e.getMessage() + "\"}\n\n");
                })
                .doOnComplete(() -> log.info("Stream completed"));
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("config", qwenService.getClientConfig());
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    /**
     * 转换为 JSON 字符串
     */
    private String toJsonString(Object obj) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"error\": \"Failed to serialize\"}";
        }
    }
}
