# 甲乙方信息提取 - 文件解析优化方案

**问题识别日期**：2025-10-24 16:29
**问题现象**：文件解析时忽略了甲乙方信息行，导致提取"未明确提及的公司名称"

---

## 📋 问题分析

### 现象
从日志可以看到：
```
DEBUG: 段落不符合条款标题规则: '签订日期：2025-10-15'
DEBUG: 段落不符合条款标题规则: '甲方（委托方）：华南科技有限公司'
DEBUG: 段落不符合条款标题规则: '乙方（受托方）：智创信息技术有限公司'
```

这些包含甲乙方信息的行被判定为"不符合条款标题规则"，被**忽略**了。

### 根本原因

**地点**：`DocxUtils.java` 的 `isClauseTitle()` 方法（第 550-589 行）

**规则**：只有符合以下规则的段落才被识别为"条款标题"：
1. 纯数字格式（1、2、3）
2. 阿拉伯数字带符号（1.、1）
3. 罗马数字（I、II、III）
4. 中文数字（一、二、三）
5. 条款关键词
6. 冒号结尾的短文本
7. 全大写英文标题

**问题**：`甲方（委托方）：华南科技有限公司` 和 `乙方（受托方）：智创信息技术有限公司` 这样的格式**不符合任何规则**，因此被忽略。

### 影响链路

```
Word 文档
    ↓
DocxUtils.parseDocumentElements()
    ↓
isClauseTitle() 过滤
    ├─ 甲方信息 → 被过滤掉 ✗
    ├─ 乙方信息 → 被过滤掉 ✗
    └─ 其他条款 → 保留 ✓
    ↓
ParseResult (缺少甲乙方信息)
    ↓
前端调用 /api/review/extract-parties
    ↓
contractText (不包含甲乙方) → 传给 Qwen
    ↓
Qwen (收不到甲乙方信息)
    ↓
返回："未明确提及的公司名称" ✗
```

---

## 🎯 解决方案

### 核心思路

**不修改现有条款解析逻辑**，而是：

1. **保留条款标题识别不变** - 避免影响现有功能
2. **单独识别甲乙方信息行** - 在解析时特殊处理
3. **将甲乙方信息合并到合同文本** - 确保 Qwen 能看到
4. **不影响条款结构** - 甲乙方信息作为"前言"保留

### 具体步骤

#### 方案 A：在合同解析时识别甲乙方（推荐）✅

**位置**：`ContractParseService.java`

**步骤**：
```java
public ParseResult parseContract(MultipartFile file, String anchorMode) throws IOException {

    // 1. 解析所有段落（包括甲乙方）
    List<String> allParagraphs = docxUtils.parseAllParagraphs(doc);

    // 2. 识别甲乙方信息行
    String partyALine = null;
    String partyBLine = null;
    for (String para : allParagraphs) {
        if (para.contains("甲方") || para.contains("买方") || para.contains("委托方")) {
            partyALine = para;
        }
        if (para.contains("乙方") || para.contains("卖方") || para.contains("受托方")) {
            partyBLine = para;
        }
    }

    // 3. 提取合同完整文本（包括甲乙方）
    StringBuilder fullText = new StringBuilder();
    if (partyALine != null) fullText.append(partyALine).append("\n");
    if (partyBLine != null) fullText.append(partyBLine).append("\n");
    for (String para : allParagraphs) {
        if (!para.equals(partyALine) && !para.equals(partyBLine)) {
            fullText.append(para).append("\n");
        }
    }

    // 4. 存储完整合同文本供后续使用
    parseResult.setFullContractText(fullText.toString());

    // 5. 现有的条款解析逻辑保持不变
    List<Clause> clauses = docxUtils.extractClauses(doc);
    parseResult.setClauses(clauses);

    return parseResult;
}
```

**优势**：
- ✅ 不修改现有条款识别逻辑
- ✅ 特殊处理甲乙方信息
- ✅ 确保 Qwen 能收到完整信息
- ✅ 后续扩展空间大

---

#### 方案 B：增强条款标题识别规则（备选）

**位置**：`DocxUtils.java` 的 `isClauseTitle()` 方法

**修改**：添加规则 8
```java
// 规则8：甲乙方信息（保留给后续提取）
String[] partyKeywords = {"甲方", "乙方", "买方", "卖方", "委托方", "受托方",
                          "承包人", "供应商", "承接方", "受托方", "发包人"};
for (String keyword : partyKeywords) {
    if (normalizedText.contains(keyword) && normalizedText.contains("：")) {
        // 这是甲乙方信息行，保留但标记为特殊类型
        logger.info("发现甲乙方信息行: {}", normalizedText);
        return true;
    }
}
```

**注意**：这种方法会将甲乙方作为条款的一部分，可能影响现有逻辑

---

#### 方案 C：在提取文本时补充甲乙方信息（简单快速）✅

**位置**：`PartyExtractionService.java` 的 `extractPartyInfoWithQwen()` 方法

**步骤**：
```java
public PartyExtractionResponse extractPartyInfoWithQwen(PartyExtractionRequest request) {
    // 改进：确保合同文本包含甲乙方信息
    String enrichedText = enrichContractWithPartyInfo(request.getContractText());

    // 构建新的请求，使用丰富后的文本
    PartyExtractionRequest enrichedRequest = PartyExtractionRequest.builder()
        .contractText(enrichedText)  // 包含甲乙方信息的完整文本
        .contractType(request.getContractType())
        .parseResultId(request.getParseResultId())
        .build();

    // 后续流程保持不变...
}

private String enrichContractWithPartyInfo(String contractText) {
    // 如果原文本不包含甲乙方信息，尝试从特定位置提取
    // 这样可以处理解析遗漏的情况

    // 可选：检查是否需要补充信息
    if (!contractText.contains("甲方") && !contractText.contains("乙方")) {
        // 使用启发式方法推断
        logger.warn("合同文本中未找到甲乙方标识，Qwen 可能需要推断");
    }

    return contractText;
}
```

---

## 🚀 推荐实施方案

### 立即实施（优先级：高）
**方案 A：在 `ContractParseService` 中识别甲乙方**

```java
// 在 parseContract() 方法中添加
public ParseResult parseContract(MultipartFile file, String anchorMode) throws IOException {
    // ... 现有代码 ...

    List<DocumentElement> allElements = docxUtils.parseDocumentElements(doc);

    // 识别甲乙方信息行
    PartyInfo partyInfo = extractPartyInfoFromElements(allElements);

    // 构建包含甲乙方的完整文本
    String fullContractText = buildFullContractText(allElements, partyInfo);

    // 存储到 ParseResult
    parseResult.setFullContractText(fullContractText);
    parseResult.setPartyA(partyInfo.getPartyA());
    parseResult.setPartyB(partyInfo.getPartyB());

    // 现有条款提取逻辑保持不变
    List<Clause> clauses = docxUtils.extractClauses(doc);
    parseResult.setClauses(clauses);

    return parseResult;
}

private PartyInfo extractPartyInfoFromElements(List<DocumentElement> elements) {
    PartyInfo info = new PartyInfo();

    for (DocumentElement elem : elements) {
        if (elem.getType() == DocumentElement.Type.PARAGRAPH) {
            String text = elem.getText();

            // 识别甲方（支持多种标签）
            if (text.matches("^\\s*(甲方|买方|委托方|需方|发包人|客户).*[:：].*")) {
                info.setPartyALine(text);
                // 简单提取公司名称（冒号后）
                String[] parts = text.split("[:：]");
                if (parts.length > 1) {
                    info.setPartyA(parts[1].trim());
                }
            }

            // 识别乙方
            if (text.matches("^\\s*(乙方|卖方|受托方|供方|承包人|服务商).*[:：].*")) {
                info.setPartyBLine(text);
                String[] parts = text.split("[:：]");
                if (parts.length > 1) {
                    info.setPartyB(parts[1].trim());
                }
            }
        }
    }

    return info;
}

private String buildFullContractText(List<DocumentElement> elements, PartyInfo partyInfo) {
    StringBuilder text = new StringBuilder();

    // 将甲乙方信息放在开头
    if (partyInfo.getPartyALine() != null) {
        text.append(partyInfo.getPartyALine()).append("\n");
    }
    if (partyInfo.getPartyBLine() != null) {
        text.append(partyInfo.getPartyBLine()).append("\n");
    }

    // 后续是其他内容
    for (DocumentElement elem : elements) {
        if (elem.getType() == DocumentElement.Type.PARAGRAPH) {
            text.append(elem.getText()).append("\n");
        }
    }

    return text.toString();
}
```

### 后续改进（优先级：中）

1. **增强前端**：显示识别到的甲乙方信息
2. **缓存优化**：缓存识别结果避免重复调用
3. **错误处理**：当本地识别失败时，让 Qwen 从完整文本中识别

---

## 📊 方案对比

| 方案 | 优点 | 缺点 | 复杂度 | 风险 |
|------|------|------|--------|------|
| **A: 在解析时识别** | ✅ 不影响现有逻辑<br/>✅ 收集完整信息<br/>✅ 易于扩展 | 需要修改 ParseResult 结构 | 中 | 低 |
| **B: 增强条款规则** | ✅ 修改最小 | ⚠️ 可能影响现有逻辑<br/>⚠️ 维护困难 | 低 | 中 |
| **C: 文本补充** | ✅ 改动最小<br/>✅ 快速实施 | ⚠️ 治标不治本 | 低 | 低 |

---

## 🛠️ 实施检查清单

- [ ] 创建 `PartyInfo` 模型类
- [ ] 修改 `ParseResult` 添加 `fullContractText`、`partyA`、`partyB` 字段
- [ ] 在 `ContractParseService` 中添加甲乙方识别逻辑
- [ ] 修改 `PartyExtractionService`，使用 `fullContractText` 代替 `contractText`
- [ ] 修改前端，显示识别的甲乙方信息
- [ ] 编译验证
- [ ] 单元测试
- [ ] 集成测试

---

## ✅ 预期效果

### 改进前
```
输入：Word 文件（包含甲方、乙方、条款等）
    ↓
解析失败（甲乙方被过滤）
    ↓
Qwen 提取失败（缺少关键信息）
    ↓
返回："未明确提及的公司名称"
```

### 改进后
```
输入：Word 文件（包含甲方、乙方、条款等）
    ↓
解析成功（保留甲乙方信息）
    ↓
Qwen 提取成功（有完整信息）
    ↓
返回："华南科技有限公司"、"智创信息技术有限公司"
```

---

## 📝 代码示例：新增的 PartyInfo 类

```java
package com.example.Contract_review.model;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class PartyInfo {
    private String partyA;              // 甲方公司名称
    private String partyB;              // 乙方公司名称
    private String partyALine;          // 甲方完整信息行
    private String partyBLine;          // 乙方完整信息行
    private String partyARoleName;      // 甲方角色标签
    private String partyBRoleName;      // 乙方角色标签
}
```

---

**建议**：实施方案 A，既保留现有逻辑，又能准确识别甲乙方信息。

是否同意该方案？如是，我将开始实施代码修改。
