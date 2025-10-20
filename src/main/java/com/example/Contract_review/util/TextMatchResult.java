package com.example.Contract_review.util;

import org.dom4j.Element;

/**
 * 文字匹配结果
 *
 * 包含匹配到的Run元素及其在Run中的位置信息
 */
public class TextMatchResult {

    /**
     * 起始Run元素（包含匹配文字开始部分的Run）
     */
    private Element startRun;

    /**
     * 结束Run元素（包含匹配文字结束部分的Run）
     */
    private Element endRun;

    /**
     * 匹配文字在起始Run中的偏移位置（字符索引）
     */
    private int startOffsetInRun;

    /**
     * 匹配文字在结束Run中的偏移位置（字符索引）
     */
    private int endOffsetInRun;

    /**
     * 匹配文字在段落完整文本中的起始位置
     */
    private int startPosition;

    /**
     * 匹配文字在段落完整文本中的结束位置
     */
    private int endPosition;

    // Getters and Setters

    public Element getStartRun() {
        return startRun;
    }

    public void setStartRun(Element startRun) {
        this.startRun = startRun;
    }

    public Element getEndRun() {
        return endRun;
    }

    public void setEndRun(Element endRun) {
        this.endRun = endRun;
    }

    public int getStartOffsetInRun() {
        return startOffsetInRun;
    }

    public void setStartOffsetInRun(int startOffsetInRun) {
        this.startOffsetInRun = startOffsetInRun;
    }

    public int getEndOffsetInRun() {
        return endOffsetInRun;
    }

    public void setEndOffsetInRun(int endOffsetInRun) {
        this.endOffsetInRun = endOffsetInRun;
    }

    public int getStartPosition() {
        return startPosition;
    }

    public void setStartPosition(int startPosition) {
        this.startPosition = startPosition;
    }

    public int getEndPosition() {
        return endPosition;
    }

    public void setEndPosition(int endPosition) {
        this.endPosition = endPosition;
    }

    @Override
    public String toString() {
        return "TextMatchResult{" +
                "startRun=" + (startRun != null ? startRun.asXML().substring(0, Math.min(50, startRun.asXML().length())) : "null") +
                ", endRun=" + (endRun != null ? endRun.asXML().substring(0, Math.min(50, endRun.asXML().length())) : "null") +
                ", startOffsetInRun=" + startOffsetInRun +
                ", endOffsetInRun=" + endOffsetInRun +
                ", startPosition=" + startPosition +
                ", endPosition=" + endPosition +
                '}';
    }
}
