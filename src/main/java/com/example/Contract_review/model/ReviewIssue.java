package com.example.Contract_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审查问题模型
 *
 * 表示LLM对某个条款的审查结果,包含问题描述、风险等级及修改建议
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewIssue {

    /**
     * 锚点ID,用于精确定位批注位置
     * 格式: "anc-{clauseId}-{shortHash}"
     */
    private String anchorId;

    /**
     * 条款ID,如 "c1", "c2"
     */
    private String clauseId;

    /**
     * 风险等级
     * 可选值: "HIGH"(高风险), "MEDIUM"(中风险), "LOW"(低风险)
     */
    private String severity;

    /**
     * 问题类别,如 "保密条款", "违约条款", "付款条款"
     */
    private String category;

    /**
     * 发现的问题描述
     */
    private String finding;

    /**
     * 修改建议
     */
    private String suggestion;

    /**
     * 要批注的具体文字（精确匹配）
     * 如果不提供，则使用整段批注（后向兼容）
     * 例如：发现的问题是针对某句话的具体文字
     */
    private String targetText;

    /**
     * 文字匹配模式
     * EXACT: 精确匹配（默认）
     * CONTAINS: 包含匹配
     * REGEX: 正则表达式匹配
     */
    @Builder.Default
    private String matchPattern = "EXACT";

    /**
     * 如果有多个匹配结果，选择第几个（1-based index，默认：1）
     * 例如：该文字在段落中出现了3次，选择第2次
     */
    @Builder.Default
    private Integer matchIndex = 1;
}

