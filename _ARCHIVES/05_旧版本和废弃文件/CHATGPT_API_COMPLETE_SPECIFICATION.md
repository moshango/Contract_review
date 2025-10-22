# ChatGPT 集成模块 - 完整 API 端点规范

**版本**: 2.1.0（XML 方式）
**日期**: 2025-10-21
**状态**: ✅ 已实现并验证

---

## 📋 目录

1. [核心工作流](#核心工作流)
2. [API 端点详解](#api-端点详解)
3. [数据模型](#数据模型)
4. [完整工作流示例](#完整工作流示例)
5. [参数参考](#参数参考)
6. [错误处理](#错误处理)

---

## 核心工作流

### 用户需求的理想工作流

```
【用户输入】合同文档
        ↓
[1] /generate-prompt        ← 解析并生成 Prompt
        ↓
【输出】Prompt JSON（包含 ParseResult）
        ↓
【用户操作】复制 Prompt 到 ChatGPT → 获得审查结果 JSON
        ↓
【用户输入】审查结果 JSON
        ↓
[2] /import-result-xml      ← 批注处理
        ↓
【输出】带批注的 .docx 文档（可选清理锚点）
```

### 当前系统完全支持此工作流 ✅

---

## API 端点详解

### 端点 1: `POST /chatgpt/generate-prompt`

**功能**: 解析合同、生成 ChatGPT 提示、返回结构化条款数据

#### 请求

```http
POST /chatgpt/generate-prompt HTTP/1.1
Host: localhost:8080
Content-Type: multipart/form-data

Parameters:
  - file: [DOCX文件] (必填)
  - contractType: string (可选，默认: "通用合同")
  - anchors: string (可选，默认: "generate")
```

#### 请求参数详解

| 参数 | 类型 | 默认值 | 说明 | 选项 |
|------|------|--------|------|------|
| `file` | MultipartFile | - | 合同 DOCX 文件 | 仅支持 `.docx` |
| `contractType` | String | "通用合同" | 合同类型 | "技术合作", "劳动合同", "租赁合同" 等 |
| `anchors` | String | "generate" | 锚点模式 | `generate`（推荐）, `regenerate`, `none` |

#### 返回响应

**状态码**: 200 OK

**响应格式**:
```json
{
  "success": true,
  "filename": "合同示例.docx",
  "clauseCount": 8,
  "contractType": "通用合同",
  "anchorsEnabled": true,
  "chatgptPrompt": "# AI 合同审查助手\n\n你是一名专业的法律顾问...[长文本]",
  "instructions": [
    "1. 访问 https://chatgpt.com/",
    "2. 复制上面的 prompt 内容",
    "3. 粘贴到ChatGPT对话框",
    "4. 等待ChatGPT返回审查结果",
    "5. 复制ChatGPT的JSON回复",
    "6. 使用系统的'导入审查结果'功能"
  ],
  "parseResult": {
    "filename": "合同示例.docx",
    "title": "技术合作协议",
    "clauses": [
      {
        "id": "c1",
        "heading": "第一条 合作范围",
        "text": "甲乙双方在以下范围内进行合作...",
        "tables": null,
        "anchorId": "anc-c1-4f21",
        "startParaIndex": 0,
        "endParaIndex": 5
      },
      {
        "id": "c2",
        "heading": "第二条 保密条款",
        "text": "双方应对涉及商业机密的资料予以保密...",
        "tables": null,
        "anchorId": "anc-c2-8f3a",
        "startParaIndex": 6,
        "endParaIndex": 12
      }
    ],
    "meta": {
      "wordCount": 5230,
      "paragraphCount": 140
    }
  },
  "workflowStep": "1-prompt-generation",
  "nextStep": "/chatgpt/import-result (步骤2：导入ChatGPT审查结果)"
}
```

#### 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `chatgptPrompt` | String | **关键**: 用户复制到 ChatGPT 的完整提示文本 |
| `parseResult` | Object | **关键**: 包含所有条款信息，用于后续批注定位 |
| `parseResult.clauses[].anchorId` | String | 每个条款的唯一锚点 ID（格式：`anc-c{n}-{hash}`） |
| `parseResult.clauses[].startParaIndex` | Integer | 条款在文档中的起始段落索引 |
| `instructions` | Array | 使用指导步骤 |

#### 示例 cURL 命令

```bash
curl -X POST "http://localhost:8080/chatgpt/generate-prompt" \
  -F "file=@contract.docx" \
  -F "contractType=技术合作" \
  -F "anchors=generate" \
  | jq '.chatgptPrompt' > prompt.txt
```

---

### 端点 2: `POST /chatgpt/import-result` (兼容端点)

**功能**: 导入 ChatGPT 审查结果，使用 XML 方式插入精确批注

**状态**: ✅ 已升级为 XML 方式（向后兼容）

#### 请求

```http
POST /chatgpt/import-result HTTP/1.1
Host: localhost:8080
Content-Type: multipart/form-data

Parameters:
  - file: [DOCX文件] (必填)
  - chatgptResponse: [JSON字符串] (必填)
  - anchorStrategy: string (可选，默认: "preferAnchor")
  - cleanupAnchors: boolean (可选，默认: true)
```

#### 请求参数详解

| 参数 | 类型 | 默认值 | 说明 | 选项 |
|------|------|--------|------|------|
| `file` | MultipartFile | - | 原始合同 DOCX 文件 | 必填，来自第一步 |
| `chatgptResponse` | String | - | ChatGPT 返回的审查 JSON | 必填，必须是有效 JSON |
| `anchorStrategy` | String | "preferAnchor" | 批注定位策略 | `preferAnchor`, `anchorOnly`, `textFallback` |
| `cleanupAnchors` | Boolean | true | 批注完成后是否清理锚点标记 | true/false |

#### anchorStrategy 策略说明

| 策略 | 说明 | 应用场景 |
|------|------|----------|
| **preferAnchor** | 优先使用 `anchorId`，失败则使用 `clauseId` 文本匹配 | **推荐**，最平衡的方案 |
| **anchorOnly** | 仅使用 `anchorId` 定位，失败则跳过该批注 | 对准确性要求极高 |
| **textFallback** | 优先用 `anchorId`，失败则用 `targetText` 文字匹配 | 希望最大化批注覆盖率 |

#### ChatGPT 审查 JSON 格式

**输入示例**（由 ChatGPT 生成）:

```json
{
  "issues": [
    {
      "clauseId": "c1",
      "anchorId": "anc-c1-4f21",
      "severity": "HIGH",
      "category": "合作范围",
      "finding": "合作范围定义不够清晰",
      "suggestion": "应明确具体的合作项目和范围",
      "targetText": "甲乙双方在以下范围内进行合作",
      "matchPattern": "EXACT",
      "matchIndex": 1
    },
    {
      "clauseId": "c2",
      "anchorId": "anc-c2-8f3a",
      "severity": "HIGH",
      "category": "保密条款",
      "finding": "未定义保密信息的具体范围",
      "suggestion": "应明确界定哪些信息属于保密信息范围",
      "targetText": "双方应对涉及商业机密的资料予以保密",
      "matchPattern": "EXACT"
    }
  ],
  "summary": {
    "totalIssues": 2,
    "highRisk": 2,
    "mediumRisk": 0,
    "lowRisk": 0,
    "recommendation": "建议按以上意见修改，特别是明确合作范围和保密范围。"
  }
}
```

#### 返回响应

**状态码**: 200 OK

**响应类型**: `application/octet-stream`（二进制 DOCX 文件）

**文件名**: `{原始名}_ChatGPT审查.docx`

示例:
```
原始文件: contract.docx
返回文件: contract_ChatGPT审查.docx
```

#### 返回说明

- 返回的是带有 AI 审查批注的 Word 文档
- 批注会出现在 Word 右侧批注栏
- 如果 `cleanupAnchors=true`，临时锚点标记会被清理
- 如果 `cleanupAnchors=false`，锚点保留，支持后续增量审查

#### 示例 cURL 命令

```bash
# 基本用法
curl -X POST "http://localhost:8080/chatgpt/import-result" \
  -F "file=@contract.docx" \
  -F "chatgptResponse=@review.json" \
  -F "anchorStrategy=preferAnchor" \
  -F "cleanupAnchors=true" \
  -o annotated.docx

# 使用 JSON 字符串直接传递（避免文件）
curl -X POST "http://localhost:8080/chatgpt/import-result" \
  -F "file=@contract.docx" \
  -F 'chatgptResponse={"issues":[{"clauseId":"c1",...}]}' \
  -o annotated.docx
```

---

### 端点 3: `POST /chatgpt/import-result-xml` (推荐端点)

**功能**: XML 专用导入端点，提供最高精度的字符级批注

**状态**: ✅ 新增推荐端点

#### 请求

```http
POST /chatgpt/import-result-xml HTTP/1.1
Host: localhost:8080
Content-Type: multipart/form-data

Parameters:
  - file: [DOCX文件] (必填)
  - chatgptResponse: [JSON字符串] (必填)
  - anchorStrategy: string (可选，默认: "preferAnchor")
  - cleanupAnchors: boolean (可选，默认: true)
```

#### 参数和返回

**完全相同于 `/import-result`**

#### 区别

| 方面 | `/import-result` | `/import-result-xml` |
|------|------------------|----------------------|
| **用途** | 一般使用 | XML 专用（推荐） |
| **批注方式** | XML | XML |
| **精确度** | 字符级 | 字符级 |
| **性能** | 高 | 最高 |
| **状态** | 兼容 | 明确推荐 |

#### 示例 cURL 命令

```bash
curl -X POST "http://localhost:8080/chatgpt/import-result-xml" \
  -F "file=@contract.docx" \
  -F "chatgptResponse=@review.json" \
  -o annotated_xml.docx
```

---

### 端点 4: `GET /chatgpt/status`

**功能**: 获取 ChatGPT 集成模块的状态、工作流信息和使用指导

#### 请求

```http
GET /chatgpt/status HTTP/1.1
Host: localhost:8080
```

#### 返回响应

**状态码**: 200 OK

```json
{
  "available": true,
  "providerName": "ChatGPT 网页版（人工智能模型集成）",
  "version": "2.0-Enhanced",
  "url": "https://chatgpt.com/",
  "description": "使用ChatGPT网页版进行AI合同审查，支持精确文字级批注",
  "workflow": {
    "totalSteps": 4,
    "currentCapability": "Parse → Review (ChatGPT) → Annotate → Cleanup",
    "step1-parse": {
      "step": 1,
      "name": "解析阶段（Parse）",
      "endpoint": "POST /chatgpt/generate-prompt",
      "description": "系统自动解析合同，提取条款，生成锚点用于精确定位"
    },
    "step2-review": {
      "step": 2,
      "name": "审查阶段（Review by ChatGPT）",
      "description": "用户复制prompt到ChatGPT.com进行审查，ChatGPT返回包含targetText的审查结果"
    },
    "step3-annotate": {
      "step": 3,
      "name": "批注阶段（Annotate）",
      "endpoint": "POST /chatgpt/import-result",
      "description": "系统解析审查结果，使用targetText精确定位，在Word中插入批注"
    },
    "step4-cleanup": {
      "step": 4,
      "name": "清理阶段（Cleanup）",
      "description": "可选：清理临时锚点标记（cleanupAnchors=true时自动执行）"
    }
  },
  "features": {
    "preciseAnnotation": [
      "精确文字级批注 - 不是段落级，是具体文字",
      "三种匹配模式 - EXACT(推荐), CONTAINS, REGEX",
      "自动锚点定位 - 支持锚点、条款ID、文字匹配",
      "多条语言支持 - 中英文混合支持"
    ]
  },
  "recommendations": {
    "prompt优化": "prompt中已包含详细指导，强调targetText的重要性",
    "ChatGPT设置": "建议使用GPT-4以获得更精确的批注定位",
    "targetText填写": "必须从原文精确复制，不能改写（EXACT模式最准确）",
    "批注策略": "优先使用 anchorStrategy=preferAnchor 以获得最佳定位准确度"
  }
}
```

---

## 数据模型

### Clause 模型（条款）

```json
{
  "id": "c1",                                    // 条款 ID（如 c1, c2, c3...）
  "heading": "第一条 合作范围",                  // 条款标题
  "text": "甲乙双方在以下范围内进行合作...",      // 条款正文
  "tables": null,                                // 条款内包含的表格数据（可为 null）
  "anchorId": "anc-c1-4f21",                     // 锚点 ID（格式：anc-{id}-{hash}）
  "startParaIndex": 0,                           // 条款起始段落索引
  "endParaIndex": 5                              // 条款结束段落索引
}
```

### ParseResult 模型（解析结果）

```json
{
  "filename": "合同示例.docx",
  "title": "技术合作协议",
  "clauses": [...],                              // Clause 对象数组
  "meta": {
    "wordCount": 5230,
    "paragraphCount": 140
  }
}
```

### ReviewIssue 模型（审查问题）

```json
{
  "clauseId": "c1",                              // 条款 ID（必填）
  "anchorId": "anc-c1-4f21",                     // 锚点 ID（可选，但推荐填写）
  "severity": "HIGH",                            // 风险等级（必填：HIGH|MEDIUM|LOW）
  "category": "合作范围",                        // 问题分类（必填）
  "finding": "合作范围定义不够清晰",             // 发现的问题（必填）
  "suggestion": "应明确具体的合作项目和范围",    // 修改建议（必填）
  "targetText": "甲乙双方在以下范围内进行合作",  // 要批注的精确文字（强烈推荐）
  "matchPattern": "EXACT",                       // 匹配模式（可选：EXACT|CONTAINS|REGEX，默认EXACT）
  "matchIndex": 1                                // 匹配序号（可选，默认1）
}
```

---

## 完整工作流示例

### 场景：审查技术合作合同

#### Step 1: 生成 Prompt

**请求**:
```bash
curl -X POST "http://localhost:8080/chatgpt/generate-prompt" \
  -F "file=@tech_cooperation.docx" \
  -F "contractType=技术合作" \
  -F "anchors=generate"
```

**响应**（简化）:
```json
{
  "success": true,
  "filename": "tech_cooperation.docx",
  "clauseCount": 5,
  "anchorsEnabled": true,
  "chatgptPrompt": "# AI 合同审查助手\n...[长 Prompt]...",
  "parseResult": {
    "clauses": [
      {
        "id": "c1",
        "heading": "第一条 合作范围",
        "anchorId": "anc-c1-xxxx",
        ...
      },
      ...
    ]
  }
}
```

#### Step 2: 用户在 ChatGPT 中审查

1. 复制 `chatgptPrompt` 到 https://chatgpt.com/
2. ChatGPT 分析后返回 JSON 结果（包含 `targetText` 和 `anchorId`）

#### Step 3: 导入审查结果

**请求**:
```bash
curl -X POST "http://localhost:8080/chatgpt/import-result-xml" \
  -F "file=@tech_cooperation.docx" \
  -F "chatgptResponse=@review.json" \
  -F "anchorStrategy=preferAnchor" \
  -F "cleanupAnchors=true" \
  -o tech_cooperation_reviewed.docx
```

**响应**: 返回带有 AI 审查批注的 Word 文档

#### Step 4: 用户检查结果

在 Word 中打开 `tech_cooperation_reviewed.docx`：
- 右侧批注栏显示所有 AI 审查意见
- 批注精确指向具体文字（字符级精度）
- 锚点已被清理（如果 `cleanupAnchors=true`）

---

## 参数参考

### contractType 选项

| 值 | 说明 |
|----|------|
| `通用合同` | 通用商业合同 |
| `技术合作` | 技术合作协议 |
| `劳动合同` | 劳动合同 |
| `租赁合同` | 租赁合同 |
| `购销合同` | 购销合同 |
| `保密协议` | 保密协议（NDA） |

### 严重等级（severity）

| 值 | 说明 | 优先级 |
|----|------|--------|
| `HIGH` | 高风险 - 必须修改 | 🔴 最高 |
| `MEDIUM` | 中风险 - 建议修改 | 🟡 中等 |
| `LOW` | 低风险 - 可选修改 | 🟢 最低 |

### 匹配模式（matchPattern）

| 模式 | 说明 | 示例 | 准确度 |
|------|------|------|--------|
| `EXACT` | 精确匹配完整文字 | `"甲方应在30天内"` | ⭐⭐⭐⭐⭐ 最高 |
| `CONTAINS` | 包含关键词（允许重叠） | `"30天"` | ⭐⭐⭐ 中等 |
| `REGEX` | 正则表达式模式 | `"\d+天"` | ⭐⭐⭐⭐ 较高 |

---

## 错误处理

### 常见错误响应

#### 1. 文件格式错误

**状态码**: 400 Bad Request

```json
{
  "success": false,
  "error": "仅支持 .docx 和 .doc 格式文件"
}
```

#### 2. JSON 格式无效

**状态码**: 400 Bad Request

```json
{
  "success": false,
  "error": "ChatGPT响应缺少必需的'issues'字段"
}
```

#### 3. 服务器错误

**状态码**: 500 Internal Server Error

```json
{
  "success": false,
  "error": "生成提示失败: [具体错误信息]"
}
```

### 错误恢复策略

| 错误 | 原因 | 解决方案 |
|------|------|---------|
| `targetText` 不匹配 | 文字与原文不同 | 从原文精确复制，使用 CONTAINS 或 REGEX 模式 |
| `anchorId` 未找到 | 锚点不存在 | 确保使用 `anchors=generate` 生成了锚点 |
| 批注位置错误 | 索引混乱 | 使用最新的 `extractClausesWithCorrectIndex()` 方法 |
| 文件损坏 | XML 操作失败 | 检查输入文件完整性，重试 |

---

## 工作流总结

```
┌─────────────────────────────────────────────────────────────────┐
│                    ChatGPT 集成模块工作流                         │
└─────────────────────────────────────────────────────────────────┘

【用户】上传合同文档 (.docx)
   │
   ├─→ POST /chatgpt/generate-prompt
   │   ├─ 解析文档（Parse）
   │   ├─ 生成锚点（Anchor）
   │   ├─ 生成 ChatGPT Prompt
   │   └─ 返回 ParseResult（包含所有条款 + 锚点信息）
   │
【响应】JSON 数据 + Prompt 文本
   │
【用户】手动复制 Prompt 到 ChatGPT
   │
【ChatGPT】 生成审查 JSON（包含 targetText + anchorId）
   │
【用户】复制 ChatGPT 的 JSON 结果
   │
   ├─→ POST /chatgpt/import-result-xml
   │   ├─ 验证 JSON 格式
   │   ├─ 解析 issues 数组
   │   ├─ 使用 XML 方式处理批注
   │   │  ├─ 第1层：锚点定位（Anchor Positioning）
   │   │  ├─ 第2层：文字匹配（Text Matching）
   │   │  └─ 第3层：精确批注（Precise Annotation）
   │   ├─ 可选：清理锚点标记（Cleanup）
   │   └─ 返回带批注的 Word 文档
   │
【响应】.docx 文件（带 AI 批注）
   │
【用户】在 Word 中查看批注、修改合同

```

---

## 现状确认

### ✅ 当前系统实现状态

| 功能 | 状态 | 备注 |
|------|------|------|
| 合同解析（Parse） | ✅ 完成 | 支持条款提取、锚点生成 |
| Prompt 生成 | ✅ 完成 | 包含详细指导和示例 |
| XML 批注方式 | ✅ 完成 | 字符级精确批注 |
| 三层定位架构 | ✅ 完成 | 锚点→文字→精确批注 |
| 三种匹配模式 | ✅ 完成 | EXACT、CONTAINS、REGEX |
| 锚点清理 | ✅ 完成 | 可选清理临时标记 |
| 错误恢复 | ✅ 完成 | 自动降级处理 |
| 向后兼容 | ✅ 完成 | POI 方式标记为废弃 |

### ✅ 用户需求映射

| 需求 | 实现方式 | 端点 |
|------|---------|------|
| 输入合同文档 | MultipartFile `file` | `/generate-prompt` |
| Parse with both 模式 | `anchors=generate` 参数 | `/generate-prompt` |
| 生成 Prompt | `chatgptPrompt` 字段 | `/generate-prompt` 响应 |
| 用户复制 Prompt | 返回 JSON 中的文本 | `/generate-prompt` 响应 |
| 用户审查（ChatGPT） | 手动操作 | - |
| 输入审查结果 JSON | `chatgptResponse` 参数 | `/import-result-xml` |
| 插入批注 | XML 方式字符级 | `/import-result-xml` 处理 |
| 清理锚点 | `cleanupAnchors` 参数 | `/import-result-xml` 参数 |
| 返回修改文档 | 二进制 .docx 文件 | `/import-result-xml` 响应 |

---

## 建议事项

### 立即可做

✅ 系统已完全满足用户需求，无需修改即可使用

### 使用建议

1. **推荐端点**: 使用 `/chatgpt/import-result-xml` 而不是 `/import-result`
2. **锚点策略**: 优先使用 `anchorStrategy=preferAnchor`
3. **清理选项**:
   - 首次审查：`cleanupAnchors=false`（保留锚点供后续增量审查）
   - 最终提交：`cleanupAnchors=true`（清理临时标记）
4. **targetText 填写**: 从原文精确复制，使用 EXACT 模式

### 未来改进方向

1. 支持直接调用 ChatGPT API（自动化审查阶段）
2. 支持修订跟踪（Track Changes）模式
3. 导出审查报告（PDF/Markdown 格式）
4. 增量审查机制（基于锚点同步）

---

**版本**: 2.1.0 | **最后更新**: 2025-10-21
