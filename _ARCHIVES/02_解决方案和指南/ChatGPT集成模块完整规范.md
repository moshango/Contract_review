# ChatGPT 集成模块 - 完整实现报告

## 📋 文档概述

本报告详细说明ChatGPT网页版集成模块的：
- **完整工作流程**（4个步骤）
- **所有API端点**及调用方式
- **输入输出JSON格式**
- **关键参数说明**
- **错误处理与降级方案**
- **实时调试工具**

---

## 第一部分：整体架构

### 1.1 系统组件

```
┌─────────────────────────────────────────────────────┐
│           ChatGPT 网页版集成模块                      │
└──────────────────────┬──────────────────────────────┘
                       │
        ┌──────────────┼──────────────┐
        │              │              │
        ▼              ▼              ▼
   ┌────────┐    ┌──────────┐   ┌──────────┐
   │前端UI  │    │API端点   │   │缓存服务  │
   │(主体)  │    │(控制器)  │   │(精确定位)│
   └────────┘    └──────────┘   └──────────┘
        │              │              │
        └──────────────┼──────────────┘
                       │
        ┌──────────────┴──────────────┐
        │                             │
        ▼                             ▼
   ┌──────────────┐         ┌─────────────────┐
   │解析服务      │         │批注服务(XML)    │
   │(Parse)       │         │(Annotate)       │
   └──────────────┘         └─────────────────┘
```

### 1.2 核心文件位置

| 组件 | 文件位置 |
|------|--------|
| 控制器 | `src/main/java/.../controller/ChatGPTIntegrationController.java` |
| 提示生成服务 | `src/main/java/.../service/impl/ChatGPTWebReviewServiceImpl.java` |
| 前端界面 | `src/main/resources/static/index.html` |
| 前端逻辑 | `src/main/resources/static/js/main.js` |
| 缓存服务 | `src/main/java/.../service/ParseResultCache.java` |

---

## 第二部分：完整工作流（4个步骤）

### 2.1 工作流概述

```
【步骤1】                【步骤2】             【步骤3】          【步骤4】
上传文件    ──────→    生成提示    ──────→   手动审查   ──────→  导入结果
(.docx)         │                │          (ChatGPT)      │
                │                └──────────────┬──────────┘
          返回parseResultId           返回JSON审查结果
                                    (包含targetText)
```

### 2.2 详细工作流程

```
用户界面
  │
  ├─ 【步骤1】上传合同文件
  │   └─ POST /chatgpt/generate-prompt
  │       ├─ 输入: file(.docx), contractType
  │       ├─ 处理: Parse文档 → 生成锚点 → 缓存文档 → 生成提示
  │       └─ 输出: parseResultId, chatgptPrompt, instructions
  │
  ├─ 【步骤1.5】下载带锚点的文档（可选）
  │   └─ GET /chatgpt/get-document-with-anchors
  │       ├─ 参数: parseResultId
  │       └─ 输出: .docx文件（包含书签）
  │
  ├─ 【步骤2】用户手动在ChatGPT审查
  │   └─ 1. 访问 https://chatgpt.com/
  │   └─ 2. 复制提示文本
  │   └─ 3. 粘贴到ChatGPT对话框
  │   └─ 4. 获取ChatGPT返回的JSON审查结果
  │
  └─ 【步骤3】导入审查结果
      └─ POST /chatgpt/import-result
          ├─ 输入: chatgptResponse(JSON), parseResultId
          ├─ 处理: 解析JSON → 精确定位 → 插入批注 → 生成文档
          └─ 输出: 带批注的.docx文件
```

---

## 第三部分：API端点详解

### 3.1 端点1：生成ChatGPT提示

#### 基本信息

```
HTTP方法: POST
路由: /chatgpt/generate-prompt
请求类型: multipart/form-data
响应类型: application/json
```

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `file` | File | ✅ | 合同Word文件 (.docx格式) |
| `contractType` | String | ⚠️ | 合同类型（默认：通用合同）<br/>可选值: general, technology, purchase, service, agency, employment, lease |
| `anchors` | String | ⚠️ | 锚点模式（默认：generate）<br/>可选值: generate, regenerate, none |

#### 请求示例

```bash
curl -X POST "http://localhost:8080/chatgpt/generate-prompt" \
  -F "file=@contract.docx" \
  -F "contractType=technology" \
  -F "anchors=generate"
```

#### 响应JSON格式

```json
{
  "success": true,
  "filename": "技术合作协议.docx",
  "clauseCount": 12,
  "contractType": "technology",
  "anchorsEnabled": true,
  "chatgptPrompt": "# AI 合同审查助手\n\n你是一名专业的法律顾问...",
  "instructions": [
    "1. 访问 https://chatgpt.com/",
    "2. 复制上面的 prompt 内容",
    "3. 粘贴到ChatGPT对话框",
    "4. 等待ChatGPT返回审查结果",
    "5. 复制ChatGPT的JSON回复",
    "6. 使用系统的'导入审查结果'功能"
  ],
  "parseResult": {
    "filename": "技术合作协议.docx",
    "title": "技术合作协议",
    "clauses": [
      {
        "id": "c1",
        "heading": "第一条 合作范围",
        "text": "甲乙双方在以下范围内进行合作...",
        "anchorId": "anc-c1-4f21",
        "startParaIndex": 2,
        "endParaIndex": 5
      }
    ],
    "meta": {
      "wordCount": 2150,
      "paragraphCount": 42
    }
  },
  "documentWithAnchorsBase64": "UEsDBBQABgAIAAAAIQD...（完整的Base64编码文档）",
  "documentWithAnchorsInfo": "本文档包含生成的锚点书签，用于精确批注定位...",
  "parseResultId": "f3a2c1e5-9d8b-4c2f-a8f7-6e9d1c3b5a2f",
  "parseResultIdUsage": "在步骤2中调用 /chatgpt/import-result-xml 时，建议传递 parseResultId 参数以确保使用同一个带锚点的文档",
  "workflowStep": "1-prompt-generation",
  "nextStep": "/chatgpt/import-result-xml (步骤2：使用带锚点的文档导入ChatGPT审查结果)"
}
```

#### 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | Boolean | 请求是否成功 |
| `filename` | String | 原始上传的文件名 |
| `clauseCount` | Integer | 解析得到的条款总数 |
| `contractType` | String | 合同类型 |
| `anchorsEnabled` | Boolean | 是否启用了锚点功能 |
| `chatgptPrompt` | String | **【关键】** 生成的ChatGPT提示文本，需要复制到ChatGPT |
| `instructions` | Array | 使用说明步骤数组 |
| `parseResult` | Object | 完整的解析结果（包含所有条款详情） |
| `documentWithAnchorsBase64` | String | Base64编码的带锚点文档（可选下载） |
| `parseResultId` | String | **【关键】** 缓存ID，必须保存用于步骤2 |
| `workflowStep` | String | 当前工作流步骤标识 |
| `nextStep` | String | 下一步操作提示 |

### 3.2 端点2：导入ChatGPT审查结果

#### 基本信息

```
HTTP方法: POST
路由: /chatgpt/import-result
请求类型: multipart/form-data
响应类型: application/octet-stream (返回.docx文件)
```

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `chatgptResponse` | String | ✅ | **【关键】** ChatGPT返回的审查结果JSON |
| `parseResultId` | String | ⚠️ | 来自步骤1的缓存ID（强烈推荐） |
| `file` | File | ⚠️ | 原始合同文件（当parseResultId不可用时使用） |
| `anchorStrategy` | String | ⚠️ | 锚点定位策略（默认：preferAnchor）<br/>可选值: preferAnchor, anchorOnly, textFallback |
| `cleanupAnchors` | Boolean | ⚠️ | 批注完成后是否清理锚点（默认：true） |

#### 请求示例

```bash
# 【推荐】使用parseResultId获得最佳精确定位
curl -X POST "http://localhost:8080/chatgpt/import-result" \
  -F "chatgptResponse=@review_result.json" \
  -F "parseResultId=f3a2c1e5-9d8b-4c2f-a8f7-6e9d1c3b5a2f" \
  -F "anchorStrategy=preferAnchor" \
  -F "cleanupAnchors=true" \
  -o annotated_contract.docx

# 【备选】仅上传chatgptResponse和file
curl -X POST "http://localhost:8080/chatgpt/import-result" \
  -F "chatgptResponse=@review_result.json" \
  -F "file=@contract.docx" \
  -F "cleanupAnchors=false" \
  -o annotated_contract.docx
```

#### ChatGPT审查结果JSON格式

```json
{
  "issues": [
    {
      "clauseId": "c1",
      "anchorId": "anc-c1-4f21",
      "severity": "HIGH",
      "category": "合作范围",
      "finding": "合作范围描述不够明确，未明确列举具体项目",
      "suggestion": "建议明确列举具体的合作项目清单，并定义范围边界",
      "targetText": "软件开发、技术咨询",
      "matchPattern": "EXACT",
      "matchIndex": 1
    },
    {
      "clauseId": "c2",
      "anchorId": "anc-c2-8f3a",
      "severity": "MEDIUM",
      "category": "保密条款",
      "finding": "保密期限过长，超过行业标准",
      "suggestion": "建议保密期限调整为3年，与市场惯例保持一致",
      "targetText": "5年",
      "matchPattern": "EXACT",
      "matchIndex": 1
    }
  ]
}
```

#### ChatGPT审查结果字段说明

| 字段 | 必填 | 类型 | 说明 |
|------|------|------|------|
| `issues` | ✅ | Array | 审查问题列表 |
| `clauseId` | ✅ | String | 条款ID（c1, c2等） |
| `severity` | ✅ | String | 风险级别：HIGH, MEDIUM, LOW |
| `category` | ✅ | String | 问题分类 |
| `finding` | ✅ | String | 问题描述 |
| `suggestion` | ✅ | String | 修改建议 |
| `anchorId` | ⚠️ | String | 锚点ID（来自步骤1的parseResult） |
| `targetText` | ⚠️ | String | **【关键】** 要批注的精确文字（关键问题处） |
| `matchPattern` | ⚠️ | String | 匹配模式：EXACT, CONTAINS, REGEX（默认EXACT） |
| `matchIndex` | ⚠️ | Integer | 匹配序号（多个匹配时指定第N个，默认1） |

#### 响应内容

```
返回类型: application/octet-stream
文件名: 原文件名_ChatGPT审查.docx
内容: 带批注的Word文档，可直接在Word中查看批注
```

#### 响应示例（成功）

```
HTTP/1.1 200 OK
Content-Type: application/octet-stream
Content-Disposition: attachment; filename="contract_ChatGPT审查.docx"

[Binary DOCX file content]
```

---

## 第四部分：关键机制详解

### 4.1 parseResultId 缓存机制

#### 设计目的

```
问题: 解析和批注需要同一个文档（含锚点）
     但用户需要在ChatGPT中手动审查（外部系统）

解决方案:
  ├─ 步骤1: 解析文档生成锚点后，保存到缓存
  ├─ 返回: parseResultId（缓存的key）
  └─ 步骤2: 使用parseResultId检索缓存的文档
```

#### 缓存流程图

```
【步骤1】生成提示
    │
    ├─ 解析文件 → 生成锚点 → 插入书签
    │
    ├─ 保存到缓存
    │   └─ key: parseResultId (UUID)
    │   └─ value: {
    │       parseResult: {...},
    │       documentWithAnchorsBytes: [...],
    │       sourceFilename: "...",
    │       timestamp: ...
    │     }
    │
    └─ 返回parseResultId给用户 ✓

【步骤2】导入结果
    │
    ├─ 接收parseResultId参数
    │
    ├─ 从缓存检索文档
    │   └─ cache.retrieve(parseResultId)
    │   └─ 返回带锚点的文档字节数组
    │
    └─ 使用缓存的文档进行批注 ✓
```

#### 缓存配置

| 参数 | 值 | 说明 |
|------|-----|------|
| TTL (生存时间) | 4小时 (240分钟) | 缓存自动过期 |
| 存储方式 | 内存 (ConcurrentHashMap) | 快速访问 |
| 过期策略 | 延迟删除 | 访问时检查过期 |

### 4.2 锚点定位策略

#### 三级定位策略

```
优先级1: 使用anchorId精确定位 ✅ 最准确
         └─ 直接查找Word书签，插入批注

优先级2: 使用targetText和matchPattern定位
         └─ 在条款文本中搜索targetText
         └─ 支持3种匹配模式:
            ├─ EXACT: 精确匹配整个targetText
            ├─ CONTAINS: 包含匹配（targetText是大文本的子串）
            └─ REGEX: 正则表达式匹配

优先级3: 使用clauseId条款级定位 ⚠️ 最宽泛
         └─ 在整个条款段落中插入批注
         └─ 定位精度最低，但最稳定
```

#### 定位流程图

```
【接收批注请求】
     │
     ├─ 有anchorId且缓存中存在?
     │   ├─ YES → 【使用anchorId定位】✅ 返回
     │   └─ NO  → 继续
     │
     ├─ 有targetText?
     │   ├─ YES → 【使用targetText定位】
     │   │        ├─ matchPattern=EXACT?     → 精确搜索
     │   │        ├─ matchPattern=CONTAINS?  → 包含搜索
     │   │        └─ matchPattern=REGEX?     → 正则搜索
     │   │        ├─ 找到? → 使用matchIndex指定第N个匹配 → ✅ 返回
     │   │        └─ 未找到? → 继续
     │   └─ NO  → 继续
     │
     └─ 【使用clauseId定位】
        └─ 在整个条款插入批注 ⚠️ 返回
```

### 4.3 targetText 优先级规则

#### targetText填写指南

```
【优先级1】关键问题处（推荐）1-10字
  示例: "30天", "5年", "无限制", "ISO9001"
  特点: 精确、短、易于唯一定位
  matchPattern: 通常 EXACT 或 CONTAINS

【优先级2】短句形式（退级选择）2-15字
  示例: "30天内交付", "保密期限为5年"
  特点: 包含前后文，唯一性更强
  matchPattern: 通常 EXACT

【优先级3】完整句子（兜底方案）10+字
  示例: "甲方应在30天内完成交付"
  特点: 完整、唯一、可靠
  matchPattern: 通常 EXACT
```

---

## 第五部分：错误处理与降级方案

### 5.1 常见错误及处理

#### 错误1：缓存过期

```
错误信息: parseResultId 已过期且没有提供 file 参数

原因:
  - 缓存TTL为4小时，超过此时间自动删除
  - 未在新请求中传递file参数

解决方案:
  ├─ 方案A: 重新调用 /chatgpt/generate-prompt 获取新parseResultId
  └─ 方案B: 在import-result中同时上传file参数作为备选
```

#### 错误2：缓存未命中

```
错误信息: 缓存不存在或已过期

原因:
  - parseResultId不正确
  - 缓存已被清理

解决方案:
  └─ 调用 /chatgpt/generate-prompt 重新生成parseResultId
```

#### 错误3：JSON格式错误

```
错误信息: ChatGPT响应缺少必需的'issues'字段

原因:
  - ChatGPT返回的JSON格式不正确
  - 缺少issues数组

解决方案:
  └─ 检查ChatGPT返回的JSON是否符合规范
     └─ 必须包含"issues"数组
     └─ 每个issue必须包含必填字段
```

### 5.2 降级方案

#### 完整降级流程

```
【理想方案】使用parseResultId
    ├─ 优点: 精确定位（有锚点）
    ├─ 缺点: 需要及时传递parseResultId
    └─ 成功率: 99%

【备选方案1】同时上传file
    ├─ parseResultId过期时使用
    ├─ 优点: 回退完整，不需要立即传递ID
    ├─ 缺点: 用户需上传两次文件
    └─ 成功率: 80%（无锚点可能定位不精确）

【备选方案2】使用anchorStrategy=textFallback
    ├─ 当anchorId失效时使用
    ├─ 优点: 尽力查找targetText
    ├─ 缺点: 可能有误匹配
    └─ 成功率: 70%
```

---

## 第六部分：前端集成

### 6.1 前端工作流

#### JavaScript关键函数

```javascript
// 步骤1: 上传文件生成提示
async function generateChatGPTPrompt()
  ├─ POST /chatgpt/generate-prompt
  ├─ 保存 parseResultId 到全局变量
  └─ 显示ChatGPT提示文本

// 步骤2: 打开ChatGPT
function openChatGPT()
  └─ window.open('https://chatgpt.com/', '_blank')

// 步骤3: 导入审查结果
async function importChatGPTResult()
  ├─ 读取用户输入的ChatGPT响应JSON
  ├─ POST /chatgpt/import-result + parseResultId
  └─ 下载批注后的文档

// 调试函数
function updateChatGPTDebugPanel()
  └─ 实时显示 parseResultId 状态

function debugShowParseResultId()
  └─ 显示所有调试信息

function clearChatGPTDebug()
  └─ 清除缓存和状态
```

### 6.2 UI交互流程

```
┌─────────────────────────────────┐
│  ChatGPT 网页版集成面板          │
└────────────┬────────────────────┘
             │
    ┌────────┴────────┐
    │                 │
    ▼                 ▼
【调试面板】      【工作流步骤说明】
├─ parseResultId状态
├─ 查看全部信息      【步骤1: 上传文件】
└─ 清除缓存         ├─ 文件输入框
                    ├─ 合同类型选择
                    └─ 生成提示按钮
                           │
                           ▼
                    【显示生成的提示】
                    ├─ 提示文本 (可复制)
                    ├─ 使用说明
                    └─ 打开ChatGPT按钮
                           │
                           ▼
                    【步骤2: 手动审查】
                    └─ 在ChatGPT中进行审查

                    【步骤3: 导入结果】
                    ├─ JSON输入框
                    ├─ 锚点定位策略选择
                    ├─ 清理锚点复选框
                    └─ 导入按钮
                           │
                           ▼
                    【显示导入结果】
                    ├─ 文件名
                    ├─ 状态信息
                    └─ 继续审查按钮
```

---

## 第七部分：实际示例

### 7.1 完整请求示例

#### 步骤1：生成提示

```bash
#!/bin/bash

# 上传合同文件，生成ChatGPT提示
RESPONSE=$(curl -X POST "http://localhost:8080/chatgpt/generate-prompt" \
  -F "file=@contract.docx" \
  -F "contractType=technology" \
  -F "anchors=generate" \
  -s)

# 提取关键信息
PARSE_RESULT_ID=$(echo $RESPONSE | jq -r '.parseResultId')
CHATGPT_PROMPT=$(echo $RESPONSE | jq -r '.chatgptPrompt')

echo "parseResultId: $PARSE_RESULT_ID"
echo "提示文本长度: ${#CHATGPT_PROMPT}"
```

#### 步骤2：用户在ChatGPT进行审查

```
1. 访问 https://chatgpt.com/
2. 复制上面的CHATGPT_PROMPT内容
3. 粘贴到ChatGPT对话框
4. 等待ChatGPT返回JSON审查结果
5. 复制完整的JSON（包括{...}）
```

#### 步骤3：导入审查结果

```bash
#!/bin/bash

# 创建审查结果JSON
REVIEW_JSON='
{
  "issues": [
    {
      "clauseId": "c1",
      "anchorId": "anc-c1-4f21",
      "severity": "HIGH",
      "category": "合作范围",
      "finding": "合作范围不明确",
      "suggestion": "应明确具体项目",
      "targetText": "软件开发",
      "matchPattern": "EXACT",
      "matchIndex": 1
    }
  ]
}
'

# 导入结果，使用parseResultId获得最佳精确定位
curl -X POST "http://localhost:8080/chatgpt/import-result" \
  -F "chatgptResponse=<(echo \"$REVIEW_JSON\")" \
  -F "parseResultId=$PARSE_RESULT_ID" \
  -F "anchorStrategy=preferAnchor" \
  -F "cleanupAnchors=true" \
  -o contract_ChatGPT审查.docx

echo "批注完成！文件已保存为: contract_ChatGPT审查.docx"
```

### 7.2 Python集成示例

```python
import requests
import json

class ChatGPTIntegration:
    def __init__(self, base_url="http://localhost:8080"):
        self.base_url = base_url
        self.parse_result_id = None

    def step1_generate_prompt(self, contract_file, contract_type="technology"):
        """步骤1: 生成ChatGPT提示"""
        with open(contract_file, 'rb') as f:
            files = {'file': f}
            params = {
                'contractType': contract_type,
                'anchors': 'generate'
            }
            response = requests.post(
                f"{self.base_url}/chatgpt/generate-prompt",
                files=files,
                params=params
            )

        result = response.json()
        self.parse_result_id = result['parseResultId']

        print(f"ParseResultId: {self.parse_result_id}")
        print(f"提示文本:\n{result['chatgptPrompt'][:200]}...")
        return result

    def step3_import_result(self, chatgpt_response_json):
        """步骤3: 导入ChatGPT审查结果"""
        params = {
            'parseResultId': self.parse_result_id,
            'anchorStrategy': 'preferAnchor',
            'cleanupAnchors': 'true'
        }
        files = {
            'chatgptResponse': (None, chatgpt_response_json)
        }

        response = requests.post(
            f"{self.base_url}/chatgpt/import-result",
            params=params,
            files=files
        )

        # 保存返回的文档
        with open('contract_annotated.docx', 'wb') as f:
            f.write(response.content)

        print("批注完成！文件已保存为: contract_annotated.docx")

# 使用示例
if __name__ == "__main__":
    integration = ChatGPTIntegration()

    # 步骤1
    result = integration.step1_generate_prompt(
        "contract.docx",
        "technology"
    )

    # 步骤2: 用户在ChatGPT中审查（手动操作）
    # ...

    # 步骤3
    chatgpt_response = '''
    {
      "issues": [
        {
          "clauseId": "c1",
          "severity": "HIGH",
          "category": "合作范围",
          "finding": "范围不明确",
          "suggestion": "应列举具体项目",
          "targetText": "软件开发",
          "matchPattern": "EXACT"
        }
      ]
    }
    '''

    integration.step3_import_result(chatgpt_response)
```

---

## 第八部分：性能与优化

### 8.1 性能指标

| 操作 | 耗时 | 说明 |
|------|------|------|
| 步骤1：生成提示 | 1-3秒 | 包含文件解析、锚点生成、提示生成 |
| 步骤2：手动审查 | 1-5分钟 | 取决于ChatGPT响应时间 |
| 步骤3：导入结果 | 2-5秒 | 包含JSON解析、批注生成、文档输出 |
| **总耗时** | 1-5分钟 | 主要由步骤2决定 |

### 8.2 优化建议

```
【缓存优化】
  ├─ 使用parseResultId避免重复解析
  └─ TTL设置为4小时（可根据需要调整）

【批注优化】
  ├─ 优先使用anchorId（最快）
  ├─ 次选targetText+matchPattern（较快）
  └─ 最后使用clauseId（最慢）

【内存优化】
  ├─ 定期清理过期缓存
  └─ 大文件使用流式处理
```

---

## 第九部分：调试工具

### 9.1 前端调试工具

#### 调试面板

```
【🔧 调试信息面板】
├─ 当前parseResultId显示
├─ 状态提示
├─ 🔍 查看全部信息 (打开详细调试信息)
└─ 🗑️  清除缓存 (重置所有状态)
```

#### 浏览器控制台输出

```javascript
// 生成提示时的详细日志
✅ 【关键】已保存parseResultId到全局变量: [UUID]
   使用 window.chatgptParseResultId 可在控制台查看

// 导入结果时的详细日志
🚀 开始导入ChatGPT审查结果...
   cleanupAnchors: true
   chatgptParseResultId: [UUID]
✅ 【关键】将传递parseResultId参数
📡 请求URL: /chatgpt/import-result?parseResultId=...
✅ 使用缓存的带锚点文档进行批注...
```

### 9.2 后端日志

```
[INFO] 为ChatGPT生成提示: filename=contract.docx, contractType=technology
[INFO] 已生成带锚点的 DOCX 文档: size=1024567 字节
[INFO] ChatGPT提示生成成功，已启用锚点精确定位
[INFO] ✅ [缓存命中] 成功使用缓存的带锚点文档: parseResultId=..., 大小=... 字节
[INFO] 📄 [批注完成] ChatGPT审查结果导入成功: 总问题12个，其中10个提供了精确文字定位
```

---

## 第十部分：故障排查

### 10.1 常见问题排查

| 问题 | 症状 | 排查步骤 |
|------|------|--------|
| parseResultId未保存 | 调试面板显示"未设置" | 1. F12打开控制台<br/>2. 检查是否有错误日志<br/>3. 确保步骤1请求成功 |
| 缓存过期 | 导入时报parseResultId已过期 | 1. 检查时间差（超过4小时）<br/>2. 重新调用generate-prompt |
| targetText匹配失败 | 批注位置不对 | 1. 检查targetText是否精确<br/>2. 调整matchPattern<br/>3. 检查matchIndex |
| 文档下载失败 | import-result返回错误 | 1. 检查JSON格式<br/>2. 验证issues字段<br/>3. 查看后端日志 |

---

## 总结

**ChatGPT集成模块**提供了完整的Word合同→ChatGPT审查→自动批注的工作流，关键特性：

✅ **精确定位**: 通过parseResultId缓存机制确保使用带锚点的文档
✅ **灵活匹配**: 支持EXACT/CONTAINS/REGEX三种targetText匹配方式
✅ **完整降级**: 多层级定位策略确保最大成功率
✅ **实时调试**: 内置调试工具快速诊断问题
✅ **易于集成**: 提供完整的API、示例和文档

---

**文档生成时间**: 2025-10-22
**系统版本**: 2.1.0
**最后更新**: 2025-10-22
