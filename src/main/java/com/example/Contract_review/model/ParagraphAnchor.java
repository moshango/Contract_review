package com.example.Contract_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 段落级别的锚点映射
 *
 * 用于将条款的每个段落与其对应的anchorId关联
 * 这样可以支持多段落条款，其中每个段落都有自己的锚点
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParagraphAnchor {

    /**
     * 段落在文档中的实际索引
     */
    private Integer paragraphIndex;

    /**
     * 该段落的锚点ID
     * 格式: "anc-{clauseId}-p{paraNum}-{hash}"
     * 例如: "anc-c2-p1-8f3a"
     */
    private String anchorId;

    /**
     * 该段落的文本内容（供ChatGPT审查）
     */
    private String paragraphText;

    /**
     * 段落号（从1开始）
     */
    private Integer paragraphNumber;

    /**
     * 是否为条款标题段落（首段）
     */
    private Boolean isTitle;
}
