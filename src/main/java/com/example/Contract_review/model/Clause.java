package com.example.Contract_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 合同条款模型
 *
 * 表示合同中的一个条款,包含条款ID、标题、正文内容、锚点ID及段落索引位置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Clause {

    /**
     * 条款唯一标识符,如 "c1", "c2"
     */
    private String id;

    /**
     * 条款标题,如 "第一条 合作范围"
     */
    private String heading;

    /**
     * 条款正文内容
     */
    private String text;

    /**
     * 锚点ID,用于精确定位批注位置
     * 格式: "anc-{clauseId}-{shortHash}"
     * 如: "anc-c1-4f21"
     */
    private String anchorId;

    /**
     * 条款在文档中的起始段落索引
     */
    private Integer startParaIndex;

    /**
     * 条款在文档中的结束段落索引
     */
    private Integer endParaIndex;
}
