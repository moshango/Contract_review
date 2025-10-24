package com.example.Contract_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 合同方信息模型
 * 用于存储文件解析时识别到的甲乙方信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartyInfo {

    /** 甲方公司名称 */
    private String partyA;

    /** 乙方公司名称 */
    private String partyB;

    /** 甲方完整信息行（包含标签和名称） */
    private String partyALine;

    /** 乙方完整信息行（包含标签和名称） */
    private String partyBLine;

    /** 甲方角色标签（如：甲方、买方、委托方等） */
    private String partyARoleName;

    /** 乙方角色标签（如：乙方、卖方、受托方等） */
    private String partyBRoleName;

    /**
     * 检查是否成功识别了双方信息
     */
    public boolean isComplete() {
        return partyA != null && !partyA.isEmpty() &&
               partyB != null && !partyB.isEmpty();
    }
}
