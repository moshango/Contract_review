package com.example.Contract_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 合同解析结果模型
 *
 * 封装合同解析后的完整信息,包含文件名、标题、条款列表及元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParseResult {

    /**
     * 原始文件名
     */
    private String filename;

    /**
     * 合同标题
     */
    private String title;

    /**
     * 解析得到的条款列表
     */
    private List<Clause> clauses;

    /**
     * 元数据信息
     * 包含 wordCount(字数统计)、paragraphCount(段落数)等
     */
    private Map<String, Object> meta;
}
