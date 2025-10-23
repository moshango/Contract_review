package com.example.Contract_review.qwen.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通义千问聊天请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRequest {
    /** 聊天消息列表 */
    private List<ChatMessage> messages;

    /** 模型名称，如 qwen-max */
    private String model;

    /** 是否流式输出 */
    @Builder.Default
    private boolean stream = false;

    /** 采样温度，范围 [0, 2)，默认 0.8 */
    @Builder.Default
    private double temperature = 0.8;

    /** 核采样参数，范围 (0, 1)，默认 0.9 */
    @Builder.Default
    private double top_p = 0.9;

    /** 验证字段 */
    public boolean validate() {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        if (model == null || model.trim().isEmpty()) {
            return false;
        }
        return messages.stream().allMatch(m ->
                m.getRole() != null && !m.getRole().isEmpty() &&
                m.getContent() != null && !m.getContent().isEmpty()
        );
    }
}
