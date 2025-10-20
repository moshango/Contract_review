# 精确文字级别批注系统使用指南

## 概述

本系统实现了**精确文字级别的批注功能**，相比传统的段落级别批注，能够精确定位到段落内的具体文字并在其处插入批注。这使得批注更加准确和有针对性。

## 功能对比

| 特性 | 传统方式（段落级别） | 新方式（文字级别） |
|------|-----------------|------------------|
| **批注粒度** | 整个段落 | 具体文字 |
| **定位精度** | 低，覆盖整段 | 高，精确到文字 |
| **用户体验** | 批注区域宽泛 | 批注精确指向 |
| **API调用** | 无需提供targetText | 提供targetText获得精确批注 |
| **向后兼容** | ✓ | ✓ 完全兼容 |

## API 使用

### 批注请求格式

#### 传统方式（段落级别 - 向后兼容）

```json
{
  "issues": [
    {
      "anchorId": "anc-c1-xxxx",
      "clauseId": "c1",
      "severity": "HIGH",
      "category": "保密条款",
      "finding": "缺少具体定义",
      "suggestion": "应明确保密信息的范围"
    }
  ]
}
```

#### 新方式（文字级别 - 推荐）

```json
{
  "issues": [
    {
      "anchorId": "anc-c1-xxxx",
      "clauseId": "c1",
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

### 参数说明

#### 精确文字匹配相关参数

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|------|------|------|--------|------|
| `targetText` | String | 否 | null | 要批注的具体文字内容 |
| `matchPattern` | String | 否 | "EXACT" | 匹配模式：EXACT(精确)、CONTAINS(包含)、REGEX(正则) |
| `matchIndex` | Integer | 否 | 1 | 如果有多个匹配，选择第几个（1-based） |

### 匹配模式说明

#### 1. EXACT（精确匹配 - 推荐）
完全匹配指定的文字，不允许部分匹配。

```json
{
  "targetText": "双方应对涉及商业机密的资料予以保密",
  "matchPattern": "EXACT"
}
```

**特点**：
- 最常用和安全的模式
- 文字必须完全相同
- 高准确度

#### 2. CONTAINS（包含匹配）
允许部分匹配，只要包含指定文字即可。

```json
{
  "targetText": "商业机密",
  "matchPattern": "CONTAINS"
}
```

**特点**：
- 更灵活，适合匹配短语
- 可能有多个匹配结果
- 使用matchIndex选择具体匹配

#### 3. REGEX（正则表达式匹配）
使用Java正则表达式进行复杂匹配。

```json
{
  "targetText": "双方应对.{2,10}予以保密",
  "matchPattern": "REGEX"
}
```

**特点**：
- 最灵活，支持复杂模式
- 适合匹配变化的文字
- 需要正则表达式知识

### matchIndex 使用示例

当同一文字在段落中出现多次时，使用matchIndex指定要批注的是第几个。

```text
原文：
"保密信息包括但不限于以下内容：
1. 商业秘密信息
2. 技术秘密信息
3. 财务秘密信息"
```

```json
{
  "targetText": "秘密信息",
  "matchPattern": "CONTAINS",
  "matchIndex": 1  // 匹配第1个"秘密信息"（商业秘密信息）
}
```

使用 `"matchIndex": 2` 则匹配第2个，依此类推。

## 实现细节

### 文字定位流程

```
1. 接收ReviewIssue，获取targetText和matchPattern
   ↓
2. 通过anchorId或clauseId定位目标段落
   ↓
3. PreciseTextAnnotationLocator扫描段落所有Run元素
   ↓
4. 构建完整段落文本和Run元素映射
   ↓
5. 根据matchPattern查找所有匹配位置
   ↓
6. 按matchIndex选择具体匹配
   ↓
7. 映射全局文本位置到具体Run元素及偏移量
   ↓
8. 在精确位置插入批注标记
   ↓
9. 若文字未找到，自动降级到段落级别批注
```

### 核心类说明

#### PreciseTextAnnotationLocator

```java
public class PreciseTextAnnotationLocator {

    /**
     * 在段落中查找目标文字
     */
    public TextMatchResult findTextInParagraph(
        Element paragraph,
        String targetText,
        String matchPattern,
        int matchIndex);
}
```

**功能**：
- 支持多种匹配模式
- 处理文字跨多个Run的情况
- 返回精确的位置信息

#### TextMatchResult

```java
public class TextMatchResult {
    private Element startRun;        // 起始Run元素
    private Element endRun;          // 结束Run元素
    private int startOffsetInRun;    // 在起始Run中的偏移
    private int endOffsetInRun;      // 在结束Run中的偏移
    private int startPosition;       // 全局起始位置
    private int endPosition;         // 全局结束位置
}
```

#### ReviewIssue 扩展字段

```java
public class ReviewIssue {
    // ... 现有字段 ...

    // 新增字段
    private String targetText;              // 要批注的文字
    @Builder.Default
    private String matchPattern = "EXACT";  // 匹配模式
    @Builder.Default
    private Integer matchIndex = 1;         // 匹配索引
}
```

## 使用示例

### 示例1：简单的精确匹配

```bash
curl -X POST "http://localhost:8080/api/annotate-xml?anchorStrategy=preferAnchor&cleanupAnchors=false" \
  -F "file=@contract.docx" \
  -F "review={
    \"issues\": [
      {
        \"anchorId\": \"anc-c2-8f3a\",
        \"clauseId\": \"c2\",
        \"severity\": \"HIGH\",
        \"category\": \"保密条款\",
        \"finding\": \"未定义保密范围\",
        \"suggestion\": \"应明确定义保密信息范围\",
        \"targetText\": \"双方应对商业机密予以保密\",
        \"matchPattern\": \"EXACT\"
      }
    ]
  }" \
  -o annotated.docx
```

### 示例2：包含匹配处理多个位置

```bash
curl -X POST "http://localhost:8080/api/annotate-xml?anchorStrategy=preferAnchor&cleanupAnchors=false" \
  -F "file=@contract.docx" \
  -F "review={
    \"issues\": [
      {
        \"anchorId\": \"anc-c3-xxxx\",
        \"clauseId\": \"c3\",
        \"severity\": \"MEDIUM\",
        \"finding\": \"第1个地方有问题\",
        \"targetText\": \"赔偿责任\",
        \"matchPattern\": \"CONTAINS\",
        \"matchIndex\": 1
      },
      {
        \"anchorId\": \"anc-c3-xxxx\",
        \"clauseId\": \"c3\",
        \"severity\": \"MEDIUM\",
        \"finding\": \"第2个地方有问题\",
        \"targetText\": \"赔偿责任\",
        \"matchPattern\": \"CONTAINS\",
        \"matchIndex\": 2
      }
    ]
  }" \
  -o annotated.docx
```

### 示例3：正则表达式匹配

```bash
curl -X POST "http://localhost:8080/api/annotate-xml" \
  -F "file=@contract.docx" \
  -F "review={
    \"issues\": [
      {
        \"anchorId\": \"anc-c4-xxxx\",
        \"clauseId\": \"c4\",
        \"severity\": \"LOW\",
        \"finding\": \"金额表述不规范\",
        \"targetText\": \"人民币[0-9]+万元\",
        \"matchPattern\": \"REGEX\"
      }
    ]
  }" \
  -o annotated.docx
```

## 常见场景

### 场景1：批注具体责任条款

**需求**：在"甲方需在10个工作日内回复"这句话处插入批注

```json
{
  "targetText": "甲方需在10个工作日内回复",
  "matchPattern": "EXACT",
  "matchIndex": 1
}
```

### 场景2：批注所有违约条款

**需求**：在文档中所有出现"违约"的地方都插入批注

```json
[
  {
    "targetText": "违约",
    "matchPattern": "CONTAINS",
    "matchIndex": 1
  },
  {
    "targetText": "违约",
    "matchPattern": "CONTAINS",
    "matchIndex": 2
  },
  {
    "targetText": "违约",
    "matchPattern": "CONTAINS",
    "matchIndex": 3
  }
]
```

### 场景3：处理动态变化的文字

**需求**：批注任何形如"赔偿金额：xxxx元"的表述

```json
{
  "targetText": "赔偿金额：[^\\s]+元",
  "matchPattern": "REGEX"
}
```

## 降级和容错

系统具有完善的容错机制：

### 1. 文字未找到
当提供的targetText在段落中找不到时，系统会：
- 记录警告日志
- 自动降级到段落级别批注
- 继续处理其他问题，不中断流程

```
日志示例：
WARN: 精确文字匹配失败，降级到段落级别批注：targetText=不存在的文字
```

### 2. 匹配索引超出范围
如果matchIndex超过实际匹配数时：
- 自动调整到最后一个匹配
- 记录提醒日志

```
日志示例：
WARN: 匹配索引超出范围: 5 > 3 个匹配
```

### 3. Run元素定位失败
当跨Run定位失败时：
- 降级到段落级别批注
- 记录错误日志

## 性能考虑

- 精确匹配（EXACT）：最快，O(n) 时间复杂度
- 包含匹配（CONTAINS）：快速，可能有重叠匹配
- 正则匹配（REGEX）：较慢，仅在必要时使用

**建议**：
- 大量批注时优先使用EXACT
- 需要灵活匹配时使用CONTAINS
- 复杂模式才使用REGEX

## 调试技巧

### 1. 检查日志输出

启用DEBUG日志以查看详细的匹配过程：

```properties
logging.level.com.example.Contract_review.util.PreciseTextAnnotationLocator=DEBUG
```

### 2. 验证targetText

确保targetText与文档中的文字完全一致（包括空格、标点）：

```java
// 推荐使用parseContract接口查看实际文字
GET /api/parse?anchors=generate
```

### 3. 多个匹配时调整matchIndex

当文字出现多次时，逐一尝试不同的matchIndex：

```json
{
  "targetText": "同一文字",
  "matchIndex": 1  // 试试 1, 2, 3 等
}
```

## 已知限制

1. **文字大小写敏感**：EXACT和CONTAINS模式区分大小写
2. **不支持跨段落匹配**：targetText必须在同一段落内
3. **正则表达式规范**：必须是有效的Java正则表达式

## 后续功能规划（Phase 2 可选）

- [ ] 支持跨段落文字匹配
- [ ] 文字匹配缓存优化
- [ ] 支持模糊匹配（考虑标点差异）
- [ ] 批量匹配结果预览API
- [ ] 自动提取建议的targetText

## 常见问题

**Q: 为什么找不到我的targetText？**
A: 检查以下几点：
1. 文字是否完全相同（包括空格）
2. 是否使用了错误的匹配模式
3. 文字是否跨越多个段落

**Q: 如何找到正确的anchorId？**
A: 调用 `/api/parse?anchors=generate` 获取包含anchorId的解析结果

**Q: 可以批注同一个位置多次吗？**
A: 可以，每个ReviewIssue会生成单独的批注，可以在同一位置添加多个批注

## 总结

精确文字级别批注系统提供了：
- ✓ 更精确的批注定位
- ✓ 更好的用户体验
- ✓ 完整的向后兼容性
- ✓ 灵活的匹配模式
- ✓ 完善的容错机制

使用这个功能可以大大提升合同审查的专业度和效率！

