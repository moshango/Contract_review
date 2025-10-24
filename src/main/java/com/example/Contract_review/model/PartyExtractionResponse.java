package com.example.Contract_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 合同方提取响应
 * 包含识别的甲乙方信息和相应的建议
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PartyExtractionResponse {
    /**
     * 是否成功识别
     */
    private boolean success;

    /**
     * 识别的甲方名称（A方）
     */
    private String partyA;

    /**
     * 识别的乙方名称（B方）
     */
    private String partyB;

    /**
     * 甲方在合同中的原始角色名称
     * 例如：甲方、买方、需方、发包人、客户等
     */
    private String partyARoleName;

    /**
     * 乙方在合同中的原始角色名称
     * 例如：乙方、卖方、供方、承包人、服务商等
     */
    private String partyBRoleName;

    /**
     * Qwen 返回的建议立场
     * 值为 "A" 或 "B"
     */
    private String recommendedStance;

    /**
     * 建议该立场的原因说明
     */
    private String stanceReason;

    /**
     * 错误消息（如果识别失败）
     */
    private String errorMessage;

    /**
     * 处理耗时（毫秒）
     */
    private long processingTime;
}

