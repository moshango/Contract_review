# 批注找不到锚点问题诊断指南

**问题**: 虽然parse生成的文档带有锚点，但批注时仍然找不到锚点
**根本原因**: 审查JSON中**没有包含anchorId字段**

---

## 🔍 问题诊断

### 我刚才已添加的诊断日志

在XmlContractAnnotateService中，现在会输出类似这样的日志：

```
INFO - [Issue 1] clauseId=c1, anchorId=✓ anc-c1-4f21, targetText存在=true, ...
INFO - [Issue 2] clauseId=c2, anchorId=✗ NULL, targetText存在=false, ...
INFO - [Issue 3] clauseId=c20, anchorId=✗ NULL, targetText存在=false, ...
```

**关键判断**:
- 如果anchorId显示 `✗ NULL` → 这就是问题所在！
- 如果anchorId显示 `✓ anc-c20-xxx` → anchorId正确，问题在其他地方

---

## 🎯 完整的信息流

### 第1步：Parse生成锚点

```bash
POST /api/parse?anchors=generate&returnMode=both
```

**输出**:
- JSON结果包含每个条款的 `anchorId`（例如：`anc-c1-4f21`）
- 文档文件包含对应的书签（Word bookmarks）

**JSON示例**:
```json
{
  "clauses": [
    {
      "id": "c1",
      "heading": "第一条",
      "text": "...",
      "anchorId": "anc-c1-4f21",
      "startParaIndex": 5,
      "endParaIndex": 9
    }
  ]
}
```

### 第2步：用户准备审查结果

**问题点** ❌ **这里可能出了问题**：

如果用户只提供 `clauseId` 和 `finding`，而**没有包含** `anchorId`：

```json
{
  "issues": [
    {
      "clauseId": "c1",
      "severity": "HIGH",
      "finding": "问题描述",
      "suggestion": "建议"
      // ❌ 缺少 anchorId！
    }
  ]
}
```

### 第3步：批注时的锚点查找

```
开始查找目标段落：clauseId=c1, anchorId=null, 策略=preferAnchor
 ↓
尝试按anchorId查找... anchorId为null，跳过锚点查找
 ↓
回退到文本匹配... 找不到"第1条"
 ↓
尝试宽松模式... 可能找到，也可能找不到
```

---

## ✅ 解决方案

### 正确的做法

在使用批注前，**必须在审查JSON中包含anchorId**：

```bash
# Step 1: 调用parse获取anchorId
curl -X POST -F "file=@contract.docx" \
  "http://localhost:8080/api/parse?anchors=generate&returnMode=both" \
  -o parsed_contract.docx

# 查看返回的JSON，复制每个条款的anchorId

# Step 2: 准备审查结果JSON，**包含anchorId**
cat > review.json << 'EOF'
{
  "issues": [
    {
      "anchorId": "anc-c1-4f21",        # ✓ 从parse结果复制
      "clauseId": "c1",
      "severity": "HIGH",
      "category": "条款类型",
      "finding": "发现的问题",
      "suggestion": "修改建议",
      "targetText": "要批注的具体文字",  # 可选
      "matchPattern": "EXACT"           # 可选
    },
    {
      "anchorId": "anc-c2-8f3a",        # ✓ 从parse结果复制
      "clauseId": "c2",
      "severity": "MEDIUM",
      "category": "条款类型",
      "finding": "发现的问题",
      "suggestion": "修改建议"
    }
  ]
}
EOF

# Step 3: 调用批注
curl -X POST -F "file=@parsed_contract.docx" \
  -F "review=@review.json" \
  "http://localhost:8080/api/annotate?anchorStrategy=preferAnchor" \
  -o annotated_contract.docx
```

---

## 📋 JSON字段完整说明

### ReviewIssue字段

| 字段 | 必需 | 说明 | 示例 |
|------|------|------|------|
| **anchorId** | 📌 推荐 | 锚点ID，从parse结果复制 | `anc-c1-4f21` |
| **clauseId** | ✓ 必需 | 条款ID | `c1`, `c2` |
| **severity** | ✓ 必需 | 风险等级 | `HIGH`, `MEDIUM`, `LOW` |
| **category** | 可选 | 问题类别 | `保密条款`, `违约条款` |
| **finding** | ✓ 必需 | 发现的问题 | `赔偿责任不清晰` |
| **suggestion** | 可选 | 修改建议 | `应明确赔偿金额` |
| **targetText** | 可选 | 精确文字（仅当需要字级批注时） | `甲方应...` |
| **matchPattern** | 可选 | 匹配模式（EXACT/CONTAINS/REGEX）| `EXACT` |
| **matchIndex** | 可选 | 多个匹配时选择第几个 | `1` |

---

## 🔧 三层定位策略

当使用批注时，系统按以下顺序尝试定位段落：

### 层级1：锚点定位（最精确）✅ **如果anchorId存在**

```
锚点ID (anchorId)
  ↓
Word书签匹配
  ↓
找到精确的段落 ✓
```

**需要**: reviewJSON中有anchorId

### 层级2：文本匹配（备选方案）✅ **如果anchorId为null或不匹配**

```
条款ID (clauseId: c1, c20等)
  ↓
提取数字 (1, 20等)
  ↓
匹配10种条款格式（第1条、(1)、1.、1、等）
  ↓
找到段落 ✓
```

**需要**: reviewJSON中有clauseId

### 层级3：宽松匹配（最后手段）✅ **如果严格模式失败**

```
条款数字 (1, 2, 20等)
  ↓
找段落开头包含此数字的第一段
  ↓
找到段落 ✓
```

**风险**: 可能定位到错误的段落（如果文档格式复杂）

---

## 📊 现在的诊断方法

### 第1步：查看日志中的anchorId状态

运行批注后，查看日志：

```
[Issue 1] clauseId=c1, anchorId=✓ anc-c1-4f21, ...
[Issue 2] clauseId=c2, anchorId=✗ NULL, ...
```

- **✓** 开头：anchorId存在，会使用精确的锚点定位
- **✗** 开头：anchorId为null，会降级到文本匹配

### 第2步：根据anchorId状态判断

**如果全是✗ NULL**：
- 原因：审查JSON中缺少anchorId
- 解决：从parse结果中复制anchorId

**如果混合✓和✗**：
- 原因：部分issue有anchorId，部分没有
- 解决：补充缺失的anchorId

**如果全是✓**：
- anchorId存在，但批注仍然集中在第一段
- 原因：文档中的书签标记可能被损坏或不一致
- 解决：重新生成锚点或检查文档

---

## 🚨 关键点总结

| 问题 | 症状 | 原因 | 解决 |
|------|------|------|------|
| anchorId=NULL | 批注集中在第一段 | 审查JSON缺少anchorId | 从parse结果复制anchorId |
| 文本匹配失败 | 批注都在第一段 | 条款标题格式不匹配 | 使用anchorId或调整标题格式 |
| 书签被损坏 | anchorId正确但无定位 | 文档处理过程中书签丢失 | 重新parse生成锚点 |

---

## 📝 建议的完整工作流

```bash
# 1. 上传并parse，获取anchorId
curl -X POST -F "file=@contract.docx" \
  "http://localhost:8080/api/parse?anchors=generate&returnMode=both" \
  > parse_response.json
# 保存返回的JSON，记下所有anchorId

# 2. 准备批注JSON（FROM parse_response.json复制anchorId）
vim review.json
# 编辑review.json，确保每个issue都有：
# - anchorId（从parse复制）✓
# - clauseId ✓
# - severity ✓
# - finding ✓
# - 其他可选字段

# 3. 执行批注（使用preferAnchor策略）
curl -X POST \
  -F "file=@parsed_contract.docx" \
  -F "review=@review.json" \
  "http://localhost:8080/api/annotate?anchorStrategy=preferAnchor&cleanupAnchors=false" \
  -o annotated_contract.docx

# 4. 检查日志确认anchorId状态
tail -100 app.log | grep "Issue"
```

---

## 🎯 预期的日志输出

**正确配置的情况**:
```
INFO - [Issue 1] clauseId=c1, anchorId=✓ anc-c1-4f21, ...
INFO - [Issue 2] clauseId=c2, anchorId=✓ anc-c2-8f3a, ...
INFO - ✓ 通过锚点找到目标段落：anchorId=anc-c1-4f21, 段落索引=15
INFO - ✓ 通过锚点找到目标段落：anchorId=anc-c2-8f3a, 段落索引=25
INFO - 成功添加批注：commentId=1, clauseId=c1, 方式=精确
INFO - 成功添加批注：commentId=2, clauseId=c2, 方式=精确
```

**问题配置的情况**:
```
INFO - [Issue 1] clauseId=c1, anchorId=✗ NULL, ...
INFO - [Issue 2] clauseId=c2, anchorId=✗ NULL, ...
WARN - ✗ 未找到anchorId对应的书签：anchorId=null, ...
INFO - 锚点查找失败，回退到文本匹配
DEBUG - 使用第一个段落作为fallback
```

---

## 💡 核心要点

**记住这一点**：

> 如果你使用 `parse?anchors=generate` 生成的文档进行批注，
> 那么在准备审查JSON时，**必须从parse返回的JSON中复制anchorId**。
>
> 否则系统无法进行精确的锚点定位，会降级到文本匹配，
> 这样可能导致定位不准或全部堆在第一段。

---

**修复完成日期**: 2025-10-20 14:30
**状态**: ✅ 编译通过，等待用户验证日志
