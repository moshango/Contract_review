package com.example.Contract_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 审查规则模型
 *
 * 从 rules.xlsx 加载的规则配置，用于合同条款的关键字和正则匹配
 * 匹配流程：关键字粗召回 → 正则精筛 → 为LLM生成 prompt
 *
 * 注：此模型同时支持两种规则格式：
 * 1. 从 rules.xlsx 加载：使用 contractTypes, keywords, regex, checklist 等
 * 2. 从 ReviewStandard 创建：使用 name, description, severity, targetClauses 等
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRule {

    /**
     * 规则ID（自动生成，格式：rule_N）
     */
    private String id;

    /**
     * 规则名称（用于向后兼容 ReviewStandardService）
     */
    private String name;

    /**
     * 规则描述（用于向后兼容 ReviewStandardService）
     */
    private String description;

    /**
     * 适用的合同类型，多个用分号隔开
     * 例如：采购;外包;NDA
     * 为空表示对所有合同类型适用
     */
    private String contractTypes;

    /**
     * 适用范围
     * Neutral（通用）/ A（甲方）/ B（乙方）
     */
    @Builder.Default
    private String partyScope = "Neutral";

    /**
     * 风险等级
     * low / medium / high / blocker
     * 也用于向后兼容 ReviewStandardService（使用 severity 别名）
     */
    private String risk;

    /**
     * 风险等级（向后兼容，同义于 risk）
     */
    private String severity;

    /**
     * 规则类别（向后兼容）
     */
    private String category;

    /**
     * 关键字列表，多个关键字用分号隔开
     * 例如：付款方式;支付周期;付款条件
     * 任一关键字命中即判定为"疑似命中"（广召回）
     */
    private String keywords;

    /**
     * 检查的条款类型/关键词（向后兼容）
     */
    private List<String> targetClauses;

    /**
     * 正则表达式，用于更精确的匹配
     * 例如：支付.*\d+天
     * 可以为空，关键字优先级更高
     */
    private String regex;

    /**
     * 编译后的正则Pattern（运行时生成，用于提高匹配性能）
     */
    private transient Pattern compiledPattern;

    /**
     * 审查检查要点，多行文本，用\n分隔
     * 给 LLM 的检查清单，例如：
     * 1. 确认付款方式（现金/票据）
     * 2. 明确付款周期
     * 3. 检查付款条件是否完整
     */
    private String checklist;

    /**
     * 检查条件描述（向后兼容）
     */
    private String condition;

    /**
     * 问题描述模板（向后兼容）
     */
    private String findingTemplate;

    /**
     * 对甲方的建议文本
     */
    private String suggestA;

    /**
     * 对乙方的建议文本
     */
    private String suggestB;

    /**
     * 建议模板（向后兼容）
     */
    private String suggestionTemplate;

    /**
     * 是否启用（向后兼容）
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * 权重（用于排序，向后兼容）
     */
    @Builder.Default
    private int weight = 0;

    /**
     * 在条款中实际匹配到的关键词列表（运行时计算，用于前端显示）
     */
    private List<String> matchedKeywords;

    /**
     * 获取适用于特定 party 的建议
     *
     * @param party "A" 或 "B"，表示甲方或乙方
     * @return 对应的建议，如果 partyScope 为 Neutral 则返回 suggestA
     */
    public String getSuggestion(String party) {
        if ("B".equals(party)) {
            return suggestB;
        }
        return suggestA;
    }

    /**
     * 获取关键字列表
     * @return 分割后的关键字数组
     */
    public String[] getKeywordList() {
        if (keywords == null || keywords.trim().isEmpty()) {
            return new String[0];
        }
        return keywords.split(";");
    }

    /**
     * 检查是否与给定文本匹配
     * 优先检查关键字（广召回），再检查正则表达式（精筛）
     * 同时记录实际匹配的关键词用于前端显示
     *
     * @param text 要检查的文本
     * @return 是否匹配
     */
    public boolean matches(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        // 清空之前的匹配关键词列表
        this.matchedKeywords = new java.util.ArrayList<>();

        // 优先检查关键字（广召回）
        String sanitizedText = text.replaceAll("\\s+", "");
        String[] keywordList = getKeywordList();
        for (String keyword : keywordList) {
            String trimmedKeyword = keyword.trim();
            if (trimmedKeyword.isEmpty()) {
                continue;
            }
            if (text.contains(trimmedKeyword)) {
                this.matchedKeywords.add(trimmedKeyword);
            } else {
                String normalizedKeyword = trimmedKeyword.replaceAll("\\s+", "");
                if (!normalizedKeyword.isEmpty() && sanitizedText.contains(normalizedKeyword)) {
                    this.matchedKeywords.add(trimmedKeyword + "(忽略空白)");
                }
            }
        }

        // 如果关键字匹配了，直接返回true
        if (!this.matchedKeywords.isEmpty()) {
            return true;
        }

        // 检查 targetClauses（向后兼容）
        if (targetClauses != null && !targetClauses.isEmpty()) {
            String lowerText = text.toLowerCase();
            for (String clause : targetClauses) {
                if (lowerText.contains(clause.toLowerCase())) {
                    this.matchedKeywords.add(clause);
                    return true;
                }
            }
        }

        // 如果有正则表达式，进行正则匹配
        if (regex != null && !regex.trim().isEmpty()) {
            if (compiledPattern == null) {
                try {
                    compiledPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
                } catch (Exception e) {
                    // 正则编译失败，记录日志但不中断
                    System.err.println("Failed to compile regex for rule " + id + ": " + regex);
                    return false;
                }
            }
            if (compiledPattern.matcher(text).find()) {
                this.matchedKeywords.add("正则: " + regex);
                return true;
            }
            // 对去除空白的文本再尝试一次
            if (compiledPattern.matcher(sanitizedText).find()) {
                this.matchedKeywords.add("正则(忽略空白): " + regex);
                return true;
            }
        }

        return false;
    }

    /**
     * 检查此规则是否适用于给定的合同类型
     *
     * @param contractType 合同类型
     * @return 是否适用
     */
    public boolean applicableToContractType(String contractType) {
        if (contractTypes == null || contractTypes.trim().isEmpty()) {
            return true; // 如果未指定，则对所有合同类型适用
        }

        String[] types = contractTypes.split(";");
        for (String type : types) {
            if (type.trim().equalsIgnoreCase(contractType.trim())) {
                return true;
            }
        }
        return false;
    }
}