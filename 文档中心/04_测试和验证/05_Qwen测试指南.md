# Qwen 集成 - 完整测试指南

本文档提供完整的 Qwen 集成测试步骤与验证方法。

## 📋 目录

1. [快速开始](#快速开始)
2. [测试先决条件](#测试先决条件)
3. [端到端测试](#端到端测试)
4. [单个端点测试](#单个端点测试)
5. [流式聊天测试](#流式聊天测试)
6. [错误处理测试](#错误处理测试)
7. [性能测试](#性能测试)
8. [故障排查](#故障排查)

---

## 🚀 快速开始

### 1. 获取 API Key

1. 访问 [阿里云 DashScope 控制台](https://dashscope.console.aliyun.com/)
2. 注册或登录账户
3. 创建新的 API Key（格式：`sk-` 开头）
4. 复制 API Key 备用

### 2. 编译项目

```bash
cd "D:/工作/合同审查系统开发/spring boot/Contract_review"
mvn clean compile -DskipTests
```

**预期结果：** `BUILD SUCCESS`

### 3. 启动应用

**方式 A：使用默认端口 8080**

```bash
# 设置环境变量
export DASHSCOPE_API_KEY="sk-your-api-key-here"

# 启动应用
mvn spring-boot:run -DskipTests
```

**方式 B：使用自定义端口 8888**

```bash
export DASHSCOPE_API_KEY="sk-your-api-key-here"
mvn spring-boot:run -DskipTests -Dspring-boot.run.arguments="--server.port=8888"
```

**预期结果：**

```
Tomcat started on port(s): 8080 (http)
Started Contract_review in X.XXX seconds (JVM running for X.XXX)
```

### 4. 验证服务启动

```bash
# 使用默认端口 8080
curl -s http://localhost:8080/api/qwen/health | jq .

# 或使用自定义端口 8888
curl -s http://localhost:8888/api/qwen/health | jq .
```

**预期响应：**

```json
{
  "status": "ok",
  "config": {
    "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
    "model": "qwen-max",
    "timeout": "30s",
    "apiKeySet": true
  },
  "timestamp": 1698000000000
}
```

---

## ✅ 测试先决条件

| 项目 | 要求 | 验证方法 |
|------|------|--------|
| **Java** | 17+ | `java -version` |
| **Maven** | 3.6+ | `mvn -version` |
| **API Key** | 有效的 DASHSCOPE_API_KEY | `echo $DASHSCOPE_API_KEY` |
| **网络** | 可访问 dashscope.aliyuncs.com | `curl -I https://dashscope.aliyuncs.com` |
| **端口** | 8080 或 8888 未被占用 | `lsof -i :8080` 或 `lsof -i :8888` |

---

## 🧪 端到端测试

### 测试 1：基础健康检查

**目的：** 验证服务启动并能正确读取配置

```bash
curl -X GET http://localhost:8080/api/qwen/health
```

**预期结果：**
- HTTP 状态码: `200 OK`
- 响应包含 `"status": "ok"`
- 响应包含 `"apiKeySet": true`（API Key 已设置）
- 配置信息正确反映 baseUrl、model、timeout

**失败排查：**
- 如果 `apiKeySet` 为 `false`：未设置 `DASHSCOPE_API_KEY` 环境变量
- 如果无响应：服务未启动，检查日志

---

### 测试 2：非流式聊天（完整）

**目的：** 验证非流式 API 可正常调用 Qwen 并返回完整响应

```bash
curl -X POST http://localhost:8080/api/qwen/chat \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "user", "content": "你好，请简要自我介绍"}
    ],
    "model": "qwen-max"
  }' | jq .
```

**预期结果：**
- HTTP 状态码: `200 OK`
- 响应包含 `"content"` 字段（AI 的回复）
- 响应包含 `"finishReason": "stop"`
- `"model"` 字段显示 `"qwen-max"`

**示例响应：**

```json
{
  "id": "chatcmpl-xxxxx",
  "content": "你好！我是通义千问，一个由阿里云开发的AI助手...",
  "model": "qwen-max",
  "finishReason": "stop"
}
```

**失败排查：**
- 如果返回 `401 Unauthorized`：API Key 无效，检查 DASHSCOPE_API_KEY
- 如果返回 `429 Too Many Requests`：速率限制，等待后重试
- 如果返回 `500`：Qwen 服务故障，稍后重试

---

### 测试 3：流式聊天（SSE）

**目的：** 验证流式 API 可逐字符流式返回响应

```bash
curl -X POST http://localhost:8080/api/qwen/stream \
  -H "Content-Type: application/json" \
  -N \
  -d '{
    "messages": [
      {"role": "user", "content": "写一首春天的诗，不超过 50 个字"}
    ],
    "model": "qwen-max"
  }'
```

**参数说明：**
- `-N`：禁用缓冲，实时显示流式数据

**预期结果：**
- HTTP 状态码: `200 OK`
- 响应为 SSE 格式，每行以 `data: ` 开头
- 多行 JSON，每行包含 `"delta"` 字段（增量内容）
- 最后一条包含 `"done": true`

**示例响应：**

```
data: {"delta":"春","done":false}

data: {"delta":"风","done":false}

data: {"delta":"吹","done":false}

...

data: {"delta":"","done":true,"finishReason":"stop"}
```

**失败排查：**
- 如果没有任何输出：检查网络连接和 API Key
- 如果收到单条错误信息：检查请求格式是否正确

---

## 🔧 单个端点测试

### 端点 1：非流式聊天

**URL：** `POST /api/qwen/chat`

**请求参数：**

```json
{
  "messages": [
    {
      "role": "user",
      "content": "你好"
    }
  ],
  "model": "qwen-max",
  "temperature": 0.8,
  "top_p": 0.9
}
```

**必需字段：**
- `messages[].role`：`"user"` 或 `"assistant"` 或 `"system"`
- `messages[].content`：消息内容（非空）
- `model`：模型名称（非空）

**可选字段：**
- `temperature`：采样温度 (0-2)，默认 0.8
- `top_p`：核采样参数 (0-1)，默认 0.9

**测试用例：**

```bash
# 用例 1：简单问答
curl -X POST http://localhost:8080/api/qwen/chat \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "user", "content": "2+2等于多少？"}
    ],
    "model": "qwen-max"
  }'

# 用例 2：多轮对话
curl -X POST http://localhost:8080/api/qwen/chat \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "system", "content": "你是一个数学助手"},
      {"role": "user", "content": "2+2是多少？"},
      {"role": "assistant", "content": "2+2=4"},
      {"role": "user", "content": "那么 4+4 呢？"}
    ],
    "model": "qwen-max"
  }'

# 用例 3：高创意度（温度 1.5）
curl -X POST http://localhost:8080/api/qwen/chat \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "user", "content": "给我讲一个离奇的故事"}
    ],
    "model": "qwen-max",
    "temperature": 1.5
  }'

# 用例 4：保守回答（温度 0.3）
curl -X POST http://localhost:8080/api/qwen/chat \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "user", "content": "中国的首都是哪里？"}
    ],
    "model": "qwen-max",
    "temperature": 0.3
  }'
```

---

### 端点 2：流式聊天

**URL：** `POST /api/qwen/stream`

**请求参数：** 同非流式端点

**响应格式：** Server-Sent Events (SSE)

**测试用例：**

```bash
# 用例 1：简单流式输出
curl -X POST http://localhost:8080/api/qwen/stream \
  -H "Content-Type: application/json" \
  -N \
  -d '{
    "messages": [
      {"role": "user", "content": "列举三个编程语言"}
    ],
    "model": "qwen-max"
  }'

# 用例 2：带系统提示的流式输出
curl -X POST http://localhost:8080/api/qwen/stream \
  -H "Content-Type: application/json" \
  -N \
  -d '{
    "messages": [
      {"role": "system", "content": "你是一位专业的法律顾问。请用专业术语回答。"},
      {"role": "user", "content": "什么是合同？"}
    ],
    "model": "qwen-max"
  }'

# 用例 3：输出到文件
curl -X POST http://localhost:8080/api/qwen/stream \
  -H "Content-Type: application/json" \
  -N \
  -d '{
    "messages": [
      {"role": "user", "content": "写一个 Python 的 Hello World 程序"}
    ],
    "model": "qwen-max"
  }' > response.txt
cat response.txt
```

---

### 端点 3：健康检查

**URL：** `GET /api/qwen/health`

**响应字段说明：**

| 字段 | 含义 |
|------|------|
| `status` | 服务状态（`ok` 表示正常）|
| `config.baseUrl` | 当前使用的 API 基础 URL |
| `config.model` | 当前默认模型 |
| `config.timeout` | 请求超时时间 |
| `config.apiKeySet` | API Key 是否已设置 |
| `timestamp` | 响应时间戳（毫秒） |

**测试用例：**

```bash
# 基础健康检查
curl -s http://localhost:8080/api/qwen/health | jq .

# 检查 API Key 是否已设置
curl -s http://localhost:8080/api/qwen/health | jq '.config.apiKeySet'

# 检查 Base URL
curl -s http://localhost:8080/api/qwen/health | jq '.config.baseUrl'

# 定时健康检查（每 5 秒一次）
while true; do
  echo "$(date): $(curl -s http://localhost:8080/api/qwen/health | jq '.config.apiKeySet')"
  sleep 5
done
```

---

## 🌊 流式聊天测试

### 原理说明

流式聊天使用 **Server-Sent Events (SSE)** 协议，客户端可逐步接收响应，无需等待完整生成。

### 解析 SSE 格式

每行 SSE 数据格式：

```
data: {"delta":"文本","done":false}
```

完整流：

```
data: {"delta":"第","done":false}

data: {"delta":"一","done":false}

data: {"delta":"步","done":false}

data: {"delta":"","done":true,"finishReason":"stop"}
```

### 使用 curl 测试

```bash
# 方式 1：直接 curl（实时显示）
curl -X POST http://localhost:8080/api/qwen/stream \
  -H "Content-Type: application/json" \
  -N \
  -d '{"messages":[{"role":"user","content":"简述"}],"model":"qwen-max"}'

# 方式 2：保存到文件
curl -X POST http://localhost:8080/api/qwen/stream \
  -H "Content-Type: application/json" \
  -N \
  -d '{"messages":[{"role":"user","content":"简述"}],"model":"qwen-max"}' > stream.txt

# 方式 3：使用 bash 脚本逐行处理
curl -X POST http://localhost:8080/api/qwen/stream \
  -H "Content-Type: application/json" \
  -N \
  -d '{"messages":[{"role":"user","content":"简述"}],"model":"qwen-max"}' | while IFS= read -r line; do
  if [[ $line == data:* ]]; then
    # 提取 JSON 部分
    json="${line:6}"
    # 使用 jq 提取 delta
    echo -n "$(echo "$json" | jq -r '.delta' 2>/dev/null)"
  fi
done
```

### JavaScript 客户端测试

```javascript
// 使用 fetch EventSource
const eventSource = new EventSource('http://localhost:8080/api/qwen/stream', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    messages: [{ role: 'user', content: '你好' }],
    model: 'qwen-max'
  })
});

eventSource.onmessage = (event) => {
  const data = JSON.parse(event.data);
  console.log(data.delta);
  if (data.done) {
    eventSource.close();
  }
};

eventSource.onerror = () => {
  eventSource.close();
  console.error('Stream error');
};
```

---

## ⚠️ 错误处理测试

### 测试 1：API Key 无效

```bash
# 不设置 API Key
curl -X POST http://localhost:8080/api/qwen/chat \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"test"}],"model":"qwen-max"}'
```

**预期响应：**

```
400 Bad Request
{
  "error": "API key not configured. Set DASHSCOPE_API_KEY environment variable"
}
```

### 测试 2：空消息列表

```bash
curl -X POST http://localhost:8080/api/qwen/chat \
  -H "Content-Type: application/json" \
  -d '{"messages":[],"model":"qwen-max"}'
```

**预期响应：**

```
400 Bad Request
{
  "error": "Invalid chat request: messages or model is empty"
}
```

### 测试 3：缺少模型

```bash
curl -X POST http://localhost:8080/api/qwen/chat \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"test"}]}'
```

**预期响应：**

```
400 Bad Request
{
  "error": "Invalid chat request: messages or model is empty"
}
```

### 测试 4：请求超时（配置 `qwen.timeout=1`）

修改 `application.properties`：

```properties
qwen.timeout=1
```

重启应用，然后：

```bash
curl -X POST http://localhost:8080/api/qwen/chat \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"写一篇 1000 字的文章"}],"model":"qwen-max"}'
```

**预期响应：**

```
500 Internal Server Error
Timeout after 1 second
```

### 测试 5：错误的 Base URL

修改 `application.properties`：

```properties
qwen.base-url=https://invalid-url.com/api
```

重启应用，然后：

```bash
curl -X POST http://localhost:8080/api/qwen/chat \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"test"}],"model":"qwen-max"}'
```

**预期响应：**

```
500 Internal Server Error
404 Not Found. Check base URL: https://invalid-url.com/api
```

---

## 📊 性能测试

### 测试 1：吞吐量测试（非流式）

```bash
# 并发 10 个请求，统计平均响应时间
for i in {1..10}; do
  time curl -s -X POST http://localhost:8080/api/qwen/chat \
    -H "Content-Type: application/json" \
    -d "{\"messages\":[{\"role\":\"user\",\"content\":\"请说第 $i 句话\"}],\"model\":\"qwen-max\"}" \
    > /dev/null
done
```

**预期结果：** 平均响应时间 2-5 秒（取决于网络和 Qwen 服务）

### 测试 2：并发连接测试

```bash
# 使用 Apache Bench（ab）
ab -n 100 -c 10 -p request.json \
  -T "application/json" \
  http://localhost:8080/api/qwen/chat

# request.json 内容：
# {"messages":[{"role":"user","content":"test"}],"model":"qwen-max"}
```

### 测试 3：流式响应延迟

```bash
# 测试流式响应首字出现时间
time curl -X POST http://localhost:8080/api/qwen/stream \
  -H "Content-Type: application/json" \
  -N \
  -d '{"messages":[{"role":"user","content":"hello"}],"model":"qwen-max"}' | head -1
```

**预期结果：** 首字响应时间 1-3 秒

---

## 🔍 故障排查

### 问题 1：应用启动失败

**症状：** `Failed to start application`

**解决步骤：**

1. 检查 Java 版本

   ```bash
   java -version
   # 需要 Java 17+
   ```

2. 检查 Maven 版本

   ```bash
   mvn -version
   # 需要 Maven 3.6+
   ```

3. 清除缓存并重新编译

   ```bash
   mvn clean compile -DskipTests
   ```

4. 查看完整错误日志

   ```bash
   mvn spring-boot:run -DskipTests 2>&1 | grep -i error
   ```

---

### 问题 2：401 Unauthorized

**症状：** API 返回 `401 Unauthorized`

**原因：** API Key 无效或未设置

**解决步骤：**

1. 验证环境变量

   ```bash
   echo $DASHSCOPE_API_KEY
   ```

2. 确保 API Key 格式正确（应以 `sk-` 开头）

3. 检查 API Key 是否已过期（在控制台重新生成）

4. 重新设置环境变量并重启应用

   ```bash
   export DASHSCOPE_API_KEY="sk-your-new-key"
   mvn spring-boot:run -DskipTests
   ```

5. 验证 health 端点

   ```bash
   curl http://localhost:8080/api/qwen/health | jq '.config.apiKeySet'
   # 应返回 true
   ```

---

### 问题 3：404 Not Found

**症状：** API 返回 `404 Not Found`

**原因：** Base URL 不正确或地区配置错误

**解决步骤：**

1. 检查当前 Base URL

   ```bash
   curl http://localhost:8080/api/qwen/health | jq '.config.baseUrl'
   ```

2. 确认选择正确的地区：

   - **中国区：** `https://dashscope.aliyuncs.com/compatible-mode/v1`（默认）
   - **新加坡区：** `https://dashscope-intl.aliyuncs.com/compatible-mode/v1`

3. 如需切换地区，设置环境变量

   ```bash
   # 切换到新加坡区
   export QWEN_BASE_URL="https://dashscope-intl.aliyuncs.com/compatible-mode/v1"
   mvn spring-boot:run -DskipTests
   ```

4. 使用 curl 直接测试 Base URL

   ```bash
   curl -X GET "https://dashscope.aliyuncs.com/compatible-mode/v1"
   # 应返回某个响应（可能是 404 的根端点）
   ```

---

### 问题 4：429 Too Many Requests

**症状：** API 返回 `429 Too Many Requests`

**原因：** 请求频率超过限制或免费额度已用完

**解决步骤：**

1. **降低请求频率**

   在请求之间添加延迟：

   ```bash
   for i in {1..5}; do
     curl -s -X POST http://localhost:8080/api/qwen/chat \
       -H "Content-Type: application/json" \
       -d '{"messages":[{"role":"user","content":"test"}],"model":"qwen-max"}'
     sleep 2  # 等待 2 秒
   done
   ```

2. **检查 API 配额**

   登录 [DashScope 控制台](https://dashscope.console.aliyun.com/)，检查：
   - 免费额度是否已用完
   - QPS（每秒请求数）限制
   - 账户是否有 Token 余额

3. **升级账户或充值**

   如需更高的 QPS，可升级到付费账户或充值

---

### 问题 5：没有响应或超时

**症状：** 请求无响应或长时间挂起

**原因：** 网络连接问题、防火墙、或服务故障

**解决步骤：**

1. **检查网络连接**

   ```bash
   # 测试能否访问 Qwen 服务
   curl -I https://dashscope.aliyuncs.com
   # 应返回 200 或 404，不应超时
   ```

2. **检查防火墙**

   确保允许出站 HTTPS 连接（端口 443）

3. **增加超时时间**

   修改 `application.properties`：

   ```properties
   qwen.timeout=60
   ```

4. **检查代理设置**

   如在公司网络中，可能需要配置代理：

   修改 `application.properties`：

   ```properties
   # 启用代理
   spring.http.proxy.host=proxy.company.com
   spring.http.proxy.port=8080
   ```

5. **检查 Qwen 服务状态**

   访问 [阿里云状态页](https://status.aliyun.com/)，查看 DashScope 是否有故障

---

## ✨ 额外测试场景

### 场景 1：与合同审查集成

```bash
# 1. 上传合同文件并解析
curl -X POST http://localhost:8080/parse \
  -F "file=@contract.docx" \
  -o parsed.json

# 2. 使用 Qwen 审查
curl -X POST http://localhost:8080/api/qwen/chat \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {
        "role": "system",
        "content": "你是专业法律顾问，请审查合同"
      },
      {
        "role": "user",
        "content": "以下是合同条款：$(cat parsed.json)"
      }
    ],
    "model": "qwen-max"
  }' > review.json

# 3. 根据审查结果生成批注
curl -X POST http://localhost:8080/annotate \
  -F "file=@contract.docx" \
  -F "review=@review.json"
  -o annotated.docx
```

---

### 场景 2：使用不同模型

```bash
# 测试 qwen-plus（均衡能力和成本）
curl -X POST http://localhost:8080/api/qwen/chat \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [{"role": "user", "content": "hello"}],
    "model": "qwen-plus"
  }'

# 测试 qwen-turbo（低成本高速）
curl -X POST http://localhost:8080/api/qwen/chat \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [{"role": "user", "content": "hello"}],
    "model": "qwen-turbo"
  }'

# 测试 qwen-long（长文本处理，支持 200K tokens）
curl -X POST http://localhost:8080/api/qwen/chat \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [{"role": "user", "content": "[长合同文本]"}],
    "model": "qwen-long"
  }'
```

---

## 📝 测试检查清单

使用此清单追踪所有测试完成情况：

- [ ] **编译**：`mvn clean compile -DskipTests` 成功
- [ ] **启动**：应用成功启动在 8080（或其他端口）
- [ ] **健康检查**：`/api/qwen/health` 返回 200 OK
- [ ] **API Key**：`health` 响应显示 `apiKeySet: true`
- [ ] **非流式聊天**：`/api/qwen/chat` 返回完整回复
- [ ] **流式聊天**：`/api/qwen/stream` 返回 SSE 流
- [ ] **错误处理**：无 API Key 时返回清晰错误信息
- [ ] **超时处理**：长时间请求能正确超时
- [ ] **参数验证**：空消息/模型被正确拒绝
- [ ] **并发**：多个并发请求正常处理
- [ ] **不同模型**：支持 qwen-max、qwen-plus、qwen-turbo
- [ ] **地区切换**：能切换到新加坡区

---

## 📞 获取帮助

如遇问题，请：

1. 查看 [README-QWEN.md](README-QWEN.md) 快速参考
2. 查看 [docs/Qwen集成指南.md](docs/Qwen集成指南.md) 详细文档
3. 检查日志：`mvn spring-boot:run -DskipTests 2>&1 | tail -100`
4. 启用调试日志（修改 `application.properties`）：

   ```properties
   logging.level.com.example.Contract_review.qwen=DEBUG
   logging.level.org.springframework.web.reactive=DEBUG
   ```

5. 访问 [Qwen 官方文档](https://help.aliyun.com/zh/dashscope/)

---

**最后更新：2025-10-23**

**祝测试顺利！**

