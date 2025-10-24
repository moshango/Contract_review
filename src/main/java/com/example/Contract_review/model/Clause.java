package com.example.Contract_review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 合同条款模型
 *
 * 表示合同中的一个条款,包含条款ID、标题、正文内容、表格数据、锚点ID及段落索引位置
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
     * 条款包含的表格数据
     * 每个Map代表一个表格，key为列名，value为该列的所有行数据
     */
    private List<Map<String, List<String>>> tables;

    /**
     * 锚点ID,用于精确定位批注位置（主锚点，指向条款标题）
     * 格式: "anc-{clauseId}-{shortHash}"
     * 如: "anc-c1-4f21"
     */
    private String anchorId;

    /**
     * 【新增】段落级别的锚点列表
     *
     * 为该条款的每个段落生成唯一的anchorId，包括：
     * - 标题段落
     * - 内容段落
     * - 所有子段落
     *
     * 这样ChatGPT可以精确指定是哪个段落的内容
     */
    private List<ParagraphAnchor> paragraphAnchors;

    /**
     * 条款在文档中的起始段落索引
     */
    private Integer startParaIndex;

    /**
     * 条款在文档中的结束段落索引
     */
    private Integer endParaIndex;

    /**
     * 获取包含表格内容的完整文本
     * 将正文内容和表格数据合并为一个完整的文本表示
     *
     * @return 完整的条款文本（包括表格）
     */
    public String getFullText() {
        StringBuilder fullText = new StringBuilder();

        // 添加正文内容
        if (text != null && !text.trim().isEmpty()) {
            fullText.append(text.trim());
        }

        // 添加表格内容
        if (tables != null && !tables.isEmpty()) {
            for (int i = 0; i < tables.size(); i++) {
                fullText.append("\n\n【表格").append(i + 1).append("】\n");
                Map<String, List<String>> table = tables.get(i);

                if (!table.isEmpty()) {
                    // 获取所有列名
                    List<String> headers = new ArrayList<>(table.keySet());

                    // 添加表头
                    fullText.append(String.join(" | ", headers)).append("\n");

                    // 计算最大行数
                    int maxRows = table.values().stream()
                            .mapToInt(List::size)
                            .max()
                            .orElse(0);

                    // 添加表格内容
                    for (int row = 0; row < maxRows; row++) {
                        List<String> rowData = new ArrayList<>();
                        for (String header : headers) {
                            List<String> columnData = table.get(header);
                            if (columnData != null && row < columnData.size()) {
                                rowData.add(columnData.get(row));
                            } else {
                                rowData.add("");
                            }
                        }
                        fullText.append(String.join(" | ", rowData)).append("\n");
                    }
                }
            }
        }

        return fullText.toString();
    }
}
