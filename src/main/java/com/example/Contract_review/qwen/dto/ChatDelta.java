package com.example.Contract_review.qwen.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 流式响应的 delta 部分
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatDelta {
    /** 增量内容 */
    @JsonProperty("delta")
    private String delta;

    /** 是否结束（true 表示流结束） */
    @JsonProperty("done")
    private boolean done;

    /** 完成原因 */
    @JsonProperty("finish_reason")
    private String finishReason;

    /** 原始事件数据（用于调试） */
    private String rawData;
}
