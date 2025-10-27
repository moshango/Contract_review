# Qwen 集成 - 完整实现总结

## 🎯 完成情况

✅ **全部完成** - 通义千问（Qwen）OpenAI 兼容接口集成

### 核心功能

- ✅ 非流式聊天 API (`POST /api/qwen/chat`)
- ✅ 流式聊天 API (`POST /api/qwen/stream`)
- ✅ 健康检查端点 (`GET /api/qwen/health`)
- ✅ 可配置的 Base URL 和 API Key
- ✅ 错误处理与重试钩子
- ✅ 完整的 DTO 验证

## 📦 文件结构

```
src/main/java/com/example/Contract_review/qwen/
├── controller/
│   └── QwenController.java              # REST API 端点
├── service/
│   └── QwenService.java                 # 业务逻辑
├── client/
│   └── QwenClient.java                  # HTTP 客户端（WebFlux）
└── dto/
    ├── ChatMessage.java                 # 消息 DTO
    ├── ChatRequest.java                 # 请求 DTO
    ├── ChatResponse.java                # 响应 DTO
    └── ChatDelta.java                   # 流式增量 DTO

src/main/resources/
└── application.properties                # 配置文件（含 Qwen 配置）

docs/
└── Qwen集成指南.md                       # 详细文档

test-qwen.sh                              # 测试脚本
```

## 🚀 快速开始

### 1. 编译项目

```bash
cd Contract_review
mvn clean package -DskipTests
```

### 2. 配置 API Key

```bash
# 方式 1：环境变量（推荐）
export DASHSCOPE_API_KEY="sk-xxxxxxxxxxxxxxxxxxxx"

# 方式 2：application.properties
# qwen.api-key=sk-xxxxxxxxxxxxxxxxxxxx
```

### 3. 启动应用

```bash
# Maven
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8888" -DskipTests

# 或 Java 直接运行
java -jar target/Contract_review-0.0.1-SNAPSHOT.jar --server.port=8888
```

### 4. 测试 API

```bash
# 健康检查
curl -X GET http://localhost:8888/api/qwen/health

# 非流式聊天
curl -X POST http://localhost:8888/api/qwen/chat \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [{"role": "user", "content": "你好"}],
    "model": "qwen-max"
  }'

# 流式聊天
curl -X POST http://localhost:8888/api/qwen/stream \
  -H "Content-Type: application/json" \
  -N \
  -d '{
    "messages": [{"role": "user", "content": "说一句话"}],
    "model": "qwen-max"
  }'
```

## 📋 配置说明

### application.properties

```properties
# API 密钥 (从环境变量或直接设置)
qwen.api-key=${DASHSCOPE_API_KEY:}

# 基础 URL (默认: 中国区)
qwen.base-url=${QWEN_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}

# 模型名称
qwen.model=qwen-max

# 超时时间 (秒)
qwen.timeout=30
```

### 地区选择

```bash
# 中国区（默认）
export QWEN_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"

# 新加坡区
export QWEN_BASE_URL="https://dashscope-intl.aliyuncs.com/compatible-mode/v1"
```

## 🔌 API 接口

### 非流式聊天

**请求**：
```http
POST /api/qwen/chat
Content-Type: application/json

{
  "messages": [
    {"role": "user", "content": "你好"}
  ],
  "model": "qwen-max"
}
```

**响应**：
```json
{
  "id": "chatcmpl-...",
  "content": "你好！...",
  "model": "qwen-max",
  "finishReason": "stop"
}
```

### 流式聊天

**请求**：
```http
POST /api/qwen/stream
Content-Type: application/json

{
  "messages": [
    {"role": "user", "content": "写一首诗"}
  ],
  "model": "qwen-max"
}
```

**响应** (SSE)：
```
data: {"delta":"春","done":false}

data: {"delta":"日","done":false}

...

data: {"delta":"","done":true,"finishReason":"stop"}
```

### 健康检查

**请求**：
```http
GET /api/qwen/health
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

## 🛠️ 技术细节

### 依赖

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

### 关键类

| 类 | 功能 |
|---|---|
| `QwenClient` | HTTP 客户端，使用 WebClient 进行异步请求 |
| `QwenService` | 业务逻辑层，提供同步和异步方法 |
| `QwenController` | REST 控制器，暴露 API 端点 |
| `ChatRequest` | 请求模型，包含消息、模型、温度等参数 |
| `ChatResponse` | 响应模型，包含内容、完成原因等 |
| `ChatDelta` | 流式增量模型，用于SSE解析 |

### 错误处理

- **404**: Base URL 不正确，检查地区配置
- **401**: API Key 无效，检查 DASHSCOPE_API_KEY
- **429**: 速率限制，减低请求频率
- **5xx**: 服务端问题，稍后重试

### 重试机制

代码中的重试功能默认关闭（`ENABLE_RETRY = false`）。启用：

```java
private static final boolean ENABLE_RETRY = true;  // 启用重试
private static final int MAX_RETRIES = 3;
private static final long RETRY_DELAY_MS = 1000;
```

## 🧪 测试

### 运行测试脚本

```bash
bash test-qwen.sh
```

### 手动测试

```bash
# 1. 检查服务健康
curl -X GET http://localhost:8888/api/qwen/health

# 2. 测试请求（会提示缺少 API Key）
curl -X POST http://localhost:8888/api/qwen/chat \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"test"}],"model":"qwen-max"}'

# 3. 配置 API Key 后重新测试
export DASHSCOPE_API_KEY="your-api-key"
# 重启应用...
# 重新测试 curl 命令
```

## 📚 相关链接

- [获取 API Key](https://dashscope.console.aliyun.com/)
- [官方文档](https://help.aliyun.com/zh/dashscope/)
- [OpenAI 兼容接口](https://help.aliyun.com/zh/dashscope/developer-reference/compatible-openai)
- [模型列表](https://help.aliyun.com/zh/dashscope/developer-reference/models-list)

## 📖 完整文档

详见 `docs/Qwen集成指南.md`

## 🎨 集成示例

### 在合同审查中使用 Qwen

```java
@Service
public class ContractReviewService {
    @Autowired
    private QwenService qwenService;

    public String reviewContract(String contractText) {
        List<ChatMessage> messages = Arrays.asList(
            ChatMessage.builder()
                .role("system")
                .content("你是一个专业的合同审查顾问")
                .build(),
            ChatMessage.builder()
                .role("user")
                .content("请审查以下合同：\n" + contractText)
                .build()
        );

        // 同步调用
        ChatResponse response = qwenService.chatBlocking(messages, "qwen-max");
        return response.getContent();
    }

    public void reviewContractStream(String contractText) {
        List<ChatMessage> messages = Arrays.asList(...);

        // 流式调用
        qwenService.streamChat(messages, "qwen-max")
            .subscribe(delta -> System.out.print(delta.getDelta()));
    }
}
```

## ✨ 特性

- ✅ **异步非阻塞**：使用 Project Reactor 和 WebFlux
- ✅ **流式支持**：Server-Sent Events (SSE)
- ✅ **配置灵活**：支持环境变量和 properties 文件
- ✅ **错误处理**：详细的错误消息和日志
- ✅ **参数验证**：请求内容检验
- ✅ **地区切换**：支持中国区和新加坡区

## 🔐 安全建议

1. 不要在代码中硬编码 API Key
2. 使用环境变量 `DASHSCOPE_API_KEY`
3. 监控 token 消费，避免成本超支
4. 定期轮换 API Key

## 📝 日志配置

启用 Qwen 模块的调试日志：

```properties
logging.level.com.example.Contract_review.qwen=DEBUG
logging.level.org.springframework.web.reactive=DEBUG
```

## 🚨 故障排查

| 问题 | 解决方案 |
|------|--------|
| 404 Not Found | 检查 Base URL 和地区配置 |
| 401 Unauthorized | 检查 API Key 有效性 |
| 429 Too Many Requests | 减低请求频率，检查配额 |
| Connection timeout | 增加 `qwen.timeout` 值 |
| 应用启动失败 | 检查 8888 端口是否被占用 |

## 📦 构建和部署

```bash
# 打包
mvn clean package -DskipTests

# 运行 JAR
java -jar target/Contract_review-0.0.1-SNAPSHOT.jar \
  --server.port=8888 \
  --qwen.api-key=sk-xxx \
  --qwen.model=qwen-max

# Docker 部署（可选）
docker build -t contract-review .
docker run -p 8888:8888 \
  -e DASHSCOPE_API_KEY=sk-xxx \
  contract-review
```

## ✅ 验证清单

- ✅ 编译成功（mvn clean compile）
- ✅ 应用启动成功（mvn spring-boot:run）
- ✅ API 端点可访问（/api/qwen/health）
- ✅ 参数验证正确
- ✅ 错误处理完善
- ✅ 文档完整

## 📅 版本

- **Qwen 集成版本**：1.0
- **Spring Boot**：3.5.6
- **Java**：17
- **创建日期**：2025-10-23

---

**准备好使用 Qwen 了！🚀**

配置好 API Key 后，就可以开始使用通义千问进行合同审查和其他 AI 任务了。
