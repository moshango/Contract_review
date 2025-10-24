# Qwen 配置测试

## 修复内容

### 问题1：Qwen服务显示"未配置"
- **原因**: QwenClient.getConfig() 返回的键名与 QwenRuleReviewService.isQwenAvailable() 期望的键名不匹配
  - QwenClient 返回: "baseUrl", "apiKeySet"
  - QwenRuleReviewService 期望: "base-url", "api-key"

- **修复**: 更新 QwenClient.getConfig() 方法
  ```java
  // 修改前：
  config.put("baseUrl", baseUrl);
  config.put("apiKeySet", String.valueOf(apiKey != null && !apiKey.isEmpty()));
  
  // 修改后：
  config.put("base-url", baseUrl);
  config.put("api-key", apiKey != null ? apiKey : "");
  ```

### 问题2：favicon.ico 404错误
- **原因**: static 文件夹中没有 favicon.ico 文件
- **修复**: 添加 favicon.ico 到 src/main/resources/static/

## 验证步骤

### 1. 重新启动应用
```bash
mvn spring-boot:run
```

### 2. 检查服务状态API
```bash
curl http://localhost:8080/api/qwen/rule-review/status
```

**期望响应**:
```json
{
  "success": true,
  "qwenAvailable": true,
  "message": "✓ Qwen服务已就绪",
  "config": {
    "base-url": "https://dashscope.aliyuncs.com/compatible-mode/v1",
    "model": "qwen-vl-max-latest",
    "api-key": "sk-3098eac61f7140f4aeb20cac3a030ed2"
  }
}
```

### 3. 检查日志
```bash
tail -f logs/application.log
```

应该看到:
```
✓ Qwen服务可用
```

而不是:
```
✗ Qwen服务不可用: API Key 或 Base URL 未配置
```

## 修改的文件

1. `src/main/java/com/example/Contract_review/qwen/client/QwenClient.java` (getConfig方法)
2. `src/main/resources/static/favicon.ico` (新增)

## 编译状态

✅ BUILD SUCCESS (0 errors)
