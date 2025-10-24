# API接口 - 文档导航

本目录包含Qwen规则审查系统的所有API端点详细参考。

## 📋 文档列表

### 📖 API完整参考
**📄 [01_Qwen规则审查API参考](01_Qwen规则审查API参考.md)**
- 🔗 所有API端点详解
- 📝 请求/响应格式说明
- 💻 多语言调用示例
- ⚠️ 错误处理指南
- 🧪 调试技巧

**包含的接口**:
- POST /api/qwen/rule-review/review (核心审查接口)
- GET /api/qwen/rule-review/status (服务状态检查)
- GET /api/qwen/rule-review/config (配置查询)

## 🔗 API 快速参考

### 1️⃣ 核心审查接口

```http
POST /api/qwen/rule-review/review
Content-Type: application/json

{
  "prompt": "根据规则审查...",
  "contractType": "采购合同",
  "stance": "A"
}
```

**功能**: 将规则审查的Prompt发送给Qwen进行审查

**耗时**: 15-30秒

**返回**: 审查结果JSON

### 2️⃣ 服务状态接口

```http
GET /api/qwen/rule-review/status
```

**功能**: 检查Qwen服务是否可用

**返回**: 服务状态和配置信息

### 3️⃣ 配置查询接口

```http
GET /api/qwen/rule-review/config
```

**功能**: 获取当前Qwen配置（敏感信息隐藏）

**返回**: 模型名称、超时时间等信息

## 📊 API 对比表

| 接口 | 方法 | 功能 | 耗时 | 权限 |
|------|------|------|------|------|
| /review | POST | 审查合同 | 15-30秒 | 公开 |
| /status | GET | 检查服务 | < 1秒 | 公开 |
| /config | GET | 查看配置 | < 1秒 | 公开 |

## 💻 调用示例

### cURL 调用

```bash
# 执行Qwen审查
curl -X POST http://localhost:8080/api/qwen/rule-review/review \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "根据以下规则审查...",
    "contractType": "采购合同",
    "stance": "A"
  }'

# 检查服务状态
curl http://localhost:8080/api/qwen/rule-review/status

# 查看配置
curl http://localhost:8080/api/qwen/rule-review/config
```

### Python 调用

```python
import requests
import json

# 发送审查请求
url = "http://localhost:8080/api/qwen/rule-review/review"
payload = {
    "prompt": "根据规则审查...",
    "contractType": "采购合同",
    "stance": "A"
}

response = requests.post(url, json=payload)
result = response.json()

if result['success']:
    print(f"检出 {result['issueCount']} 个问题")
else:
    print(f"错误: {result['error']}")
```

### JavaScript 调用

```javascript
// 发送审查请求
const response = await fetch('/api/qwen/rule-review/review', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
        prompt: "根据规则审查...",
        contractType: "采购合同",
        stance: "A"
    })
});

const result = await response.json();
if (result.success) {
    console.log(`检出 ${result.issueCount} 个问题`);
}
```

## 📋 响应格式说明

### 成功响应 (200)

```json
{
  "success": true,
  "issueCount": 5,
  "processingTime": "18234ms",
  "review": {
    "issues": [
      {
        "anchorId": "anc-c1-4f21",
        "clauseId": "c1",
        "severity": "HIGH",
        "category": "保密条款",
        "finding": "未定义保密信息范围",
        "suggestion": "应增加保密信息的定义..."
      }
    ]
  }
}
```

### 错误响应 (400/500)

```json
{
  "success": false,
  "error": "Qwen服务未配置或不可用",
  "hint": "请检查application.properties中的qwen配置"
}
```

## 🔍 请求参数说明

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| prompt | string | ✓ | 规则审查生成的Prompt |
| contractType | string | ✗ | 合同类型 (用于日志) |
| stance | string | ✗ | 审查立场: A/B/Neutral |

## 📤 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| success | boolean | 请求是否成功 |
| issueCount | integer | 检出的问题数量 |
| processingTime | string | 处理耗时 |
| review.issues | array | 审查问题列表 |

## ⚠️ 错误处理

### 常见HTTP状态码

| 代码 | 含义 | 处理方式 |
|------|------|--------|
| 200 | 审查成功 | 处理结果 |
| 400 | 参数错误 | 检查请求参数 |
| 400 | 服务未配置 | 配置API Key |
| 500 | 服务器错误 | 查看日志 |

### 错误处理示例

```javascript
try {
    const response = await fetch('/api/qwen/rule-review/review', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });

    if (!response.ok) {
        const error = await response.json();
        console.error('API错误:', error.error);
        return;
    }

    const result = await response.json();
    if (!result.success) {
        console.error('审查失败:', result.error);
        return;
    }

    // 处理成功的审查结果
    processReviewResult(result.review);

} catch (error) {
    console.error('网络错误:', error);
}
```

## 🧪 调试工具

### 使用Postman测试

1. 创建新请求
2. 选择 POST 方法
3. 输入 URL: `http://localhost:8080/api/qwen/rule-review/review`
4. 设置Headers: `Content-Type: application/json`
5. 输入Body (JSON):
```json
{
  "prompt": "根据以下规则...",
  "contractType": "采购合同",
  "stance": "A"
}
```
6. 点击Send

### 浏览器开发者工具

```javascript
// 在浏览器控制台运行
fetch('/api/qwen/rule-review/status')
    .then(r => r.json())
    .then(d => console.table(d));
```

## 📊 API限流和配额

- 单个请求最大Prompt: 8000字符
- 单个请求超时: 30秒 (可配置)
- 并发请求: 10+用户支持
- 无日请求限制

## 🚀 集成建议

### 前端集成
- 使用Fetch API或Axios调用
- 设置适当的超时和重试
- 显示加载动画
- 处理所有错误情况

### 后端集成
- 缓存审查结果（需要时）
- 记录所有API调用
- 实施请求去重
- 添加监控告警

### 自动化工作流
- 定时审查任务
- 审查结果回调处理
- 多环境配置管理
- 审查历史追踪

## 📚 相关文档

| 文档 | 用途 |
|------|------|
| [快速开始](../快速开始) | 入门指南 |
| [功能说明](../功能说明) | 功能详解 |
| [实现总结](../实现总结) | 技术细节 |
| [故障排除](../故障排除) | 问题解决 |

## ❓ 常见问题

**Q: API的认证方式是什么?**
A: 当前版本无需认证，所有接口都是公开的

**Q: 可以进行批量审查吗?**
A: 可以，多次调用审查接口即可

**Q: 如何设置自定义超时?**
A: 修改 application.properties 中的 qwen.timeout 值

**Q: 支持CORS跨域调用吗?**
A: 支持，系统已配置CORS

## 🎯 快速查询

```
我要...                 → 查看文档
───────────────────────────────────
审查合同             → 01_API参考 → 核心审查接口
检查服务是否可用    → 01_API参考 → 服务状态接口
查看当前配置        → 01_API参考 → 配置查询接口
进行错误处理        → 01_API参考 → 错误处理章节
集成到应用          → 本README → 调用示例
```

---

**版本**: 1.0
**最后更新**: 2025-10-24
**状态**: ✅ 生产就绪
