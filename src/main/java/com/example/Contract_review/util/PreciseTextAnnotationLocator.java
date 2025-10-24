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
        logger.debug("【文字匹配】寻找: '{}' (长度: {}, 模式: {})", targetText, targetText.length(), matchPattern);

        // 查找所有匹配位置
        List<Integer> positions = findMatches(completeText, targetText, matchPattern);

        if (positions.isEmpty()) {
            logger.warn("未找到匹配文字: {} (模式: {})", targetText, matchPattern);
            logger.warn("  已尝试：直接匹配 + 规范化匹配");
            logger.debug("  段落文本: '{}'", completeText);
            logger.debug("  目标文本: '{}'", targetText);
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
     * 支持智能文本规范化，处理空格、引号、全宽/半宽字符等差异
     *
     * @param text 要搜索的完整文本
     * @param pattern 查找模式
     * @param matchType 匹配类型（EXACT, CONTAINS, REGEX）
     * @return 所有匹配位置的列表
     */
    private List<Integer> findMatches(String text, String pattern, String matchType) {
        List<Integer> positions = new ArrayList<>();

        if ("EXACT".equalsIgnoreCase(matchType)) {
            // 首先尝试直接精确匹配（原始文本）
            int index = 0;
            while ((index = text.indexOf(pattern, index)) != -1) {
                positions.add(index);
                index += pattern.length();
            }

            // 如果直接匹配失败，尝试规范化后的匹配
            if (positions.isEmpty()) {
                logger.debug("直接精确匹配失败，尝试规范化匹配...");
                String normalizedText = normalizeText(text);
                String normalizedPattern = normalizeText(pattern);

                logger.debug("  原始pattern: '{}' (长度: {})", pattern, pattern.length());
                logger.debug("  规范化后: '{}' (长度: {})", normalizedPattern, normalizedPattern.length());

                // 构建映射关系：规范化文本中的位置 -> 原始文本中的位置
                List<Integer> normalizedMatches = new ArrayList<>();
                index = 0;
                while ((index = normalizedText.indexOf(normalizedPattern, index)) != -1) {
                    normalizedMatches.add(index);
                    index += normalizedPattern.length();
                }

                // 将规范化的位置映射回原始文本位置
                if (!normalizedMatches.isEmpty()) {
                    logger.debug("  规范化匹配找到 {} 个位置，映射到原始文本...", normalizedMatches.size());
                    for (Integer normalizedPos : normalizedMatches) {
                        // 将规范化位置映射回原始文本
                        int originalPos = mapNormalizedPositionToOriginal(text, normalizedPos);
                        if (originalPos >= 0) {
                            positions.add(originalPos);
                            logger.debug("    规范化位置 {} -> 原始位置 {}", normalizedPos, originalPos);
                        }
                    }
                }
            }

            logger.debug("精确匹配: 找到 {} 个位置 (直接匹配: {}, 规范化匹配: {})",
                        positions.size(),
                        positions.isEmpty() ? "0" : "✓",
                        positions.isEmpty() ? "✓" : "无需");

        } else if ("CONTAINS".equalsIgnoreCase(matchType)) {
            // 包含匹配（允许重叠）
            int index = 0;
            while ((index = text.indexOf(pattern, index)) != -1) {
                positions.add(index);
                index += 1;  // 每次只前进1个字符，允许重叠
            }

            // 规范化后的包含匹配
            if (positions.isEmpty()) {
                logger.debug("包含匹配：尝试规范化匹配...");
                String normalizedText = normalizeText(text);
                String normalizedPattern = normalizeText(pattern);

                index = 0;
                while ((index = normalizedText.indexOf(normalizedPattern, index)) != -1) {
                    int originalPos = mapNormalizedPositionToOriginal(text, index);
                    if (originalPos >= 0) {
                        positions.add(originalPos);
                    }
                    index += 1;
                }
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
     * 规范化文本以提高匹配成功率
     * - 统一全宽和半宽字符
     * - 统一引号样式
     * - 规范化空白字符
     * - 移除不可见字符
     *
     * @param text 原始文本
     * @return 规范化后的文本
     */
    private String normalizeText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // 1. 全宽字符转半宽（除了中文）
        text = text
            .replace("（", "(")
            .replace("）", ")")
            .replace("：", ":")
            .replace("；", ";")
            .replace("，", ",")
            .replace("。", ".")
            .replace("！", "!")
            .replace("？", "?")
            .replace("／", "/")
            .replace("～", "~")
            .replace("　", " ");  // 全宽空格转半宽空格

        // 2. 统一引号样式（curly quotes -> straight quotes）
        text = text
            .replace("\u201c", "\"")  // left double quotation mark
            .replace("\u201d", "\"")  // right double quotation mark
            .replace("\u2018", "'")   // left single quotation mark
            .replace("\u2019", "'")   // right single quotation mark
            .replace("«", "\"")
            .replace("»", "\"");

        // 3. 规范化连续空白（多个空白字符变为一个空格）
        text = text.replaceAll("\\s+", " ");

        // 4. 移除首尾空白
        text = text.trim();

        return text;
    }

    /**
     * 将规范化文本中的位置映射回原始文本中的位置
     *
     * @param originalText 原始文本
     * @param normalizedPos 规范化文本中的位置
     * @return 对应的原始文本位置，未找到返回-1
     */
    private int mapNormalizedPositionToOriginal(String originalText, int normalizedPos) {
        String normalizedText = normalizeText(originalText);

        int originalIdx = 0;
        int normalizedIdx = 0;

        while (originalIdx < originalText.length() && normalizedIdx < normalizedPos) {
            char originalChar = originalText.charAt(originalIdx);
            String normalized = normalizeText(String.valueOf(originalChar));

            if (!normalized.isEmpty()) {
                normalizedIdx++;
            }
            originalIdx++;
        }

        return originalIdx;
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
        for (int i = 0; i < runInfos.size(); i++) {
            RunInfo info = runInfos.get(i);
            if (startPos >= info.startPos && startPos < info.endPos) {
                result.setStartRun(info.run);
                result.setStartOffsetInRun(startPos - info.startPos);
                logger.debug("起始Run: 全局位置={}, Run索引={}, Run内偏移={}, 文本='{}'",
                           startPos, i, result.getStartOffsetInRun(), info.text);
                break;
            }
        }

        // 查找结束Run
        for (int i = 0; i < runInfos.size(); i++) {
            RunInfo info = runInfos.get(i);
            if (endPos > info.startPos && endPos <= info.endPos) {
                result.setEndRun(info.run);
                result.setEndOffsetInRun(endPos - info.startPos);
                logger.debug("结束Run: 全局位置={}, Run索引={}, Run内偏移={}, 文本='{}'",
                           endPos, i, result.getEndOffsetInRun(), info.text);
                break;
            }
        }

        // 如果结束位置没有找到对应Run（比如正好在Run结束处），使用最后一个Run
        if (result.getEndRun() == null && !runInfos.isEmpty()) {
            RunInfo lastInfo = runInfos.get(runInfos.size() - 1);
            if (endPos == lastInfo.endPos) {
                result.setEndRun(lastInfo.run);
                result.setEndOffsetInRun(lastInfo.text.length());
                logger.debug("结束位置在最后一个Run: 位置={}, Run索引={}, 偏移={}, 文本='{}'",
                           endPos, runInfos.size() - 1, result.getEndOffsetInRun(), lastInfo.text);
            }
        }

        // 检查是否在单个Run内
        if (result.getStartRun() != null && result.getEndRun() != null) {
            if (result.getStartRun() == result.getEndRun()) {
                result.setIsSingleRun(true);
                logger.debug("匹配在单个Run内：Run索引已确定，可以进行Run分割");
            } else {
                result.setIsSingleRun(false);
                logger.debug("匹配跨越多个Run：需要降级处理");
            }
        }

        // 检查映射结果
        if (result.getStartRun() == null || result.getEndRun() == null) {
            logger.warn("Run映射失败: startRun={}, endRun={}, 匹配范围={}-{}, RunInfo总数={}",
                       result.getStartRun() != null ? "✓" : "✗",
                       result.getEndRun() != null ? "✓" : "✗",
                       startPos, endPos, runInfos.size());

            // 详细输出所有Run的位置范围
            for (int i = 0; i < runInfos.size(); i++) {
                RunInfo info = runInfos.get(i);
                logger.debug("  Run[{}]: 位置范围=[{}-{}), 文本='{}'", i, info.startPos, info.endPos, info.text);
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
