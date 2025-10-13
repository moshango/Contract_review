package com.example.Contract_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 审查规则模型
 *
 * 定义具体的审查检查项和判断标准
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRule {

    /**
     * 规则ID
     */
    private String id;

    /**
     * 规则名称
     */
    private String name;

    /**
     * 规则描述
     */
    private String description;

    /**
     * 规则类别
     */
    private String category;

    /**
     * 风险等级 HIGH/MEDIUM/LOW
     */
    private String severity;

    /**
     * 检查的条款类型/关键词
     */
    private List<String> targetClauses;

    /**
     * 检查条件描述
     */
    private String condition;

    /**
     * 问题描述模板
     */
    private String findingTemplate;

    /**
     * 建议模板
     */
    private String suggestionTemplate;

    /**
     * 是否启用
     */
    private boolean enabled;

    /**
     * 权重(用于排序)
     */
    private int weight;
}