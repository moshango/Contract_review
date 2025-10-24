package com.example.Contract_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 合同方提取请求
 * 用于将已解析的合同内容发送给 Qwen 进行甲乙方识别
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PartyExtractionRequest {
    /**
     * 合同文本（完整或摘要）
     */
    private String contractText;

    /**
     * 合同类型
     */
    private String contractType;

    /**
     * 可选：parseResultId 用于后续关联
     */
    private String parseResultId;
}
