package com.example.Contract_review.util;

import java.util.List;
import java.util.Map;

/**
 * 文档元素类
 *
 * 用于表示Word文档中的不同类型元素（段落、表格等）
 */
public class DocumentElement {

    public enum Type {
        PARAGRAPH,  // 段落
        TABLE       // 表格
    }

    private Type type;
    private String text;                           // 段落文本内容
    private Map<String, List<String>> tableData;  // 表格数据

    public DocumentElement(Type type, String text, Map<String, List<String>> tableData) {
        this.type = type;
        this.text = text;
        this.tableData = tableData;
    }

    // Getters
    public Type getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public Map<String, List<String>> getTableData() {
        return tableData;
    }

    // 便利方法
    public boolean isParagraph() {
        return type == Type.PARAGRAPH;
    }

    public boolean isTable() {
        return type == Type.TABLE;
    }

    // Setters
    public void setType(Type type) {
        this.type = type;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setTableData(Map<String, List<String>> tableData) {
        this.tableData = tableData;
    }

    @Override
    public String toString() {
        return "DocumentElement{" +
                "type=" + type +
                ", text='" + text + '\'' +
                ", tableData=" + tableData +
                '}';
    }
}