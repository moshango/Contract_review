# Word精确批注实现指南 - 按文字选定位置批注

**创建时间**: 2025-10-20
**目标**: 实现如效果演示.png所示的精确批注效果（关联到具体文字，而非文尾）

---

## 🎯 效果对比

### ❌ 当前效果（不理想）
```
正文: 甲方应在损害事实发生后30天内承担赔偿责任。

批注效果: 批注直接插在文尾或整段
（没有关联到具体文字）
```

### ✅ 目标效果（期望）
```
正文: 甲方应在损害事实发生后30天内承担赔偿责任。
                           ↑↑↑↑↑↑↑↑↑↑↑↑↑ (文字被选中/高亮)

批注: [高风险] 赔偿责任: 时限设定不合理...
      （批注清楚地关联到"30天"这个文字）
```

---

## 📋 三层批注定位架构

### 第1层：锚点定位（Anchor Positioning）✅ 已实现
- 通过anchorId查找条款所在的段落
- 例如：anchorId="anc-c1-4f21" → 找到第一条款

### 第2层：精确文字匹配（Precise Text Matching）✅ 已实现
- 在定位到的段落中，查找具体的targetText
- 支持3种匹配模式：
  - **EXACT**：完全匹配
  - **CONTAINS**：包含匹配（用于关键词）
  - **REGEX**：正则表达式匹配

### 第3层：精确Run级别插入（Precise Annotation Insertion）✅ 已实现
- 在匹配的文字前后插入批注标记
- Word会自动高亮选定的文字并显示批注

---

## 🔧 如何实现精确批注

### 第1步：准备完整的JSON，包含targetText

**关键字段说明**：

```json
{
  "clauseId": "c1",
  "anchorId": "anc-c1-4f21",

  // ✅ 这是关键：指定要批注的具体文字
  "targetText": "30天内承担赔偿责任",

  // 匹配模式：EXACT | CONTAINS | REGEX
  "matchPattern": "EXACT",

  // 如果文字出现多次，选择第几个（1-based）
  "matchIndex": 1,

  "finding": "发现的问题",
  "suggestion": "修改建议",
  "severity": "HIGH"
}
```

### 第2步：使用已提供的annotate_PRECISE.json

文件位置：`annotate_PRECISE.json`

**特点**：
- ✅ 所有9个issue都包含targetText
- ✅ targetText精确指向要批注的核心文字
- ✅ matchPattern根据场景选择EXACT或CONTAINS

### 第3步：调用批注API

```bash
curl -X POST "http://localhost:8080/annotate?anchorStrategy=preferAnchor&cleanupAnchors=false" \
  -F "file=@parsed-测试合同_综合测试版.docx" \
  -F "review=@annotate_PRECISE.json" \
  -o 精确批注结果.docx
```

**关键参数**：
- `anchorStrategy=preferAnchor`：优先用anchorId定位
- `cleanupAnchors=false`：保留锚点标记（便于后续调试）

### 第4步：验证效果

使用Word打开生成的文档，检查：
- ✅ 批注是否关联到具体文字
- ✅ 文字是否被高亮
- ✅ 右侧批注栏是否显示批注内容

---

## 📝 targetText最佳实践

### 原则1：精准指定关键词

**❌ 不好**：
```json
{
  "targetText": "甲方应在损害事实发生后30天内承担赔偿责任，若逾期则需支付违约金。",
  "finding": "时限设定不清晰"
}
```
问题：太长，整句都会被批注

**✅ 好**：
```json
{
  "targetText": "30天",
  "matchPattern": "CONTAINS",
  "finding": "时限设定不清晰，建议改为15天"
}
```
问题：只有"30天"被批注

---

### 原则2：选择合适的匹配模式

| matchPattern | 适用场景 | 例子 |
|---|---|---|
| **EXACT** | 完整短语唯一 | "五、保密与数据安全" |
| **CONTAINS** | 关键词出现多次 | "全部经济损失"（只高亮这个短语） |
| **REGEX** | 复杂模式匹配 | `\d{1,3}天内` 匹配"30天内" |

---

### 原则3：处理多个匹配

如果关键词在段落中出现多次：

**例1**：在条款中出现3个"30天"
```json
{
  "targetText": "30天",
  "matchPattern": "CONTAINS",
  "matchIndex": 1,  // 批注第1个"30天"
  "finding": "第一个30天时限不合理"
}
```

**例2**：如果要批注第2个"30天"
```json
{
  "targetText": "30天",
  "matchPattern": "CONTAINS",
  "matchIndex": 2,  // 批注第2个"30天"
  "finding": "第二个30天时限不合理"
}
```

---

### 原则4：考虑换行符

某些JSON中的targetText包含换行：

```json
{
  "targetText": "- 首付款：合同签订后7个工作日内支付30%;\n- 中期款：第二阶段通过验收后支付40%",
  "matchPattern": "EXACT"
}
```

系统会自动处理`\n`，但建议：
- 如果是多行内容，考虑使用CONTAINS匹配关键部分
- 或者分解成多个issue，每个针对一行

---

## 🧪 测试步骤

### 测试方案1：快速验证（5分钟）

```bash
# 使用提供的annotate_PRECISE.json
curl -X POST "http://localhost:8080/annotate?anchorStrategy=preferAnchor" \
  -F "file=@parsed-测试合同_综合测试版.docx" \
  -F "review=@annotate_PRECISE.json" \
  -o test_precise.docx

# 用Word打开test_precise.docx
# 检查右侧批注是否关联到具体文字
```

**预期结果**：
- ✅ 9个批注都应该正确定位
- ✅ 每个批注都关联到指定的targetText
- ✅ 文字应该被Word自动高亮/选中

---

### 测试方案2：逐个验证（15分钟）

创建test_single_issue.json，每次只测试一个issue：

```json
{
  "issues": [
    {
      "clauseId": "c16",
      "anchorId": "anc-c16-2dab",
      "targetText": "全部经济损失",
      "matchPattern": "CONTAINS",
      "finding": "赔偿上限不清晰",
      "suggestion": "应明确赔偿上限",
      "severity": "MEDIUM",
      "category": "违约责任"
    }
  ]
}
```

运行批注：
```bash
curl -X POST "http://localhost:8080/annotate?anchorStrategy=preferAnchor" \
  -F "file=@parsed-测试合同_综合测试版.docx" \
  -F "review=@test_single_issue.json" \
  -o test_single.docx
```

查看日志，应该看到：
```
INFO - [Issue 1] clauseId=c16, anchorId=✓ anc-c16-2dab, targetText存在=true
INFO - 使用精确文字匹配插入批注：文字=全部经济损失, 起始Run=✓, 结束Run=✓
INFO - 成功添加批注：commentId=1, clauseId=c16, 方式=精确
```

---

### 测试方案3：调试不匹配（如果出问题）

如果日志显示文字没有匹配上：

```
WARN - 精确文字匹配失败，降级到段落级别批注：targetText=xxx
```

**排查步骤**：

```bash
# 1️⃣ 提取实际文档中的文字
unzip -p parsed-测试合同_综合测试版.docx word/document.xml > document.xml

# 2️⃣ 搜索你要匹配的文字
grep "全部经济损失" document.xml

# 3️⃣ 如果找不到，说明：
#   - 文字不存在于该段落
#   - 文字可能被分成多个Run（跨越格式标记）
#   - 需要调整targetText的内容

# 4️⃣ 调整策略：
#   - 尝试使用CONTAINS匹配关键词
#   - 或者缩短targetText只匹配核心部分
#   - 或者使用REGEX进行灵活匹配
```

---

## 📊 常见问题解决

### Q1：targetText不匹配怎么办？

**问题现象**：
```
WARN - 精确文字匹配失败
```

**可能原因**：
1. 文字可能被分割到多个Run（中间含格式）
2. targetText有多余的空格或特殊字符
3. 文字不在该段落中

**解决方案**：
```json
{
  "targetText": "全部经济损失",
  "matchPattern": "CONTAINS",  // 改用CONTAINS
  "finding": "赔偿范围不清晰"
}
```

---

### Q2：需要批注多段文字怎么办？

**方案1**：创建多个issue，每个针对一个文字段
```json
{
  "issues": [
    {
      "clauseId": "c1",
      "targetText": "第一个关键词",
      "finding": "问题1"
    },
    {
      "clauseId": "c1",
      "targetText": "第二个关键词",
      "finding": "问题2"
    }
  ]
}
```

**方案2**：使用REGEX匹配多个变体
```json
{
  "targetText": "30天|7天|15天",  // 匹配这三种时限
  "matchPattern": "REGEX",
  "finding": "时限设定不统一"
}
```

---

### Q3：如何确保批注精确度？

**最佳实践**：
1. targetText要**足够具体**（避免过宽泛）
2. 使用**合适的matchPattern**（EXACT最精确）
3. 如果EXACT失败，改用CONTAINS或REGEX
4. 在JSON中明确指定matchIndex（如有多个匹配）

---

### Q4：为什么有些批注还是段落级别？

**原因**：
- targetText未提供
- targetText提供但无法匹配
- matchPattern设置不对

**调试**：
查看日志中是否有：
```
DEBUG - 使用精确文字匹配插入批注  ✅ 精确模式
DEBUG - 精确文字匹配失败，降级到段落级别  ⚠️ 需要调整
```

---

## 🚀 完整工作流

```bash
# 1️⃣ 使用提供的精确批注JSON
使用 annotate_PRECISE.json 进行批注

# 2️⃣ 调用API
curl -X POST "http://localhost:8080/annotate?anchorStrategy=preferAnchor&cleanupAnchors=false" \
  -F "file=@parsed-测试合同_综合测试版.docx" \
  -F "review=@annotate_PRECISE.json" \
  -o result_precise.docx

# 3️⃣ 在Word中打开result_precise.docx
# 右侧应该显示9个批注，每个都关联到具体文字

# 4️⃣ 检查效果
- 批注是否关联到targetText指定的文字？
- 文字是否被Word高亮？
- 批注内容是否完整？

# 5️⃣ 如果满意，可以清理锚点
curl -X POST "http://localhost:8080/annotate?anchorStrategy=preferAnchor&cleanupAnchors=true" \
  -F "file=@parsed-测试合同_综合测试版.docx" \
  -F "review=@annotate_PRECISE.json" \
  -o result_precise_clean.docx
```

---

## 📚 JSON字段完全参考

| 字段 | 必需 | 类型 | 说明 | 例子 |
|------|------|------|------|------|
| **anchorId** | 推荐 | String | 锚点ID | `anc-c1-4f21` |
| **clauseId** | ✓ | String | 条款ID | `c1` |
| **finding** | ✓ | String | 发现的问题 | `时限不清晰` |
| **suggestion** | 可选 | String | 修改建议 | `建议改为15天` |
| **severity** | ✓ | String | 风险等级 | `HIGH/MEDIUM/LOW` |
| **category** | 可选 | String | 问题类别 | `赔偿责任` |
| **targetText** | 可选 | String | 要批注的文字 | `30天` |
| **matchPattern** | 可选 | String | 匹配模式 | `EXACT/CONTAINS/REGEX` |
| **matchIndex** | 可选 | Integer | 第几个匹配 | `1` (默认) |

---

## ✨ 关键特点

### 系统的智能回退机制

```
优先级顺序：
1️⃣ 精确文字匹配 (targetText + matchPattern)
   ↓ 成功 → 使用精确位置批注
   ↓ 失败 ↓
2️⃣ 段落级别批注 (自动降级)
   ↓ 确保系统稳定性
3️⃣ 关联到整个条款段落
```

**优势**：
- 即使文字匹配失败，也不会导致系统崩溃
- 自动降级到段落级别批注
- 用户能看到批注，只是定位不如预期精确

---

## 📌 快速检查清单

在使用annotate_PRECISE.json之前，检查：

- [ ] 所有issue都有anchorId
- [ ] 所有issue都有targetText（可选但推荐）
- [ ] 所有issue都有severity
- [ ] 所有issue都有finding
- [ ] anchorId与文档中的书签一致
- [ ] targetText在对应段落中存在
- [ ] matchPattern选择正确

---

## 🎓 学习资源

- **效果演示**: `效果演示.png`（显示期望的Word批注效果）
- **代码实现**: `WordXmlCommentProcessor.java`（批注逻辑）
- **文字匹配**: `PreciseTextAnnotationLocator.java`（精确匹配算法）
- **模型定义**: `ReviewIssue.java`（JSON字段定义）

---

**更新时间**: 2025-10-20 15:45
**状态**: ✅ 准备就绪
**下一步**: 使用annotate_PRECISE.json进行测试
