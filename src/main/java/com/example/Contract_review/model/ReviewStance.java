package com.example.Contract_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审查立场模型
 *
 * 用于存储用户的审查立场设置（甲方或乙方），
 * 在规则匹配时可根据用户立场返回对应的建议
 *
 * 立场类型：
 * - "A" - 甲方立场（Party A）
 * - "B" - 乙方立场（Party B）
 * - null/空 - 中立立场（返回通用建议）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewStance {

    /**
     * 用户选择的立场
     * "A" (甲方) / "B" (乙方) / null或空（中立）
     */
    private String party;

    /**
     * 立场描述（用于前端显示）
     */
    private String description;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 是否为默认设置
     */
    private Boolean isDefault;

    /**
     * 检查给定的规则是否对当前立场适用
     *
     * @param rule 审查规则
     * @return true 如果规则适用于当前立场
     */
    public boolean isRuleApplicable(ReviewRule rule) {
        if (rule == null || rule.getPartyScope() == null) {
            return true;
        }

        String ruleScope = rule.getPartyScope();

        // 如果规则是"Neutral"，则对所有立场适用
        if ("Neutral".equalsIgnoreCase(ruleScope)) {
            return true;
        }

        // 如果用户没有设置立场（中立），则只返回Neutral的规则
        if (party == null || party.trim().isEmpty()) {
            return "Neutral".equalsIgnoreCase(ruleScope);
        }

        // 如果用户设置了立场，则返回对应立场的规则或Neutral的规则
        return party.equalsIgnoreCase(ruleScope) || "Neutral".equalsIgnoreCase(ruleScope);
    }

    /**
     * 根据立场获取规则建议
     *
     * @param rule 审查规则
     * @return 对应立场的建议文本
     */
    public String getRuleSuggestion(ReviewRule rule) {
        if (rule == null) {
            return null;
        }

        if (party == null || party.trim().isEmpty() || "A".equalsIgnoreCase(party)) {
            return rule.getSuggestA();
        } else if ("B".equalsIgnoreCase(party)) {
            return rule.getSuggestB();
        }

        return null;
    }

    /**
     * 创建默认的中立立场
     */
    public static ReviewStance neutral() {
        return ReviewStance.builder()
            .party(null)
            .description("中立")
            .timestamp(System.currentTimeMillis())
            .isDefault(true)
            .build();
    }

    /**
     * 创建甲方立场
     */
    public static ReviewStance partyA() {
        return ReviewStance.builder()
            .party("A")
            .description("甲方")
            .timestamp(System.currentTimeMillis())
            .isDefault(false)
            .build();
    }

    /**
     * 创建乙方立场
     */
    public static ReviewStance partyB() {
        return ReviewStance.builder()
            .party("B")
            .description("乙方")
            .timestamp(System.currentTimeMillis())
            .isDefault(false)
            .build();
    }

    /**
     * 根据党派标识创建立场
     *
     * @param partyId "A" 或 "B" 或其他（返回中立）
     * @return ReviewStance 对象
     */
    public static ReviewStance fromPartyId(String partyId) {
        if ("A".equalsIgnoreCase(partyId)) {
            return partyA();
        } else if ("B".equalsIgnoreCase(partyId)) {
            return partyB();
        } else {
            return neutral();
        }
    }
}
