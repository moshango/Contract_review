# Qwen 规则审查 API 参考文档

## API 总览

| 接口 | 方法 | 功能 | 权限 | 返回值 |
|------|------|------|------|--------|
| `/api/qwen/rule-review/review` | POST | 执行Qwen审查 | 公开 | JSON审查结果 |
| `/api/qwen/rule-review/status` | GET | 检查服务状态 | 公开 | 状态信息 |
| `/api/qwen/rule-review/config` | GET | 获取配置信息 | 公开 | 配置信息 |

---

## 1. 审查接口

### 端点
```
POST /api/qwen/rule-review/review
```

### 描述
将规则审查生成的Prompt发送给Qwen，获取结构化的审查结果JSON

### 请求

#### 请求头
```
Content-Type: application/json
```

#### 请求体参数

| 参数 | 类型 | 必需 | 示例 | 说明 |
|------|------|------|------|------|
| `prompt` | string | ✓ | "根据以下规则审查..." | 规则审查生成的Prompt |
| `contractType` | string | ✗ | "采购合同" | 合同类型(用于日志记录) |
| `stance` | string | ✗ | "A" | 审查立场: A/B/Neutral |

#### 请求示例

**基础请求**:
```bash
curl -X POST http://localhost:8080/api/qwen/rule-review/review \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "根据以下规则审查合同条款...",
    "contractType": "采购合同",
    "stance": "A"
  }'
```

**使用 Postman**:
```
POST http://localhost:8080/api/qwen/rule-review/review

Headers:
  Content-Type: application/json

Body (raw JSON):
{
  "prompt": "根据以下规则审查合同条款\n规则1: ...\n规则2: ...",
  "contractType": "采购合同",
  "stance": "A"
}
```

**使用 Python**:
```python
import requests

url = "http://localhost:8080/api/qwen/rule-review/review"
headers = {"Content-Type": "application/json"}
payload = {
    "prompt": "根据以下规则审查...",
    "contractType": "采购合同",
    "stance": "A"
}

response = requests.post(url, json=payload, headers=headers)
result = response.json()
print(f"检出 {result['issueCount']} 个问题")
print(f"耗时 {result['processingTime']}")
```

### 响应

#### 响应头
```
Content-Type: application/json
```

#### 响应体 (成功 - 200)

```json
{
  "success": true,
  "issueCount": 5,
  "processingTime": "18234ms",
  "timestamp": 1729750217000,
  "review": {
    "issues": [
      {
        "anchorId": "anc-c1-4f21",
        "clauseId": "c1",
        "severity": "HIGH",
        "category": "保密条款",
        "finding": "未定义保密信息范围",
        "suggestion": "应增加保密信息的定义及披露条件。"
      },
      {
        "anchorId": "anc-c2-8f3a",
        "clauseId": "c2",
        "severity": "MEDIUM",
        "category": "违约条款",
        "finding": "违约金计算方式不明确",
        "suggestion": "建议明确违约金的计算基数和比例"
      }
    ]
  }
}
```

#### 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | boolean | 请求是否成功 |
| `issueCount` | integer | 检出的问题数量 |
| `processingTime` | string | Qwen处理耗时 |
| `timestamp` | number | 响应时间戳(毫秒) |
| `review` | object | 审查结果对象 |
| `review.issues` | array | 问题列表 |

#### Issues数组字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `anchorId` | string | 锚点ID，用于精确定位批注 |
| `clauseId` | string | 条款ID |
| `severity` | string | 风险等级: HIGH/MEDIUM/LOW |
| `category` | string | 问题分类 |
| `finding` | string | 发现的问题描述 |
| `suggestion` | string | 修改建议 |

#### 响应体 (参数错误 - 400)

```json
{
  "success": false,
  "error": "Prompt不能为空",
  "timestamp": 1729750217000
}
```

#### 响应体 (服务未配置 - 400)

```json
{
  "success": false,
  "error": "Qwen服务未配置或不可用",
  "hint": "请检查application.properties中的qwen配置",
  "timestamp": 1729750217000
}
```

#### 响应体 (服务器错误 - 500)

```json
{
  "success": false,
  "error": "Qwen审查失败: 连接超时",
  "timestamp": 1729750217000
}
```

### 状态码

| 代码 | 说明 |
|------|------|
| 200 | 审查成功 |
| 400 | 参数错误或服务未配置 |
| 500 | 服务器错误 |

### 错误处理

```javascript
// 前端错误处理示例
try {
    const response = await fetch('/api/qwen/rule-review/review', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            prompt: prompt,
            contractType: contractType,
            stance: stance
        })
    });

    if (!response.ok) {
        const error = await response.json();
        console.error('审查失败:', error.error);
        showErrorMessage(error.error);
        return;
    }

    const result = await response.json();
    if (result.success) {
        console.log(`检出 ${result.issueCount} 个问题`);
        processReviewResult(result.review);
    } else {
        console.error('审查失败:', result.error);
        showErrorMessage(result.error);
    }

} catch (error) {
    console.error('网络错误:', error);
    showErrorMessage('网络连接失败');
}
```

---

## 2. 状态检查接口

### 端点
```
GET /api/qwen/rule-review/status
```

### 描述
检查Qwen服务是否已配置并可用

### 请求

#### 请求示例
```bash
curl http://localhost:8080/api/qwen/rule-review/status
```

### 响应

#### 成功响应 (200)

```json
{
  "success": true,
  "qwenAvailable": true,
  "timestamp": 1729750217000,
  "config": {
    "model": "qwen-max-latest",
    "hasApiKey": true,
    "hasBaseUrl": true
  },
  "endpoints": {
    "review": "POST /api/qwen/rule-review/review",
    "status": "GET /api/qwen/rule-review/status",
    "config": "GET /api/qwen/rule-review/config"
  },
  "message": "✓ Qwen服务已就绪"
}
```

#### 服务不可用响应 (200)

```json
{
  "success": true,
  "qwenAvailable": false,
  "timestamp": 1729750217000,
  "config": {
    "model": "未配置",
    "hasApiKey": false,
    "hasBaseUrl": false
  },
  "message": "✗ Qwen服务未配置"
}
```

---

## 3. 配置查询接口

### 端点
```
GET /api/qwen/rule-review/config
```

### 描述
获取当前Qwen配置信息(隐藏敏感数据)

### 请求

#### 请求示例
```bash
curl http://localhost:8080/api/qwen/rule-review/config
```

### 响应

```json
{
  "success": true,
  "qwen": {
    "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
    "model": "qwen-max-latest",
    "timeout": "30s",
    "api-key": "sk-***"
  }
}
```

---

## 完整工作流程示例

### JavaScript完整流程

```javascript
// 第1步: 执行规则审查获得Prompt
async function analyzeContract(file) {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('contractType', '采购合同');
    formData.append('party', 'A');

    const response = await fetch('/api/review/analyze', {
        method: 'POST',
        body: formData
    });

    if (!response.ok) {
        throw new Error('规则审查失败');
    }

    return await response.json();
}

// 第2步: 检查Qwen服务
async function checkQwenService() {
    const response = await fetch('/api/qwen/rule-review/status');
    const status = await response.json();

    if (!status.qwenAvailable) {
        throw new Error('Qwen服务未配置');
    }

    return status;
}

// 第3步: 调用Qwen进行审查
async function reviewWithQwen(prompt, contractType, stance) {
    const response = await fetch('/api/qwen/rule-review/review', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            prompt: prompt,
            contractType: contractType,
            stance: stance
        })
    });

    if (!response.ok) {
        const error = await response.json();
        throw new Error(error.error || '审查失败');
    }

    const result = await response.json();

    if (!result.success) {
        throw new Error(result.error);
    }

    return result;
}

// 第4步: 导入审查结果生成批注
async function importReviewAndAnnotate(parseResultId, reviewJson) {
    const response = await fetch(
        `/chatgpt/import-result?parseResultId=${parseResultId}`,
        {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                review: reviewJson,
                cleanupAnchors: true
            })
        }
    );

    if (!response.ok) {
        throw new Error('批注生成失败');
    }

    // 下载文件
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'contract_annotated.docx';
    a.click();
    URL.revokeObjectURL(url);
}

// 整合流程
async function oneClickReview(file) {
    try {
        console.log('步骤1: 执行规则审查...');
        const analysis = await analyzeContract(file);
        const prompt = analysis.prompt;
        const parseResultId = analysis.parseResultId;

        console.log('步骤2: 检查Qwen服务...');
        await checkQwenService();

        console.log('步骤3: 调用Qwen进行审查...');
        const qwenResult = await reviewWithQwen(
            prompt,
            analysis.contractType,
            'A'
        );

        console.log(`✓ 审查完成，检出 ${qwenResult.issueCount} 个问题`);

        console.log('步骤4: 导入并生成批注文档...');
        await importReviewAndAnnotate(parseResultId, qwenResult.review);

        console.log('✅ 全流程完成！');

    } catch (error) {
        console.error('❌ 过程中出现错误:', error.message);
        alert('审查失败: ' + error.message);
    }
}

// 使用
document.getElementById('review-button').addEventListener('click', () => {
    const file = document.getElementById('file-input').files[0];
    if (file) {
        oneClickReview(file);
    }
});
```

### 响应时间表

| 操作 | 平均耗时 | 范围 |
|------|--------|------|
| 规则匹配 | 1.5秒 | 1-3秒 |
| Qwen审查 | 20秒 | 15-30秒 |
| 批注生成 | 3秒 | 2-5秒 |
| **总计** | **24.5秒** | **18-38秒** |

### 成功场景

```
用户操作: 点击"一键Qwen审查"按钮
↓
检查Prompt是否存在 ✓
↓
发送POST请求到/api/qwen/rule-review/review
↓
系统处理:
  ├─ 验证Qwen服务可用 ✓
  ├─ 发送Prompt到Qwen API ✓
  ├─ 等待审查结果 (15-30秒) ✓
  ├─ 提取和验证JSON ✓
  └─ 自动修复格式错误 ✓
↓
返回200和结果JSON
↓
前端处理:
  ├─ 显示成功提示 ✓
  ├─ 自动填充导入框 ✓
  └─ 提示用户下一步 ✓
↓
用户点击"导入并生成批注文档"
↓
✅ 完成! 下载批注文档
```

### 失败场景

```
用户操作: 点击"一键Qwen审查"按钮
↓
检查Prompt是否存在 ✗
↓
显示错误: "请先执行规则审查"
↓
用户需要: 先点击"开始规则审查"
```

---

## 调试技巧

### 启用详细日志

在 `application.properties` 中添加:

```properties
logging.level.com.example.Contract_review.service.QwenRuleReviewService=DEBUG
logging.level.com.example.Contract_review.controller.QwenRuleReviewController=DEBUG
logging.level.com.example.Contract_review.qwen.client.QwenClient=DEBUG
```

### 浏览器开发者工具

```javascript
// 在浏览器控制台查看
// 1. 检查Prompt
console.log(document.getElementById('rule-review-prompt').textContent);

// 2. 手动调用API
fetch('/api/qwen/rule-review/review', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
        prompt: '测试prompt',
        contractType: '采购合同',
        stance: 'A'
    })
}).then(r => r.json()).then(d => console.log(d));

// 3. 检查服务状态
fetch('/api/qwen/rule-review/status')
    .then(r => r.json())
    .then(d => console.table(d));
```

### 常见HTTP错误

| 错误代码 | 原因 | 解决方案 |
|--------|------|--------|
| 400 | Prompt为空 | 先执行规则审查 |
| 400 | API Key未配置 | 配置application.properties |
| 401 | API Key无效 | 检查API Key是否正确 |
| 429 | 请求过于频繁 | 等待后重试 |
| 500 | Qwen服务错误 | 查看服务器日志 |
| 503 | Qwen服务不可用 | 稍后重试 |

---

**API版本**: v1.0
**最后更新**: 2025-10-24
**状态**: ✅ 稳定
