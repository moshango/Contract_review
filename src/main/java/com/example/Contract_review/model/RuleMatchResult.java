package com.example.Contract_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 规则匹配结果模型
 *
 * 表示一个条款与规则匹配的结果，包含匹配的规则、条款和其他信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleMatchResult {

    /**
     * 条款ID
     */
    private String clauseId;

    /**
     * 锚点ID（支持两个级别，通过格式自动识别）
     *
     * 【关键】这个字段会被包含在生成的Prompt中，用于告知ChatGPT返回的anchorId格式
     *
     * 支持格式：
     * - 条款级: "anc-c1-4f21" (anc-{clauseId}-{hash})
     * - 段落级: "anc-c1-p2-9f4b" (anc-{clauseId}-p{paraNum}-{hash})
     *
     * 系统会自动通过格式中是否含有 "-pX-" 来判断级别
     */
    private String anchorId;

    /**
     * 【新增】段落级别的锚点列表
     *
     * 包含该条款所有段落的锚点信息，ChatGPT需要根据targetText所在的段落
     * 返回对应的anchorId
     *
     * 例如对于跨3个段落的条款：
     *   - paragraphAnchors[0] -> 标题段落（anc-c1-p1-xxx）+ 文本
     *   - paragraphAnchors[1] -> 第二段（anc-c1-p2-yyy）+ 文本
     *   - paragraphAnchors[2] -> 第三段（anc-c1-p3-zzz）+ 文本
     */
    private List<ParagraphAnchor> paragraphAnchors;

    /**
     * 条款标题
     */
    private String clauseHeading;

    /**
     * 条款文本（完整内容，供LLM审查）
     */
    private String clauseText;

    /**
     * 匹配的规则列表
     */
    private List<ReviewRule> matchedRules;

    /**
     * 匹配的规则数量
     */
    private int matchCount;

    /**
     * 最高风险等级（high > medium > low > blocker）
     */
    private String highestRisk;

    /**
     * 获取匹配规则的检查清单（用于生成 prompt）
     *
     * @return 合并后的检查清单文本
     */
    public String getCombinedChecklist() {
        if (matchedRules == null || matchedRules.isEmpty()) {
            return "";
        }

        StringBuilder combined = new StringBuilder();
        for (ReviewRule rule : matchedRules) {
            if (rule.getChecklist() != null && !rule.getChecklist().trim().isEmpty()) {
                combined.append(rule.getChecklist()).append("\n");
            }
        }
        return combined.toString().trim();
    }

    /**
     * 获取匹配规则的所有建议（for 甲方）
     *
     * @return 建议文本列表
     */
    public List<String> getSuggestionsForPartyA() {
        if (matchedRules == null || matchedRules.isEmpty()) {
            return List.of();
        }

        return matchedRules.stream()
            .map(ReviewRule::getSuggestA)
            .filter(s -> s != null && !s.trim().isEmpty())
            .toList();
    }

    /**
     * 获取匹配规则的所有建议（for 乙方）
     *
     * @return 建议文本列表
     */
    public List<String> getSuggestionsForPartyB() {
        if (matchedRules == null || matchedRules.isEmpty()) {
            return List.of();
        }

        return matchedRules.stream()
            .map(ReviewRule::getSuggestB)
            .filter(s -> s != null && !s.trim().isEmpty())
            .toList();
    }

    /**
     * 判断是否有高风险（high 或 blocker）
     *
     * @return 是否包含高风险规则
     */
    public boolean hasHighRisk() {
        return "high".equalsIgnoreCase(highestRisk) || "blocker".equalsIgnoreCase(highestRisk);
    }
}
