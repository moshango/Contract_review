# 规则审查模块API端口文档

## 📋 文档概述

本文档详细描述了合同审查系统中规则审查模块的所有API端口，包括输入输出参数、请求格式、响应格式和错误处理。

**文档版本**: 1.0.0  
**最后更新**: 2025-01-27  
**适用系统**: Contract Review System v2.4.0

---

## 🎯 API总览

规则审查模块包含以下主要API组：

| API组 | 基础路径 | 功能描述 | 控制器 |
|-------|----------|----------|--------|
| **规则审查** | `/api/review` | 规则匹配和Prompt生成 | ApiReviewController |
| **Qwen审查** | `/api/qwen/rule-review` | Qwen AI审查服务 | QwenRuleReviewController |
| **统一审查** | `/api/unified` | 统一审查入口 | UnifiedReviewController |
| **合同解析** | `/api/parse` | 合同解析和锚点生成 | ContractController |
| **批注导入** | `/api/annotate` | 批注导入和文档生成 | ContractController |

---

## 🔧 规则审查API组 (`/api/review`)

### 1. 合同分析接口

**端点**: `POST /api/review/analyze`

**功能**: 解析合同文件，进行规则匹配，生成LLM审查Prompt

#### 请求参数

| 参数名 | 类型 | 必需 | 说明 | 示例值 |
|--------|------|------|------|--------|
| `file` | MultipartFile | ✓ | 合同文件(.docx/.doc) | contract.docx |
| `contractType` | String | ✗ | 合同类型 | 采购合同、外包合同、NDA、通用合同 |
| `party` | String | ✗ | 审查立场 | A(甲方)、B(乙方)、null(中立) |

#### 请求示例

```bash
# 基础请求
curl -X POST "http://localhost:8080/api/review/analyze" \
  -F "file=@contract.docx" \
  -F "contractType=采购合同" \
  -F "party=A"

# 仅文件上传
curl -X POST "http://localhost:8080/api/review/analyze" \
  -F "file=@contract.docx"
```

#### 响应格式

**成功响应**:
```json
{
  "success": true,
  "filename": "contract.docx",
  "contractType": "采购合同",
  "userStance": "A",
  "statistics": {
    "totalClauses": 25,
    "matchedClauses": 12,
    "highRiskClauses": 3,
    "mediumRiskClauses": 6,
    "lowRiskClauses": 3,
    "totalRules": 120,
    "applicableRules": 45,
    "totalMatchedRules": 18,
    "parseTime": 1234,
    "matchTime": 567
  },
  "matchResults": [
    {
      "clauseId": "c1",
      "anchorId": "anc-c1-4f21",
      "clauseHeading": "第一条 合作范围",
      "clauseText": "甲乙双方在以下范围内进行合作...",
      "matchedRules": [
        {
          "id": "rule_1",
          "risk": "high",
          "keywords": "合作范围;业务范围",
          "checklist": "1. 确认合作范围是否明确\n2. 检查业务边界",
          "suggestA": "建议甲方明确合作范围",
          "suggestB": "乙方应确认业务边界"
        }
      ],
      "matchCount": 1,
      "highestRisk": "high"
    }
  ],
  "prompt": "您是一位资深的合同法律顾问...",
  "parseResultId": "uuid-1234-5678",
  "guidance": "审查指导信息...",
  "processingTime": 2345
}
```

**错误响应**:
```json
{
  "success": false,
  "error": "文件格式不支持",
  "filename": "contract.pdf",
  "supportedFormats": [".docx", ".doc"]
}
```

---

### 2. 获取规则列表接口

**端点**: `GET /api/review/rules`

**功能**: 获取所有或特定合同类型的审查规则

#### 请求参数

| 参数名 | 类型 | 必需 | 说明 | 示例值 |
|--------|------|------|------|--------|
| `contractType` | String | ✗ | 合同类型过滤 | 采购合同 |

#### 请求示例

```bash
# 获取所有规则
curl "http://localhost:8080/api/review/rules"

# 获取特定类型规则
curl "http://localhost:8080/api/review/rules?contractType=采购合同"
```

#### 响应格式

```json
{
  "success": true,
  "contractType": "采购合同",
  "totalRules": 45,
  "riskDistribution": {
    "high": 8,
    "medium": 20,
    "low": 15,
    "blocker": 2
  },
  "rules": [
    {
      "id": "rule_1",
      "contractTypes": "采购;外包",
      "partyScope": "Neutral",
      "risk": "high",
      "keywords": "付款方式;支付周期;付款条件",
      "regex": "支付.*\\d+天",
      "checklist": "1. 确认付款方式\n2. 明确付款周期",
      "suggestA": "建议甲方明确付款方式...",
      "suggestB": "建议乙方确认付款条件..."
    }
  ]
}
```

---

### 3. 重新加载规则接口

**端点**: `POST /api/review/reload-rules`

**功能**: 重新从rules.xlsx加载规则（无需重启服务）

#### 请求示例

```bash
curl -X POST "http://localhost:8080/api/review/reload-rules"
```

#### 响应格式

```json
{
  "success": true,
  "message": "规则已重新加载",
  "totalRules": 120
}
```

---

### 4. 服务状态接口

**端点**: `GET /api/review/status`

**功能**: 获取规则审查服务状态信息

#### 响应格式

```json
{
  "service": "API Review Service",
  "version": "1.0",
  "rulesLoaded": true,
  "cachedRuleCount": 120,
  "timestamp": 1706342400000,
  "endpoints": {
    "analyze": "POST /api/review/analyze",
    "rules": "GET /api/review/rules",
    "reloadRules": "POST /api/review/reload-rules",
    "settings": "POST /api/review/settings",
    "status": "GET /api/review/status"
  }
}
```

---

### 5. 设置审查立场接口

**端点**: `POST /api/review/settings`

**功能**: 设置用户的审查立场

#### 请求参数

| 参数名 | 类型 | 必需 | 说明 | 示例值 |
|--------|------|------|------|--------|
| `party` | String | ✗ | 审查立场 | A(甲方)、B(乙方)、null(中立) |

#### 请求示例

```bash
curl -X POST "http://localhost:8080/api/review/settings" \
  -F "party=A"
```

#### 响应格式

```json
{
  "success": true,
  "message": "审查立场已设置为: A方",
  "currentStance": "A",
  "stanceDescription": "甲方立场：关注自身权益保护"
}
```

---

### 6. 获取审查设置接口

**端点**: `GET /api/review/settings`

**功能**: 获取当前审查设置

#### 响应格式

```json
{
  "success": true,
  "currentStance": "A",
  "stanceDescription": "甲方立场：关注自身权益保护",
  "availableStances": ["A", "B", "Neutral"]
}
```

---

### 7. 当事人信息提取接口

**端点**: `POST /api/review/extract-parties`

**功能**: 从合同文本中提取甲乙方信息

#### 请求参数

| 参数名 | 类型 | 必需 | 说明 | 示例值 |
|--------|------|------|------|--------|
| `text` | String | ✓ | 合同文本内容 | "甲方：ABC公司 乙方：XYZ公司..." |

#### 请求示例

```bash
curl -X POST "http://localhost:8080/api/review/extract-parties" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "甲方：ABC科技有限公司 乙方：XYZ服务有限公司"
  }'
```

#### 响应格式

```json
{
  "success": true,
  "partyA": "ABC科技有限公司",
  "partyB": "XYZ服务有限公司",
  "partyARoleName": "甲方",
  "partyBRoleName": "乙方",
  "extractionTime": 123
}
```

---

## 🤖 Qwen审查API组 (`/api/qwen/rule-review`)

### 1. Qwen审查接口

**端点**: `POST /api/qwen/rule-review/review`

**功能**: 将规则审查生成的Prompt发送给Qwen，获取结构化审查结果

#### 请求参数

| 参数名 | 类型 | 必需 | 说明 | 示例值 |
|--------|------|------|------|--------|
| `prompt` | String | ✓ | 规则审查生成的Prompt | "根据以下规则审查合同..." |
| `contractType` | String | ✗ | 合同类型 | 采购合同 |
| `stance` | String | ✗ | 审查立场 | A、B、Neutral |

#### 请求示例

```bash
curl -X POST "http://localhost:8080/api/qwen/rule-review/review" \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "根据以下规则审查合同条款...",
    "contractType": "采购合同",
    "stance": "A"
  }'
```

#### 响应格式

```json
{
  "success": true,
  "issueCount": 3,
  "issues": [
    {
      "anchorId": "anc-c2-8f3a",
      "clauseId": "c2",
      "severity": "HIGH",
      "category": "保密条款",
      "finding": "未定义保密信息范围",
      "targetText": "保密信息",
      "suggestion": "应增加保密信息的定义及披露条件"
    }
  ],
  "processingTime": 2345,
  "model": "qwen-max"
}
```

---

### 2. Qwen服务状态接口

**端点**: `GET /api/qwen/rule-review/status`

**功能**: 检查Qwen服务状态

#### 响应格式

```json
{
  "success": true,
  "status": "ok",
  "config": {
    "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
    "model": "qwen-max",
    "timeout": "30s",
    "apiKeySet": true
  },
  "timestamp": 1706342400000
}
```

---

### 3. Qwen配置接口

**端点**: `GET /api/qwen/rule-review/config`

**功能**: 获取Qwen配置信息

#### 响应格式

```json
{
  "success": true,
  "config": {
    "base-url": "https://dashscope.aliyuncs.com/compatible-mode/v1",
    "model": "qwen-max",
    "timeout": "30s",
    "api-key": "sk-***"
  }
}
```

---

### 4. 一键审查接口

**端点**: `POST /api/qwen/rule-review/one-click-review`

**功能**: 完整的审查工作流（解析→规则匹配→Qwen审查→批注导入）

#### 请求参数

| 参数名 | 类型 | 必需 | 说明 | 示例值 |
|--------|------|------|------|--------|
| `file` | MultipartFile | ✓ | 合同文件 | contract.docx |
| `stance` | String | ✗ | 审查立场 | neutral |

#### 请求示例

```bash
curl -X POST "http://localhost:8080/api/qwen/rule-review/one-click-review" \
  -F "file=@contract.docx" \
  -F "stance=A"
```

#### 响应格式

```json
{
  "success": true,
  "filename": "contract.docx",
  "reviewResult": {
    "issueCount": 3,
    "issues": [...]
  },
  "annotatedDocumentUrl": "/api/download/contract_审查结果.docx",
  "processingTime": 4567
}
```

---

## 🔄 统一审查API组 (`/api/unified`)

### 1. 统一审查接口

**端点**: `POST /api/unified/review`

**功能**: 统一的审查入口，支持多种审查模式

#### 请求参数

| 参数名 | 类型 | 必需 | 说明 | 示例值 |
|--------|------|------|------|--------|
| `file` | MultipartFile | ✓ | 合同文件 | contract.docx |
| `contractType` | String | ✗ | 合同类型 | 通用合同 |
| `party` | String | ✗ | 审查立场 | A方、B方、null |
| `reviewMode` | String | ✗ | 审查模式 | rules、ai、full |
| `aiProvider` | String | ✗ | AI提供商 | qwen、chatgpt、claude |

#### 审查模式说明

| 模式 | 说明 | 返回内容 |
|------|------|----------|
| `rules` | 仅规则审查 | Prompt、匹配结果、统计信息 |
| `ai` | 调用AI审查 | AI审查结果JSON |
| `full` | 完整流程 | 规则审查+AI审查+批注导入 |

#### 请求示例

```bash
# 仅规则审查
curl -X POST "http://localhost:8080/api/unified/review" \
  -F "file=@contract.docx" \
  -F "contractType=采购合同" \
  -F "party=A方" \
  -F "reviewMode=rules"

# AI审查
curl -X POST "http://localhost:8080/api/unified/review" \
  -F "file=@contract.docx" \
  -F "reviewMode=ai" \
  -F "aiProvider=qwen"

# 完整流程
curl -X POST "http://localhost:8080/api/unified/review" \
  -F "file=@contract.docx" \
  -F "reviewMode=full" \
  -F "aiProvider=qwen"
```

#### 响应格式

**规则模式响应**:
```json
{
  "success": true,
  "statistics": {...},
  "matchResults": [...],
  "prompt": "审查Prompt...",
  "parseResultId": "uuid-1234",
  "userStance": "A方",
  "processingTime": 1234,
  "reviewMode": "rules"
}
```

**AI模式响应**:
```json
{
  "success": true,
  "statistics": {...},
  "matchResults": [...],
  "prompt": "审查Prompt...",
  "aiResult": {
    "issues": [...]
  },
  "parseResultId": "uuid-1234",
  "processingTime": 4567,
  "reviewMode": "ai"
}
```

**完整模式响应**:
```json
{
  "success": true,
  "statistics": {...},
  "matchResults": [...],
  "prompt": "审查Prompt...",
  "aiResult": {
    "issues": [...]
  },
  "parseResultId": "uuid-1234",
  "annotatedDocumentUrl": "/api/download/contract_统一审查_A方.docx",
  "processingTime": 7890,
  "reviewMode": "full"
}
```

---

### 2. 健康检查接口

**端点**: `GET /api/unified/health`

**功能**: 统一审查服务健康检查

#### 响应格式

```json
{
  "status": "UP",
  "service": "Unified Review API",
  "version": "1.0.0"
}
```

---

## 📄 合同解析API组 (`/api/parse`)

### 1. 合同解析接口

**端点**: `POST /api/parse`

**功能**: 解析合同文档，提取条款结构，生成锚点

#### 请求参数

| 参数名 | 类型 | 必需 | 说明 | 示例值 |
|--------|------|------|------|--------|
| `file` | MultipartFile | ✓ | 合同文件 | contract.docx |
| `anchors` | String | ✗ | 锚点模式 | none、generate、regenerate |
| `returnMode` | String | ✗ | 返回模式 | json、file、both |

#### 请求示例

```bash
# 仅解析，不生成锚点
curl -X POST "http://localhost:8080/api/parse" \
  -F "file=@contract.docx" \
  -F "anchors=none" \
  -F "returnMode=json"

# 生成锚点并返回带锚点文档
curl -X POST "http://localhost:8080/api/parse" \
  -F "file=@contract.docx" \
  -F "anchors=generate" \
  -F "returnMode=both"
```

#### 响应格式

```json
{
  "filename": "contract.docx",
  "title": "技术合作协议",
  "partyA": "ABC科技有限公司",
  "partyB": "XYZ服务有限公司",
  "partyARoleName": "甲方",
  "partyBRoleName": "乙方",
  "clauses": [
    {
      "id": "c1",
      "heading": "第一条 合作范围",
      "text": "甲乙双方在以下范围内进行合作...",
      "anchorId": "anc-c1-4f21",
      "startParaIndex": 5,
      "endParaIndex": 9
    }
  ],
  "meta": {
    "wordCount": 5230,
    "paragraphCount": 140,
    "parseResultId": "uuid-1234"
  }
}
```

---

## 📝 批注导入API组 (`/api/annotate`)

### 1. 批注导入接口

**端点**: `POST /api/annotate`

**功能**: 根据审查结果在合同中插入批注

#### 请求参数

| 参数名 | 类型 | 必需 | 说明 | 示例值 |
|--------|------|------|------|--------|
| `file` | MultipartFile | ✓ | 原始合同文件 | contract.docx |
| `review` | String | ✓ | 审查结果JSON | {"issues": [...]} |
| `anchorStrategy` | String | ✗ | 锚点策略 | preferAnchor、anchorOnly、textFallback |
| `cleanupAnchors` | Boolean | ✗ | 是否清理锚点 | true、false |

#### 请求示例

```bash
curl -X POST "http://localhost:8080/api/annotate" \
  -F "file=@contract.docx" \
  -F "review=@review.json" \
  -F "anchorStrategy=preferAnchor" \
  -F "cleanupAnchors=true"
```

#### 响应格式

**成功响应**: 返回带批注的Word文档（二进制流）

**错误响应**:
```json
{
  "success": false,
  "error": "批注导入失败",
  "details": "无法找到对应的锚点"
}
```

---

### 2. XML批注导入接口

**端点**: `POST /api/annotate-xml`

**功能**: 使用XML方式导入批注（更精确的定位）

#### 请求参数

| 参数名 | 类型 | 必需 | 说明 | 示例值 |
|--------|------|------|------|--------|
| `file` | MultipartFile | ✓ | 原始合同文件 | contract.docx |
| `review` | String | ✓ | 审查结果JSON | {"issues": [...]} |
| `anchorStrategy` | String | ✗ | 锚点策略 | preferAnchor |
| `cleanupAnchors` | Boolean | ✗ | 是否清理锚点 | false |

---

## ⚠️ 错误处理

### 通用错误格式

```json
{
  "success": false,
  "error": "错误描述",
  "details": "详细错误信息",
  "timestamp": 1706342400000
}
```

### 常见错误码

| HTTP状态码 | 错误类型 | 说明 |
|-----------|----------|------|
| 400 | BAD_REQUEST | 请求参数错误 |
| 404 | NOT_FOUND | 资源不存在 |
| 413 | PAYLOAD_TOO_LARGE | 文件过大（>50MB） |
| 415 | UNSUPPORTED_MEDIA_TYPE | 不支持的文件格式 |
| 500 | INTERNAL_SERVER_ERROR | 服务器内部错误 |

### 文件格式限制

- **支持格式**: `.docx`, `.doc`
- **文件大小**: 最大50MB
- **编码要求**: UTF-8

---

## 🔧 配置说明

### 应用配置

```properties
# 服务器配置
server.port=8080

# 文件上传配置
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB

# Qwen配置
qwen.api-key=sk-xxxxxxxxxxxxxxxx
qwen.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1
qwen.model=qwen-vl-max-latest
qwen.timeout=300
```

### 环境变量

| 变量名 | 说明 | 示例值 |
|--------|------|--------|
| `DASHSCOPE_API_KEY` | Qwen API密钥 | sk-xxxxxxxxxxxxxxxx |
| `QWEN_BASE_URL` | Qwen API基础URL | https://dashscope.aliyuncs.com/compatible-mode/v1 |

---

## 📚 使用示例

### 完整审查流程示例

```bash
# 1. 上传文件并分析
curl -X POST "http://localhost:8080/api/review/analyze" \
  -F "file=@contract.docx" \
  -F "contractType=采购合同" \
  -F "party=A" > analysis.json

# 2. 提取Prompt
PROMPT=$(cat analysis.json | jq -r '.prompt')

# 3. 使用Qwen审查
curl -X POST "http://localhost:8080/api/qwen/rule-review/review" \
  -H "Content-Type: application/json" \
  -d "{\"prompt\": \"$PROMPT\"}" > review.json

# 4. 导入批注
curl -X POST "http://localhost:8080/api/annotate" \
  -F "file=@contract.docx" \
  -F "review=@review.json" \
  -o annotated_contract.docx
```

### 一键审查示例

```bash
# 使用统一接口进行完整审查
curl -X POST "http://localhost:8080/api/unified/review" \
  -F "file=@contract.docx" \
  -F "contractType=采购合同" \
  -F "party=A方" \
  -F "reviewMode=full" \
  -F "aiProvider=qwen"
```

---

## 📞 技术支持

- **文档版本**: 1.0.0
- **API版本**: v1
- **最后更新**: 2025-01-27
- **维护团队**: Contract Review System Team

---

**注意**: 本文档基于系统当前版本编写，如有更新请参考最新版本。
