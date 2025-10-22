# ChatGPT 集成模块 - 快速参考

**版本**: 2.1.0
**用途**: API 快速查询和使用

---

## 🚀 三行概括

```
1. POST /chatgpt/generate-prompt        ← 解析合同、生成 Prompt
2. [用户手动] 复制 Prompt 到 ChatGPT → 获得审查结果 JSON
3. POST /chatgpt/import-result-xml      ← 导入审查结果、生成批注
```

---

## 📡 API 端点一览

| 端点 | 方法 | 功能 | 返回 | 推荐 |
|------|------|------|------|------|
| `/chatgpt/generate-prompt` | POST | 解析 + 生成 Prompt | JSON + Prompt 文本 | ✅ |
| `/chatgpt/import-result-xml` | POST | 导入审查结果（XML 方式） | .docx 文件 | ✅ 推荐 |
| `/chatgpt/import-result` | POST | 导入审查结果（兼容） | .docx 文件 | ✓ 可用 |
| `/chatgpt/workflow` | POST | 一键流程（步骤 1 或 2） | JSON 或 .docx | - |
| `/chatgpt/status` | GET | 获取状态和指导 | JSON 信息 | ✓ 可用 |

---

## 🔄 工作流速记

```
Input:  contract.docx
   ↓
POST /generate-prompt
   ↓
Output: { chatgptPrompt, parseResult }
   ↓
[User copies Prompt to ChatGPT]
   ↓
[User gets review JSON with targetText]
   ↓
Input:  review.json
   ↓
POST /import-result-xml
   ↓
Output: annotated.docx (with comments)
```

---

## 📝 请求模板

### 1️⃣ 生成 Prompt

```bash
curl -X POST "http://localhost:8080/chatgpt/generate-prompt" \
  -F "file=@contract.docx" \
  -F "contractType=通用合同" \
  -F "anchors=generate"
```

**参数简表**:
- `file`: 合同 DOCX 文件
- `contractType`: 合同类型（默认："通用合同"）
- `anchors`: "generate" 或 "regenerate"（默认：generate）

**返回字段重点**:
- `chatgptPrompt`: 复制给 ChatGPT 的完整提示文本
- `parseResult.clauses`: 条款列表（包含 anchorId、startParaIndex）

---

### 2️⃣ 导入审查结果

```bash
curl -X POST "http://localhost:8080/chatgpt/import-result-xml" \
  -F "file=@contract.docx" \
  -F "chatgptResponse=@review.json" \
  -F "anchorStrategy=preferAnchor" \
  -F "cleanupAnchors=true" \
  -o output.docx
```

**参数简表**:
- `file`: 原始合同文件
- `chatgptResponse`: ChatGPT 返回的 JSON（**必须包含 issues 数组**）
- `anchorStrategy`: `preferAnchor`（推荐）| `anchorOnly` | `textFallback`
- `cleanupAnchors`: `true`（推荐清理） | `false`（保留锚点）

**返回**: 带批注的 .docx 文件

---

## 📤 JSON 格式速记

### 输入：ChatGPT 审查 JSON

```json
{
  "issues": [
    {
      "clauseId": "c1",
      "anchorId": "anc-c1-4f21",
      "severity": "HIGH",
      "category": "分类名",
      "finding": "发现的问题",
      "suggestion": "修改建议",
      "targetText": "【关键】要批注的精确文字",
      "matchPattern": "EXACT"
    }
  ],
  "summary": {
    "totalIssues": 1,
    "highRisk": 1,
    "mediumRisk": 0,
    "lowRisk": 0,
    "recommendation": "总体建议"
  }
}
```

**关键字段说明**:
- `targetText`: **必须从原文逐字逐句复制**，不能改写或创造
- `matchPattern`: `EXACT`（精确）| `CONTAINS`（包含）| `REGEX`（正则）
- `anchorId`: 来自 `/generate-prompt` 的 `parseResult.clauses[].anchorId`
- `severity`: `HIGH`（高风险）| `MEDIUM`（中风险）| `LOW`（低风险）

---

## ⚡ 常用场景

### 场景 1: 审查新合同

```bash
# 步骤 1：生成 Prompt
curl -X POST "http://localhost:8080/chatgpt/generate-prompt" \
  -F "file=@new_contract.docx" \
  -F "contractType=技术合作" \
  | jq '.chatgptPrompt' > prompt.txt

# 步骤 2：[用户手动] 复制 prompt.txt 到 ChatGPT，获得 review.json

# 步骤 3：导入审查结果
curl -X POST "http://localhost:8080/chatgpt/import-result-xml" \
  -F "file=@new_contract.docx" \
  -F "chatgptResponse=@review.json" \
  -o new_contract_reviewed.docx
```

### 场景 2: 增量审查（保留锚点）

```bash
# 首次审查：保留锚点
curl -X POST "http://localhost:8080/chatgpt/import-result-xml" \
  -F "file=@contract.docx" \
  -F "chatgptResponse=@review1.json" \
  -F "cleanupAnchors=false" \
  -o contract_v1.docx

# 修改后再审：锚点仍然有效
curl -X POST "http://localhost:8080/chatgpt/import-result-xml" \
  -F "file=@contract_v1.docx" \
  -F "chatgptResponse=@review2.json" \
  -F "cleanupAnchors=false" \
  -o contract_v2.docx
```

---

## ✅ 错误排查

| 错误 | 原因 | 解决 |
|------|------|------|
| `targetText` 不匹配 | 文字与原文不同 | 从 Prompt 中原文精确复制 |
| 批注位置错误 | 索引混乱 | 使用新方法 `extractClausesWithCorrectIndex()` |
| `anchorId` 未找到 | 生成时未启用锚点 | 使用 `anchors=generate` 重新生成 |
| JSON 格式错误 | 缺少必需字段 | 检查是否有 `issues` 数组 |

---

## 🎯 参数值速查

### contractType

- `通用合同`、`技术合作`、`劳动合同`、`租赁合同`、`购销合同`、`保密协议`

### severity

- `HIGH` = 高风险（🔴）
- `MEDIUM` = 中风险（🟡）
- `LOW` = 低风险（🟢）

### matchPattern

- `EXACT` = 精确匹配（推荐 ⭐⭐⭐⭐⭐）
- `CONTAINS` = 关键词包含（⭐⭐⭐）
- `REGEX` = 正则表达式（⭐⭐⭐⭐）

### anchorStrategy

| 策略 | 说明 | 场景 |
|------|------|------|
| `preferAnchor` | 优先锚点，次选条款ID | 推荐，最平衡 |
| `anchorOnly` | 仅用锚点 | 对准确性要求高 |
| `textFallback` | 优先锚点，失败用文字 | 最大化覆盖率 |

---

## 📊 返回码速查

| 码 | 说明 |
|----|------|
| 200 | ✅ 成功 |
| 400 | ❌ 请求错误（参数不合法） |
| 500 | ❌ 服务器错误 |

---

## 🔗 相关链接

- **完整规范**: `CHATGPT_API_COMPLETE_SPECIFICATION.md`
- **升级说明**: `XML_ANNOTATION_UPGRADE_SUMMARY.md`
- **迁移指南**: `POI_TO_XML_MIGRATION_GUIDE.md`
- **项目说明**: `CLAUDE.md`

---

**最后更新**: 2025-10-21 | **版本**: 2.1.0 XML 方式
