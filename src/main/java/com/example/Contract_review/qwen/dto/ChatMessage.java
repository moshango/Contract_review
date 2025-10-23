package com.example.Contract_review.qwen.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通义千问聊天消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {
    /** 消息角色: user, assistant, system */
    private String role;

    /** 消息内容 */
    private String content;
}
