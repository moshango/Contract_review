package com.example.Contract_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审查统计信息
 *
 * 包含合同解析、规则匹配的统计数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewStatistics {

    /**
     * 合同总条款数
     */
    private int totalClauses;

    /**
     * 匹配到规则的条款数
     */
    private int matchedClauses;

    /**
     * 高风险条款数
     */
    private int highRiskClauses;

    /**
     * 中风险条款数
     */
    private int mediumRiskClauses;

    /**
     * 低风险条款数
     */
    private int lowRiskClauses;

    /**
     * 规则总数
     */
    private int totalRules;

    /**
     * 适用的规则数（根据合同类型和立场过滤后）
     */
    private int applicableRules;

    /**
     * 触发的规则总数
     */
    private int totalMatchedRules;

    /**
     * 解析耗时（毫秒）
     */
    private Long parseTime;

    /**
     * 规则匹配耗时（毫秒）
     */
    private Long matchTime;

    /**
     * 合同类型
     */
    private String contractType;

    /**
     * 用户立场
     */
    private String userStance;
}
