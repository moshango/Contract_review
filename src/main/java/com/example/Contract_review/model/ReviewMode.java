package com.example.Contract_review.model;

import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 统一审查模式枚举
 *
 * RULES: 仅规则审查（返回Prompt供用户复制到LLM）
 * AI: 调用AI进行审查（但不生成批注）
 * FULL: 完整流程（规则审查 + AI审查 + 批注导入）
 */
public enum ReviewMode {
    RULES("rules"),           // 仅规则审查
    AI("ai"),                 // 仅AI审查（需要已有的规则审查结果）
    FULL("full");             // 完整流程

    private final String value;

    ReviewMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ReviewMode fromString(String value) {
        for (ReviewMode mode : ReviewMode.values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return RULES;  // 默认仅规则审查
    }
}
