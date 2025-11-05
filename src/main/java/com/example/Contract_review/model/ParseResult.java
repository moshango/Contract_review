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
     * 甲方名称（从合同文本中识别）
     */
    private String partyA;

    /**
     * 乙方名称（从合同文本中识别）
     */
    private String partyB;

    /**
     * 甲方角色标签（如：甲方、买方、委托方等）
     */
    private String partyARoleName;

    /**
     * 乙方角色标签（如：乙方、卖方、受托方等）
     */
    private String partyBRoleName;

    /**
     * 完整的合同文本（包含甲乙方信息）
     * 用于后续的合同方信息提取和 AI 分析
     */
    private String fullContractText;

    /**
     * 解析得到的条款列表
     */
    private List<Clause> clauses;

    /**
     * 元数据信息
     * 包含 wordCount(字数统计)、paragraphCount(段落数)等
     */
    private Map<String, Object> meta;
    
    /**
     * 缓存ID（用于复用解析结果，避免重复解析）
     * 
     * 工作流程：
     * 1. /api/parse 解析并缓存，返回cacheId
     * 2. /api/one-click-review 使用cacheId获取缓存结果
     * 3. 避免重复解析，提升性能10-18%
     */
    private String cacheId;
}
