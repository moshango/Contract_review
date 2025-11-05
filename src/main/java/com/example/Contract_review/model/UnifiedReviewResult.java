package com.example.Contract_review.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一审查结果模型
 *
 * 包含规则审查结果和可选的AI审查结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedReviewResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 错误信息（如有）
     */
    private String error;

    /**
     * 规则审查统计信息
     */
    private ReviewStatistics statistics;

    /**
     * 匹配的条款及规则详情
     */
    private List<RuleMatchResult> matchResults;

    /**
     * 为LLM生成的Prompt
     */
    private String prompt;

    /**
     * 解析结果缓存ID（用于后续批注）
     */
    private String parseResultId;

    /**
     * AI审查结果（仅当reviewMode包含AI时）
     */
    private JsonNode aiResult;

    /**
     * 带批注的文档URL或Base64（仅当reviewMode=FULL时）
     */
    private String annotatedDocumentUrl;

    /**
     * 审查指导信息（规则应用指南）
     */
    private String guidance;

    /**
     * 用户的审查立场
     */
    private String userStance;

    /**
     * 处理耗时（毫秒）
     */
    private Long processingTime;

    /**
     * 审查模式（用于前端记录）
     */
    private String reviewMode;
}
