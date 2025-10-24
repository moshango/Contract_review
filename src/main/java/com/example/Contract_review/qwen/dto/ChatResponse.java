package com.example.Contract_review.qwen.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 通义千问聊天响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatResponse {
    /** 响应ID */
    private String id;

    /** 响应内容 */
    @JsonProperty("content")
    private String content;

    /** 模型名称 */
    private String model;

    /** 完成状态，stop 表示正常结束 */
    @JsonProperty("finish_reason")
    private String finishReason;

    /** 使用的 token 数 */
    @JsonProperty("usage")
    private Usage usage;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        @JsonProperty("input_tokens")
        private int inputTokens;

        @JsonProperty("output_tokens")
        private int outputTokens;
    }

    /**
     * 提取响应内容
     * @return 响应内容，如果为 null 则返回空字符串
     */
    public String extractContent() {
        return content != null ? content : "";
    }
}

