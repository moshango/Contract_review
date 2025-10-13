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
}
