package com.example.Contract_review.qwen.service;

import com.example.Contract_review.qwen.client.QwenClient;
import com.example.Contract_review.qwen.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 通义千问服务层
 */
@Slf4j
@Service
public class QwenService {

    private final QwenClient qwenClient;

    public QwenService(QwenClient qwenClient) {
        this.qwenClient = qwenClient;
    }

    /**
     * 非流式聊天
     */
    public Mono<ChatResponse> chat(List<ChatMessage> messages, String model) {
        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .model(model)
                .stream(false)
                .build();

        return qwenClient.chat(request);
    }

    /**
     * 流式聊天
     */
    public Flux<ChatDelta> streamChat(List<ChatMessage> messages, String model) {
        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .model(model)
                .stream(true)
                .build();

        return qwenClient.streamChat(request);
    }

    /**
     * 同步聊天（阻塞调用）
     */
    public ChatResponse chatBlocking(List<ChatMessage> messages, String model) {
        return chat(messages, model).block();
    }

    /**
     * 获取客户端配置
     */
    public java.util.Map<String, String> getClientConfig() {
        return qwenClient.getConfig();
    }
}
