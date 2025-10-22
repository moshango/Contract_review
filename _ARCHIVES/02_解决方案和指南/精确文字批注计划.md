# 📝 精确文字匹配批注系统 - 完整修改方案

## 🎯 需求分析

你需要的是：
1. **锚点定位** - 确认锚点位置（已有）
2. **文字精确匹配** - 在锚点附近按文字精确匹配要批注的内容
3. **精确批注插入** - 只在匹配文字处插入批注，而不是整段

**当前问题**：现有实现是在段落级别插入批注，对整个段落标记

**改进目标**：在Run级别（文字级别）插入批注，精确到具体文字

---

## 🔧 修改方案

### 方案概览

```
文本查找流程：
  1. 通过 anchorId 精确定位锚点所在段落 ✓ (已有)
  2. 在锚点段落中查找要批注的文字 (新增)
  3. 在该文字的 Run 元素中插入批注标记 (修改)
  4. 精确定位到字符级别，而非段落级别 (改进)
```

### 核心改进点

#### 1. 扩展 ReviewIssue 模型
添加字段用于指定要批注的精确文字：

```java
public class ReviewIssue {
    // ... 现有字段 ...

    /**
     * 要批注的文字内容（精确匹配）
     * 例如：发现的问题是针对某句话
     */
    private String targetText;

    /**
     * 文字匹配模式
     * EXACT: 精确匹配
     * CONTAINS: 包含匹配
     * REGEX: 正则匹配
     */
    private String matchPattern = "EXACT";
}
```

#### 2. 创建新的文字匹配器工具类

```java
/**
 * 文字精确匹配和批注定位工具
 */
public class PreciseTextAnnotationLocator {

    /**
     * 在段落中查找目标文字的精确位置
     * @param paragraph 目标段落
     * @param targetText 要查找的文字
     * @param matchPattern 匹配模式
     * @return 包含文字位置信息的结果
     */
    public TextMatchResult findTextInParagraph(Element paragraph,
                                               String targetText,
                                               String matchPattern);

    /**
     * 在指定 Run 中精确定位文字位置
     */
    public TextLocation findTextInRun(Element run, String targetText);

    /**
     * 获取文字所在的 Run 元素列表
     */
    public List<Element> getRuns(Element paragraph);
}
```

#### 3. 修改批注插入方式

**从段落级别→Run级别：**

```java
// 旧方式（段落级别）
private void insertCommentRangeInDocument(Element paragraph, int commentId) {
    // 在整个段落上插入批注标记
    Element commentRangeStart = paragraph.addElement(...);
    // ... 整段批注
}

// 新方式（文字级别）
private void insertCommentRangeInDocumentPrecise(Element paragraph,
                                                 int commentId,
                                                 String targetText,
                                                 String matchPattern) {
    // 1. 查找目标文字
    TextMatchResult matchResult = locator.findTextInParagraph(
        paragraph, targetText, matchPattern);

    if (matchResult == null) {
        logger.warn("未找到目标文字：{}", targetText);
        return;
    }

    // 2. 在匹配位置的 Run 中插入批注标记
    Element targetRun = matchResult.getRun();
    int startOffset = matchResult.getStartOffset();
    int endOffset = matchResult.getEndOffset();

    // 3. 拆分 Run（如果需要）并插入批注标记
    splitAndAnnotateRun(targetRun, startOffset, endOffset, commentId);
}
```

---

## 📋 详细实现步骤

### Step 1: 修改 ReviewIssue 模型

```java
package com.example.Contract_review.model;

public class ReviewIssue {
    // ... 现有字段 ...
    private String clauseId;
    private String anchorId;
    private String severity;
    private String category;
    private String finding;
    private String suggestion;

    // 新增字段
    /**
     * 要批注的具体文字（精确匹配）
     */
    private String targetText;

    /**
     * 文字匹配模式：EXACT(精确), CONTAINS(包含), REGEX(正则)
     */
    private String matchPattern = "EXACT";

    /**
     * 如果有多个匹配，选择第几个 (1-based index)
     */
    private Integer matchIndex = 1;

    // Getters/Setters...
}
```

### Step 2: 创建精确匹配定位工具

```java
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
 */
@Component
public class PreciseTextAnnotationLocator {

    private static final Logger logger = LoggerFactory.getLogger(
        PreciseTextAnnotationLocator.class);

    private static final Namespace W_NS =
        Namespace.get("w", "http://schemas.openxmlformats.org/wordprocessingml/2006/main");

    /**
     * 在段落中查找目标文字
     */
    public TextMatchResult findTextInParagraph(Element paragraph,
                                               String targetText,
                                               String matchPattern,
                                               int matchIndex) {
        if (targetText == null || targetText.isEmpty()) {
            return null;
        }

        // 获取所有 Run 元素
        List<Element> runs = getRuns(paragraph);

        // 构建完整文本和映射
        StringBuilder fullText = new StringBuilder();
        List<RunInfo> runInfos = new ArrayList<>();

        for (Element run : runs) {
            int startPos = fullText.length();
            String runText = extractRunText(run);
            fullText.append(runText);
            int endPos = fullText.length();

            runInfos.add(new RunInfo(run, startPos, endPos, runText));
        }

        // 查找匹配
        List<Integer> positions = findMatches(fullText.toString(),
                                              targetText, matchPattern);

        if (positions.isEmpty()) {
            logger.warn("未找到目标文字：{}", targetText);
            return null;
        }

        if (matchIndex > positions.size()) {
            logger.warn("匹配索引超出范围：{} > {}",
                       matchIndex, positions.size());
            matchIndex = positions.size();
        }

        int matchPos = positions.get(matchIndex - 1);
        int endPos = matchPos + targetText.length();

        // 映射到 Run 元素
        return mapPositionToRuns(runInfos, matchPos, endPos);
    }

    /**
     * 查找所有匹配位置
     */
    private List<Integer> findMatches(String text, String pattern,
                                      String matchType) {
        List<Integer> positions = new ArrayList<>();

        if ("EXACT".equalsIgnoreCase(matchType)) {
            // 精确匹配
            int index = 0;
            while ((index = text.indexOf(pattern, index)) != -1) {
                positions.add(index);
                index += pattern.length();
            }
        } else if ("CONTAINS".equalsIgnoreCase(matchType)) {
            // 包含匹配（同精确）
            int index = 0;
            while ((index = text.indexOf(pattern, index)) != -1) {
                positions.add(index);
                index += 1;
            }
        } else if ("REGEX".equalsIgnoreCase(matchType)) {
            // 正则匹配
            Pattern regex = Pattern.compile(pattern);
            var matcher = regex.matcher(text);
            while (matcher.find()) {
                positions.add(matcher.start());
            }
        }

        return positions;
    }

    /**
     * 获取段落中所有 Run 元素
     */
    public List<Element> getRuns(Element paragraph) {
        return paragraph.elements(QName.get("r", W_NS));
    }

    /**
     * 提取 Run 中的文字
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
     * 将全局位置映射到 Run 元素
     */
    private TextMatchResult mapPositionToRuns(List<RunInfo> runInfos,
                                               int startPos, int endPos) {
        TextMatchResult result = new TextMatchResult();
        result.setStartPosition(startPos);
        result.setEndPosition(endPos);

        // 找到起始 Run
        for (RunInfo info : runInfos) {
            if (startPos >= info.startPos && startPos < info.endPos) {
                result.setStartRun(info.run);
                result.setStartOffsetInRun(startPos - info.startPos);
                break;
            }
        }

        // 找到结束 Run
        for (RunInfo info : runInfos) {
            if (endPos > info.startPos && endPos <= info.endPos) {
                result.setEndRun(info.run);
                result.setEndOffsetInRun(endPos - info.startPos);
                break;
            }
        }

        return result;
    }

    /**
     * 内部类：Run 信息
     */
    private static class RunInfo {
        Element run;
        int startPos;
        int endPos;
        String text;

        RunInfo(Element run, int startPos, int endPos, String text) {
            this.run = run;
            this.startPos = startPos;
            this.endPos = endPos;
            this.text = text;
        }
    }
}

/**
 * 文字匹配结果
 */
class TextMatchResult {
    private Element startRun;
    private Element endRun;
    private int startOffsetInRun;
    private int endOffsetInRun;
    private int startPosition;
    private int endPosition;

    // Getters/Setters...
}
```

### Step 3: 修改 WordXmlCommentProcessor

```java
// 在 addCommentForIssue 方法中
private boolean addCommentForIssue(Document documentXml, Document commentsXml,
                                  ReviewIssue issue, String anchorStrategy) {
    try {
        // 1. 找到目标段落
        Element targetParagraph = findTargetParagraph(documentXml,
                                                      issue, anchorStrategy);
        if (targetParagraph == null) {
            return false;
        }

        // 2. 如果提供了 targetText，进行精确文字匹配
        Element startRun = null, endRun = null;
        if (issue.getTargetText() != null &&
            !issue.getTargetText().isEmpty()) {

            TextMatchResult matchResult =
                preciseLocator.findTextInParagraph(
                    targetParagraph,
                    issue.getTargetText(),
                    issue.getMatchPattern(),
                    issue.getMatchIndex() != null ?
                        issue.getMatchIndex() : 1);

            if (matchResult == null) {
                logger.warn("文字匹配失败：{}", issue.getTargetText());
                // 降级处理：使用整段批注
                startRun = null;
            } else {
                startRun = matchResult.getStartRun();
                endRun = matchResult.getEndRun();
            }
        }

        // 3. 生成批注ID
        int commentId = commentIdCounter.getAndIncrement();

        // 4. 插入批注标记
        if (startRun != null) {
            // 精确位置批注
            insertPreciseCommentRange(targetParagraph, startRun,
                                     endRun, commentId);
        } else {
            // 段落级别批注（降级）
            insertCommentRangeInDocument(targetParagraph, commentId);
        }

        // 5. 在 comments.xml 中添加批注内容
        addCommentToCommentsXml(commentsXml, commentId, issue);

        logger.debug("成功添加批注：commentId={}, clauseId={}",
                    commentId, issue.getClauseId());
        return true;

    } catch (Exception e) {
        logger.error("添加批注失败", e);
        return false;
    }
}

/**
 * 精确位置批注标记插入
 */
private void insertPreciseCommentRange(Element paragraph,
                                       Element startRun, Element endRun,
                                       int commentId) {
    // 在段落开始插入起始标记
    Element commentRangeStart = paragraph.addElement(
        QName.get("commentRangeStart", W_NS));
    commentRangeStart.addAttribute(QName.get("id", W_NS),
                                   String.valueOf(commentId));

    // 在结束 Run 后插入结束标记
    Element commentRangeEnd = paragraph.addElement(
        QName.get("commentRangeEnd", W_NS));
    commentRangeEnd.addAttribute(QName.get("id", W_NS),
                                 String.valueOf(commentId));

    // 添加批注引用
    Element run = paragraph.addElement(QName.get("r", W_NS));
    Element commentReference = run.addElement(
        QName.get("commentReference", W_NS));
    commentReference.addAttribute(QName.get("id", W_NS),
                                  String.valueOf(commentId));

    logger.debug("插入精确位置批注：commentId={}", commentId);
}
```

---

## 💡 使用示例

### API 调用示例

```json
{
  "issues": [
    {
      "clauseId": "c1",
      "anchorId": "anc-c1-xxxx",
      "severity": "HIGH",
      "category": "保密条款",
      "finding": "缺少具体定义",
      "suggestion": "应明确保密信息的范围",

      "targetText": "双方应对涉及商业机密的资料予以保密",
      "matchPattern": "EXACT",
      "matchIndex": 1
    }
  ]
}
```

### 修改后的批注流程

```
1. 接收审查结果 JSON
   ↓
2. 通过 anchorId 定位段落 ✓
   ↓
3. 通过 targetText 在段落中查找精确文字
   ↓
4. 在匹配文字处插入批注标记（精确位置）
   ↓
5. 生成 comments.xml 中的批注内容
   ↓
6. 返回带精确批注的 DOCX 文件
```

---

## 📊 技术对比

| 方面 | 旧方式 | 新方式 |
|------|--------|--------|
| **批注粒度** | 段落级别 | 文字级别 |
| **定位精度** | 整段标记 | 精确文字 |
| **适用场景** | 问题涉及整段 | 问题针对具体文字 |
| **用户体验** | 批注区域宽泛 | 批注精确指向 |
| **实现复杂度** | 低 | 中等 |

---

## 🚀 实现优先级

### Phase 1（必须）：
- [ ] 扩展 ReviewIssue 模型
- [ ] 创建 PreciseTextAnnotationLocator
- [ ] 修改 WordXmlCommentProcessor

### Phase 2（可选）：
- [ ] 支持多个匹配结果选择
- [ ] 支持正则表达式匹配
- [ ] 缓存匹配结果提升性能

---

## ✅ 验证方案

修改后应该能够：

1. ✅ 精确匹配文字
2. ✅ 在准确位置插入批注
3. ✅ 支持多种匹配模式（精确、包含、正则）
4. ✅ 处理边界情况（文字跨多个 Run）
5. ✅ 降级处理（文字未找到时使用段落级别）

---

这是完整的修改方案。你想要：
1. **立即实现**这个方案？
2. **讨论具体细节**（如 API 设计等）？
3. **创建分步实现计划**？

请告诉我你的选择！

