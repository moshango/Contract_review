# Qwen 集成 - 配置更新说明

## 📝 更新说明

已将 Qwen API Key 配置方式从**环境变量**改为**直接在 application.properties 配置文件**中设置。

### ✅ 变更内容

| 项目 | 之前 | 现在 |
|------|------|------|
| **API Key 来源** | 环境变量 `DASHSCOPE_API_KEY` | `application.properties` 文件中 |
| **配置项** | `qwen.api-key=${DASHSCOPE_API_KEY:}` | `qwen.api-key=sk-your-api-key-here` |
| **启动方式** | 需要先设置环境变量 | 直接启动，修改配置文件即可 |
| **适用场景** | 生产环境（安全） | 开发测试环境 |

### 🚀 使用步骤

#### 步骤 1：编辑配置文件

打开 `src/main/resources/application.properties`，找到 Qwen 配置部分：

```properties
# ============================================================
# Qwen (通义千问) Configuration
# ============================================================

# API 密钥 (直接在此配置，无需环境变量)
# 请将 sk-xxxxxxxxxxxxxxxx 替换为实际的 API Key
qwen.api-key=sk-your-api-key-here

# API 基础 URL (中国区)
qwen.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1

# 模型名称 (支持: qwen-max, qwen-plus, qwen-turbo, qwen-long 等)
qwen.model=qwen-max

# 超时时间 (秒)
qwen.timeout=30
```

#### 步骤 2：替换 API Key

将 `sk-your-api-key-here` 替换为你的实际 API Key：

```properties
qwen.api-key=sk-1234567890abcdefghijklmnop
```

#### 步骤 3：启动应用

无需设置环境变量，直接启动：

```bash
mvn spring-boot:run -DskipTests
```

或

```bash
mvn clean compile -DskipTests
java -jar target/Contract_review-0.0.1-SNAPSHOT.jar
```

#### 步骤 4：验证配置

```bash
curl http://localhost:8080/api/qwen/health
```

预期响应：

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

### 🔧 可选配置

#### 切换地区

修改 `qwen.base-url`：

```properties
# 中国区（默认）
qwen.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1

# 新加坡区
qwen.base-url=https://dashscope-intl.aliyuncs.com/compatible-mode/v1
```

#### 切换模型

修改 `qwen.model`：

```properties
qwen.model=qwen-max      # 最强能力（推荐）
qwen.model=qwen-plus     # 均衡
qwen.model=qwen-turbo    # 低成本高速
qwen.model=qwen-long     # 长文本（200K tokens）
```

#### 增加超时时间

修改 `qwen.timeout`：

```properties
qwen.timeout=60  # 60 秒
```

### 📌 Git 提交信息

```
commit ef1c16a
Author: Claude Code

修改 Qwen 配置：API Key 直接在 application.properties 配置文件中

更改：
- API Key 不再依赖环境变量 DASHSCOPE_API_KEY
- 配置项 qwen.api-key 现在直接在 application.properties 文件中设置
- 值为 sk-your-api-key-here (占位符)
- Base URL、模型和超时都添加了合理的默认值
- QwenClient 的 @Value 注解添加了默认值
```

### ⚠️ 注意事项

1. **安全性**：不要将真实 API Key 提交到公开代码库
2. **本地配置**：修改后的 `application.properties` 不提交或加入 `.gitignore`
3. **备份**：修改前备份原始配置文件

### 🔄 恢复使用环境变量

如果需要改回环境变量方式（例如部署到生产环境），修改 `application.properties`：

```properties
qwen.api-key=${DASHSCOPE_API_KEY:}
```

然后设置环境变量：

```bash
export DASHSCOPE_API_KEY="sk-your-api-key"
mvn spring-boot:run -DskipTests
```

---

**更新日期：2025-10-23**
**Qwen 集成版本：1.0**

