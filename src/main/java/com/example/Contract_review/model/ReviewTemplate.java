package com.example.Contract_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审查模板模型
 *
 * 用于生成LLM审查的Prompt模板
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewTemplate {

    /**
     * 模板ID
     */
    private String id;

    /**
     * 模板名称
     */
    private String name;

    /**
     * 适用的合同类型
     */
    private String contractType;

    /**
     * 角色定义
     */
    private String roleDefinition;

    /**
     * 审查重点说明
     */
    private String reviewFocus;

    /**
     * 输出格式要求
     */
    private String outputFormat;

    /**
     * 完整的Prompt模板
     */
    private String promptTemplate;

    /**
     * 是否为默认模板
     */
    private boolean isDefault;

    /**
     * 创建时间
     */
    private String createdAt;
}