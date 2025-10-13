package com.example.Contract_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 审查标准模型
 *
 * 定义合同审查的标准化规则和检查项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewStandard {

    /**
     * 标准ID
     */
    private String id;

    /**
     * 标准名称
     */
    private String name;

    /**
     * 标准描述
     */
    private String description;

    /**
     * 适用的合同类型
     */
    private String contractType;

    /**
     * 审查规则列表
     */
    private List<ReviewRule> rules;

    /**
     * 创建时间
     */
    private String createdAt;

    /**
     * 版本号
     */
    private String version;

    /**
     * 是否启用
     */
    private boolean enabled;
}