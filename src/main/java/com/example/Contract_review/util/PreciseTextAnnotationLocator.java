package com.example.Contract_review.util;

import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 精确文字匹配和批注定位工具
 *
 * 用于在Word段落中进行精确的文字查找，支持多种匹配模式（精确、包含、正则），
 * 并将全局文本位置映射到具体的Run元素及其偏移位置
 */
@Component
public class PreciseTextAnnotationLocator {

    private static final Logger logger = LoggerFactory.getLogger(PreciseTextAnnotationLocator.class);

    private static final Namespace W_NS =
            Namespace.get("w", "http://schemas.openxmlformats.org/wordprocessingml/2006/main");

    /**
     * 在段落中查找目标文字
     *
     * @param paragraph 目标段落元素
     * @param targetText 要查找的文字
     * @param matchPattern 匹配模式（EXACT, CONTAINS, REGEX）
     * @param matchIndex 如果有多个匹配，选择第几个（1-based）
     * @return 匹配结果，包含Run元素和位置信息；未找到则返回null
     */
    public TextMatchResult findTextInParagraph(Element paragraph,
                                              String targetText,
                                              String matchPattern,
                                              int matchIndex) {
        if (targetText == null || targetText.isEmpty()) {
            logger.warn("targetText为空或null");
            return null;
        }

        if (matchPattern == null) {
            matchPattern = "EXACT";
        }

        // 获取段落中的所有Run元素
        List<Element> runs = getRuns(paragraph);
        if (runs.isEmpty()) {
            logger.debug("段落中没有Run元素");
            return null;
        }

        // 构建完整文本和Run映射关系
        StringBuilder fullText = new StringBuilder();
        List<RunInfo> runInfos = new ArrayList<>();

        for (Element run : runs) {
            int startPos = fullText.length();
            String runText = extractRunText(run);
            fullText.append(runText);
            int endPos = fullText.length();

            runInfos.add(new RunInfo(run, startPos, endPos, runText));
        }

        String completeText = fullText.toString();
        logger.debug("段落完整文本长度: {}, 内容: {}", completeText.length(), completeText);

        // 查找所有匹配位置
        List<Integer> positions = findMatches(completeText, targetText, matchPattern);

        if (positions.isEmpty()) {
            logger.warn("未找到匹配文字: {} (模式: {})", targetText, matchPattern);
            return null;
        }

        if (matchIndex < 1 || matchIndex > positions.size()) {
            logger.warn("匹配索引超出范围: {} (总共 {} 个匹配)",
                       matchIndex, positions.size());
            matchIndex = Math.max(1, Math.min(matchIndex, positions.size()));
        }

        int matchPos = positions.get(matchIndex - 1);
        int endPos = matchPos + targetText.length();

        logger.debug("找到匹配文字：位置={}, 长度={}, 索引={}/{}",
                   matchPos, targetText.length(), matchIndex, positions.size());

        // 映射到Run元素
        return mapPositionToRuns(runInfos, matchPos, endPos);
    }

    /**
     * 查找所有匹配位置
     *
     * @param text 要搜索的完整文本
     * @param pattern 查找模式
     * @param matchType 匹配类型（EXACT, CONTAINS, REGEX）
     * @return 所有匹配位置的列表
     */
    private List<Integer> findMatches(String text, String pattern, String matchType) {
        List<Integer> positions = new ArrayList<>();

        if ("EXACT".equalsIgnoreCase(matchType)) {
            // 精确匹配
            int index = 0;
            while ((index = text.indexOf(pattern, index)) != -1) {
                positions.add(index);
                index += pattern.length();
            }
            logger.debug("精确匹配: 找到 {} 个位置", positions.size());

        } else if ("CONTAINS".equalsIgnoreCase(matchType)) {
            // 包含匹配（允许重叠）
            int index = 0;
            while ((index = text.indexOf(pattern, index)) != -1) {
                positions.add(index);
                index += 1;  // 每次只前进1个字符，允许重叠
            }
            logger.debug("包含匹配: 找到 {} 个位置", positions.size());

        } else if ("REGEX".equalsIgnoreCase(matchType)) {
            // 正则表达式匹配
            try {
                Pattern regex = Pattern.compile(pattern);
                var matcher = regex.matcher(text);
                while (matcher.find()) {
                    positions.add(matcher.start());
                }
                logger.debug("正则匹配: 找到 {} 个位置", positions.size());
            } catch (Exception e) {
                logger.error("正则表达式编译失败: {}", pattern, e);
            }
        }

        return positions;
    }

    /**
     * 获取段落中所有Run元素
     */
    public List<Element> getRuns(Element paragraph) {
        return paragraph.elements(QName.get("r", W_NS));
    }

    /**
     * 提取Run中的文字
     */
    private String extractRunText(Element run) {
        StringBuilder text = new StringBuilder();
        List<Element> textElements = run.elements(QName.get("t", W_NS));
        for (Element textElem : textElements) {
            text.append(textElem.getText());
        }
        return text.toString();
    }

    /**
     * 将全局文本位置映射到具体的Run元素
     *
     * @param runInfos Run信息列表
     * @param startPos 全局开始位置
     * @param endPos 全局结束位置
     * @return 映射结果
     */
    private TextMatchResult mapPositionToRuns(List<RunInfo> runInfos,
                                              int startPos, int endPos) {
        TextMatchResult result = new TextMatchResult();
        result.setStartPosition(startPos);
        result.setEndPosition(endPos);

        // 查找起始Run
        for (RunInfo info : runInfos) {
            if (startPos >= info.startPos && startPos < info.endPos) {
                result.setStartRun(info.run);
                result.setStartOffsetInRun(startPos - info.startPos);
                logger.debug("起始Run: 全局位置={}, Run内偏移={}", startPos, result.getStartOffsetInRun());
                break;
            }
        }

        // 查找结束Run
        for (RunInfo info : runInfos) {
            if (endPos > info.startPos && endPos <= info.endPos) {
                result.setEndRun(info.run);
                result.setEndOffsetInRun(endPos - info.startPos);
                logger.debug("结束Run: 全局位置={}, Run内偏移={}", endPos, result.getEndOffsetInRun());
                break;
            }
        }

        // 如果结束位置没有找到对应Run（比如正好在Run结束处），使用最后一个Run
        if (result.getEndRun() == null && !runInfos.isEmpty()) {
            RunInfo lastInfo = runInfos.get(runInfos.size() - 1);
            if (endPos == lastInfo.endPos) {
                result.setEndRun(lastInfo.run);
                result.setEndOffsetInRun(lastInfo.text.length());
                logger.debug("结束位置在最后一个Run: 偏移={}", result.getEndOffsetInRun());
            }
        }

        return result;
    }

    /**
     * 内部类：Run信息
     */
    private static class RunInfo {
        Element run;
        int startPos;      // 该Run在完整文本中的起始位置
        int endPos;        // 该Run在完整文本中的结束位置
        String text;       // 该Run的文本内容

        RunInfo(Element run, int startPos, int endPos, String text) {
            this.run = run;
            this.startPos = startPos;
            this.endPos = endPos;
            this.text = text;
        }
    }
}
