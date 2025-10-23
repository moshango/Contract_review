# 通义千问（Qwen）集成指南

## 概览

本项目集成了阿里云通义千问（Qwen）的 OpenAI 兼容接口，支持非流式和流式两种调用方式。

## 获取 API Key

1. 访问 [阿里云 DashScope 控制台](https://dashscope.console.aliyun.com/)
2. 注册或登录账户
3. 创建新的 API Key
4. 复制 API Key 备用

**免费额度**：新用户可获得免费试用额度，详见控制台。

## 配置

### 方式 1：环境变量（推荐）

```bash
export DASHSCOPE_API_KEY="sk-xxxxxxxxxxxxxxxxxxxx"
export QWEN_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"
```

### 方式 2：application.properties

```properties
qwen.api-key=sk-xxxxxxxxxxxxxxxxxxxx
qwen.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1
qwen.model=qwen-max
qwen.timeout=30
```

## 地区配置

| 地区 | Base URL | 应用场景 |
|------|----------|---------|
| **中国区** | `https://dashscope.aliyuncs.com/compatible-mode/v1` | 国内用户，延迟低 |
| **新加坡区** | `https://dashscope-intl.aliyuncs.com/compatible-mode/v1` | 国际用户，全球可用 |

默认使用中国区。切换地区：

```bash
export QWEN_BASE_URL="https://dashscope-intl.aliyuncs.com/compatible-mode/v1"
```

## 支持的模型

- `qwen-max` - 最强能力，推荐使用
- `qwen-plus` - 均衡能力和成本
- `qwen-turbo` - 低成本高速
- `qwen-long` - 长文本处理（支持 200K tokens）

在 `application.properties` 中修改 `qwen.model` 来切换模型。

## API 接口

### 1. 非流式聊天

**请求**：
```bash
curl -X POST http://localhost:8080/api/qwen/chat \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "user", "content": "你好，请介绍一下自己"}
    ],
    "model": "qwen-max"
  }'
```

**响应**：
```json
{
  "id": "chatcmpl-...",
  "content": "你好！我是通义千问，一个由阿里云开发的AI助手...",
  "model": "qwen-max",
  "finishReason": "stop"
}
```

### 2. 流式聊天

**请求**：
```bash
curl -X POST http://localhost:8080/api/qwen/stream \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "user", "content": "写一首春天的诗"}
    ],
    "model": "qwen-max"
  }' -N
```

**响应**（SSE 格式）：
```
data: {"delta":"春","done":false}

data: {"delta":"日","done":false}

data: {"delta":"迟","done":false}

data: {"delta":"暮，","done":false}
...
data: {"delta":"","done":true,"finishReason":"stop"}
```

### 3. 健康检查

```bash
curl http://localhost:8080/api/qwen/health
```

**响应**：
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

## 常见错误

### 404 - Not Found

**原因**：Base URL 不正确

**解决**：
- 检查 `qwen.base-url` 配置
- 确认地区选择正确（中国/新加坡）
- 确认网络连接正常

### 401 - Unauthorized

**原因**：API Key 无效或过期

**解决**：
- 验证 `qwen.api-key` 配置正确
- 检查 API Key 是否已过期
- 从 DashScope 控制台重新生成 API Key

### 429 - Too Many Requests

**原因**：超出速率限制（QPS 限制）

**解决**：
- 减低请求频率
- 检查免费额度是否已用尽
- 升级到付费账户获得更高的 QPS 限制

### 500 - Internal Server Error

**原因**：Qwen 服务端问题

**解决**：
- 稍后重试
- 检查官方服务状态
- 切换到另一个模型尝试

## 启动项目

```bash
# 1. 编译项目
mvn clean package -DskipTests

# 2. 启动应用（需要提前设置环境变量）
export DASHSCOPE_API_KEY="sk-xxx"
mvn spring-boot:run

# 或使用 Java 命令启动
java -jar target/Contract_review-0.0.1-SNAPSHOT.jar
```

## 项目结构

```
src/main/java/com/example/Contract_review/qwen/
├── controller/
│   └── QwenController.java          # API 端点
├── service/
│   └── QwenService.java             # 业务逻辑
├── client/
│   └── QwenClient.java              # HTTP 客户端
└── dto/
    ├── ChatMessage.java             # 聊天消息
    ├── ChatRequest.java             # 请求 DTO
    ├── ChatResponse.java            # 响应 DTO
    └── ChatDelta.java               # 流式响应 DTO
```

## 技术细节

- **HTTP 客户端**：Spring WebFlux WebClient（异步非阻塞）
- **流式支持**：Server-Sent Events (SSE)，使用 Flux 处理
- **错误处理**：支持 429/5xx 重试（可选，代码中注释默认关闭）
- **超时配置**：可配置的请求超时时间
- **参数验证**：请求参数完整性检查

## 进阶用法

### 自定义温度（创意度）

```json
{
  "messages": [...],
  "model": "qwen-max",
  "temperature": 0.5
}
```

- 0-0.5：逻辑严谨，适合问答
- 0.5-1.5：平衡
- 1.5-2.0：创意十足，适合创意写作

### 自定义核采样

```json
{
  "messages": [...],
  "model": "qwen-max",
  "top_p": 0.8
}
```

范围：(0, 1)，越小越保守。

## 故障排查

### 检查配置

```bash
curl http://localhost:8080/api/qwen/health
```

### 启用调试日志

在 `application.properties` 中添加：

```properties
logging.level.com.example.Contract_review.qwen=DEBUG
logging.level.org.springframework.web.reactive=DEBUG
```

### 测试 API Key

```bash
curl -X POST "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions" \
  -H "Authorization: Bearer sk-xxx" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen-max",
    "messages": [{"role": "user", "content": "test"}],
    "stream": false
  }'
```

## 相关链接

- [DashScope 官方文档](https://help.aliyun.com/zh/dashscope/)
- [OpenAI API 兼容接口](https://help.aliyun.com/zh/dashscope/developer-reference/compatible-openai)
- [模型列表](https://help.aliyun.com/zh/dashscope/developer-reference/models-list)
- [计费说明](https://help.aliyun.com/zh/dashscope/billing/)

## 示例代码

### Java 调用示例

```java
@Autowired
private QwenService qwenService;

// 非流式调用
List<ChatMessage> messages = Arrays.asList(
    ChatMessage.builder().role("user").content("你好").build()
);
ChatResponse response = qwenService.chatBlocking(messages, "qwen-max");
System.out.println(response.getContent());

// 流式调用
qwenService.streamChat(messages, "qwen-max")
    .subscribe(delta -> System.out.print(delta.getDelta()));
```

### 使用 Python 调用

```python
import requests
import json

url = "http://localhost:8080/api/qwen/chat"
payload = {
    "messages": [{"role": "user", "content": "你好"}],
    "model": "qwen-max"
}

response = requests.post(url, json=payload)
print(response.json()["content"])
```

## 注意事项

1. **API Key 安全**：不要在代码中硬编码 API Key，使用环境变量
2. **成本控制**：监控 token 使用量，避免产生意外费用
3. **速率限制**：遵守 QPS 限制，实现请求队列
4. **超时设置**：根据网络情况调整 `qwen.timeout`

## 许可证

MIT

---

**最后更新**：2025-10-23
**Qwen 集成版本**：1.0
