# /api/review 接口使用指南

## 概述

`/api/review` 是一个新增的合同智能审查接口，采用**规则驱动的关键字匹配 + LLM精细审查**的两阶段流程：

1. **本地规则粗召回**：通过 `rules.xlsx` 中的关键字/正则规则快速筛选出"疑似问题"条款
2. **LLM精细审查**：为大模型生成结构化 Prompt，仅审查筛选后的条款（降低 token 消耗）

## 工作流程

```
User上传合同文件 (docx/doc)
         ↓
  /api/review/analyze
         ↓
  ┌─ 解析合同 → 得到条款列表
  ├─ 加载规则 → 按 contractType 过滤
  ├─ 关键字匹配 → 筛选疑似问题条款
  └─ 生成 Prompt → 返回给前端
         ↓
用户将 Prompt 复制到 LLM（ChatGPT、Claude等）
         ↓
LLM 返回 JSON 审查结果
         ↓
调用 /annotate 接口 → 在Word中插入批注
         ↓
返回批注后的合同文档
```

## API 端点详解

### 1. POST /api/review/analyze - 合同分析和 Prompt 生成

**功能**：上传合同，系统自动解析、匹配规则并生成 LLM Prompt。

**请求参数**：
- `file` (multipart): 合同文件（.docx 或 .doc）
- `contractType` (query, 可选): 合同类型，默认 "通用合同"
  - 支持值: `采购`, `外包`, `NDA`, `通用合同` 等

**请求示例**：
```bash
curl -X POST "http://localhost:8080/api/review/analyze" \
  -F "file=@contract.docx" \
  -F "contractType=采购"
```

**响应格式**：
```json
{
  "success": true,
  "filename": "contract.docx",
  "contractType": "采购",
  "timestamp": 1234567890,
  "statistics": {
    "totalClauses": 20,
    "matchedClauses": 8,
    "totalRules": 15,
    "applicableRules": 12,
    "totalMatchedRules": 12,
    "highRiskClauses": 3
  },
  "guidance": {
    "statistics": {
      "totalClauses": 8,
      "highRiskCount": 3,
      "totalMatchedRules": 12
    },
    "riskDistribution": {
      "high": 3,
      "medium": 4,
      "low": 1
    },
    "checkpoints": [
      {
        "clauseId": "c2",
        "heading": "第二条 付款条款",
        "riskLevel": "high",
        "checklist": "1. 确认付款方式（现金/票据）\n2. 明确付款周期\n3. 检查付款条件是否完整"
      }
    ]
  },
  "prompt": "您是一位资深的合同法律顾问。请根据以下信息对合同进行专业审查...\n\n【审查规则说明】...\n\n【需要审查的条款列表】\n【条款】c2 - 第二条 付款条款\n\n【原文】\n甲方应在货物交付后30天内付款，付款方式为银行转账。\n\n【审查要点】\n● 风险等级: HIGH\n  检查清单:\n  1. 确认付款方式（现金/票据）\n  2. 明确付款周期\n  3. 检查付款条件是否完整\n...",
  "promptLength": 2500,
  "matchResults": [
    {
      "clauseId": "c2",
      "heading": "第二条 付款条款",
      "riskLevel": "high",
      "matchedRuleCount": 2,
      "matchedRules": [
        {
          "id": "rule_1",
          "risk": "high",
          "keywords": "付款方式;支付周期;付款条件",
          "checklist": "1. 确认付款方式（现金/票据）\n2. 明确付款周期\n3. 检查付款条件是否完整"
        }
      ]
    }
  ],
  "nextStep": "将 prompt 字段的内容复制到 LLM（ChatGPT、Claude等），LLM 将返回 JSON 格式的审查结果，然后可以调用 /annotate 接口插入批注",
  "processingTime": "1234ms"
}
```

**关键字段说明**：
- `prompt`: LLM 审查提示词，包含条款原文、检查清单等
- `statistics`: 审查统计，包括条款总数、匹配数、风险分布
- `matchResults`: 详细的匹配结果，包括每个条款匹配的规则
- `guidance`: 审查指导信息，帮助前端展示和优化

---

### 2. GET /api/review/rules - 获取规则列表

**功能**：查看当前系统中所有可用的审查规则。

**请求参数**：
- `contractType` (query, 可选): 按合同类型过滤规则

**请求示例**：
```bash
curl "http://localhost:8080/api/review/rules?contractType=采购"
```

**响应格式**：
```json
{
  "success": true,
  "contractType": "采购",
  "totalRules": 12,
  "riskDistribution": {
    "high": 5,
    "medium": 5,
    "low": 2,
    "blocker": 0
  },
  "rules": [
    {
      "id": "rule_1",
      "contractTypes": "采购;外包;通用合同",
      "partyScope": "Neutral",
      "risk": "high",
      "keywords": "付款方式;支付周期;付款条件",
      "regex": "支付.*\\d+天",
      "checklist": "1. 确认付款方式（现金/票据）\n2. 明确付款周期\n3. 检查付款条件是否完整",
      "suggestA": "建议甲方明确付款方式和周期，避免纠纷",
      "suggestB": "乙方应确认付款条件，避免逾期支付风险"
    }
  ]
}
```

---

### 3. POST /api/review/reload-rules - 重新加载规则

**功能**：在修改 `rules.xlsx` 后，重新加载规则到内存（无需重启服务）。

**请求示例**：
```bash
curl -X POST "http://localhost:8080/api/review/reload-rules"
```

**响应格式**：
```json
{
  "success": true,
  "message": "规则已重新加载",
  "totalRules": 15
}
```

---

### 4. GET /api/review/status - 服务状态

**功能**：检查服务状态和规则加载情况。

**请求示例**：
```bash
curl "http://localhost:8080/api/review/status"
```

**响应格式**：
```json
{
  "service": "API Review Service",
  "version": "1.0",
  "rulesLoaded": true,
  "cachedRuleCount": 15,
  "timestamp": 1234567890,
  "endpoints": {
    "analyze": "POST /api/review/analyze",
    "rules": "GET /api/review/rules",
    "reloadRules": "POST /api/review/reload-rules",
    "status": "GET /api/review/status"
  }
}
```

---

## 规则表结构 (rules.xlsx)

| 字段 | 说明 | 例子 |
|------|------|------|
| `contract_types` | 适用合同类型（分号分隔） | `采购;外包;NDA` |
| `party_scope` | 适用方（Neutral/A/B） | `Neutral` |
| `risk` | 风险等级 | `high` / `medium` / `low` |
| `keywords` | 关键字列表（分号分隔，OR关系） | `付款;支付;结算` |
| `regex` | 正则表达式（可选） | `支付.*\d+天` |
| `checklist` | LLM检查要点（\n分隔） | `1. 检查付款方式\n2. 检查周期` |
| `suggest_A` | 对甲方的建议 | `建议明确付款方式` |
| `suggest_B` | 对乙方的建议 | `乙方应确认条件` |

**规则匹配逻辑**：
1. 优先检查 `keywords` 中的任意一个关键字是否出现在条款中（OR）
2. 如果有 `regex`，也进行正则匹配
3. 如果命中任一条件，则认为该规则适用

---

## 使用场景

### 场景1：快速审查采购合同

```bash
# 1. 上传采购合同并生成 Prompt
curl -X POST "http://localhost:8080/api/review/analyze" \
  -F "file=@procurement_contract.docx" \
  -F "contractType=采购" \
  | jq '.prompt' > prompt.txt

# 2. 复制 prompt.txt 内容到 ChatGPT（或其他 LLM）进行审查
# ChatGPT输出JSON结果，复制为 review_result.json

# 3. 将审查结果导入Word文档
curl -X POST "http://localhost:8080/annotate?cleanupAnchors=true" \
  -F "file=@procurement_contract_with_anchors.docx" \
  -F "review=@review_result.json" \
  -o annotated_contract.docx
```

### 场景2：查看采购合同有哪些规则

```bash
curl "http://localhost:8080/api/review/rules?contractType=采购" | jq '.rules[]'
```

### 场景3：修改规则后刷新

```bash
# 编辑 rules.xlsx，添加/删除/修改规则

# 重新加载规则
curl -X POST "http://localhost:8080/api/review/reload-rules"

# 验证规则已加载
curl "http://localhost:8080/api/review/status"
```

---

## 与 ChatGPT 集成的工作流程

```
1. 用户上传合同 → POST /api/review/analyze
   ↓
2. 返回 Prompt + 规则匹配结果
   ↓
3. 用户复制 Prompt 到 ChatGPT（https://chatgpt.com）
   ↓
4. ChatGPT 进行审查，返回 JSON 结果
   （格式如下）
   ↓
5. 用户复制 JSON 结果，调用 POST /annotate
   ↓
6. 返回带批注的 Word 文档

【ChatGPT 期望输出格式】
{
  "issues": [
    {
      "clauseId": "c2",
      "severity": "HIGH",
      "category": "付款条款",
      "finding": "付款周期不明确，未指定具体付款方式",
      "suggestion": "建议明确指定甲方应在收货后30天内通过银行转账方式支付合同款",
      "targetText": "甲方应在货物交付后30天内付款",
      "matchPattern": "EXACT"
    }
  ]
}
```

---

## 性能考虑

- **规则缓存**：规则在首次加载后缓存到内存，后续请求直接使用缓存
- **关键字匹配**：O(n) 复杂度，非常快速
- **正则匹配**：仅在关键字未匹配时进行，避免不必要的计算
- **文件大小**：支持最大 50MB 的合同文件

---

## 常见问题

**Q: 为什么需要关键字匹配这一步？**
A: 关键字匹配可以快速筛选出"疑似问题"条款，减少 LLM 审查的输入 token 数量，从而降低成本和加快审查速度。

**Q: 规则如何定期更新？**
A: 修改 `src/main/resources/review-rules/rules.xlsx`，然后调用 `POST /api/review/reload-rules` 端点刷新内存中的规则，无需重启服务。

**Q: 可以针对不同甲/乙方提供不同建议吗？**
A: 可以。在 rules.xlsx 中，每条规则有 `suggest_A` 和 `suggest_B` 两个建议字段，系统会根据 `party_scope` 自动选择。

**Q: 关键字和正则如何选择？**
A: 关键字用于快速粗匹配（广召回），正则用于精确匹配。建议先写关键字，如果误报率高再加正则表达式进行精筛。

---

## 扩展方向

1. **增量审查**：保留锚点，支持对已审查条款的增量更新
2. **版本管理**：为规则表添加版本控制，支持规则回滚
3. **反馈循环**：记录 LLM 的审查结果，优化关键字和正则表达式
4. **多语言支持**：支持中英文混合合同
5. **导出报告**：生成审查报告（PDF/Markdown）

---

## 快速上手

1. **启动服务**
   ```bash
   mvn spring-boot:run
   ```

2. **查看规则**
   ```bash
   curl http://localhost:8080/api/review/rules
   ```

3. **分析合同**
   ```bash
   curl -X POST "http://localhost:8080/api/review/analyze" \
     -F "file=@contract.docx" \
     -F "contractType=采购"
   ```

4. **复制 Prompt 到 LLM 进行审查**

5. **导入审查结果**
   ```bash
   curl -X POST "http://localhost:8080/annotate" \
     -F "file=@contract_with_anchors.docx" \
     -F "review=@llm_result.json"
   ```

---

最后更新：2025-10-22
作者：Claude Code
