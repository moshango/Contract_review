# LLM URL支持测试指南

## 📋 测试目标

确定您当前使用的LLM（Qwen、ChatGPT、Claude等）是否支持通过URL访问文件，以便选择合适的MinIO集成方案。

## 🔍 测试方法

### 方法一：使用测试脚本（推荐）

1. **启动您的服务**
```bash
mvn spring-boot:run -DskipTests
```

2. **运行测试脚本**
```bash
bash test-llm-url-support.sh
```

3. **查看测试结果**
脚本会自动测试多种URL访问场景并给出建议。

### 方法二：手动测试

#### 测试1：基础URL访问

**使用curl测试：**
```bash
curl -X POST http://localhost:8080/api/qwen/chat \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {
        "role": "system", 
        "content": "你是一个文件分析助手。请告诉我你是否能够访问和分析通过URL提供的文件内容。"
      },
      {
        "role": "user", 
        "content": "请访问这个URL并告诉我文件内容：https://httpbin.org/json"
      }
    ],
    "model": "qwen-max"
  }'
```

**预期结果分析：**
- ✅ **支持URL访问**：LLM返回了httpbin.org的内容
- ❌ **不支持URL访问**：LLM明确表示无法访问外部URL
- ⚠️ **不确定**：LLM响应模糊，需要进一步测试

#### 测试2：文件URL测试

```bash
curl -X POST http://localhost:8080/api/qwen/chat \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {
        "role": "system", 
        "content": "你是一个合同审查专家。用户会提供合同文件的URL，你需要分析文件内容。"
      },
      {
        "role": "user", 
        "content": "请分析这个合同文件：http://localhost:9000/contract-review/contract-123.docx"
      }
    ],
    "model": "qwen-max"
  }'
```

#### 测试3：MinIO URL测试（如果MinIO已部署）

```bash
curl -X POST http://localhost:8080/api/qwen/chat \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {
        "role": "system", 
        "content": "你是一个文件分析助手。请尝试访问MinIO存储中的文件。"
      },
      {
        "role": "user", 
        "content": "请访问这个MinIO文件URL并分析内容：http://localhost:9000/contract-review/test-file.txt"
      }
    ],
    "model": "qwen-max"
  }'
```

### 方法三：Web界面测试

1. **打开浏览器访问**：`http://localhost:8080`
2. **进入规则审查模块**
3. **在聊天框中输入测试问题**：

```
请访问这个URL并告诉我内容：https://httpbin.org/json
```

4. **观察LLM的响应**

## 📊 结果判断标准

### ✅ LLM支持URL访问的特征：
- LLM能够访问外部URL并返回内容
- LLM能够理解文件URL格式
- LLM能够处理MinIO URL
- LLM对无效URL给出明确的错误信息

### ❌ LLM不支持URL访问的特征：
- LLM明确表示无法访问外部URL
- LLM建议用户直接提供文件内容
- LLM对URL请求没有实际的文件访问行为
- LLM返回通用的"无法访问"消息

### ⚠️ 不确定的情况：
- LLM响应模糊
- LLM返回错误但不确定是URL访问问题还是其他问题
- LLM部分支持（某些URL可以，某些不可以）

## 🎯 根据测试结果选择方案

### 如果LLM支持URL访问
**推荐方案一：最小改动方案**
- 将文件上传到MinIO
- LLM通过MinIO URL访问文件
- 保持现有API不变

### 如果LLM不支持URL访问
**推荐方案二：混合存储方案**
- 本地缓存 + MinIO存储
- LLM通过文件内容访问
- 渐进式迁移

### 如果不确定
**推荐方案二：混合存储方案**
- 先实现混合存储
- 逐步测试URL访问
- 根据测试结果调整

## 🔧 不同LLM的URL支持情况

### Qwen (通义千问)
- **理论支持**：Qwen-VL模型支持图像URL访问
- **实际测试**：需要验证是否支持文档URL访问
- **建议**：重点测试qwen-vl-max-latest模型

### ChatGPT/OpenAI
- **支持情况**：GPT-4V支持图像URL，文档URL支持有限
- **限制**：可能需要特殊格式或预处理
- **建议**：测试文件URL访问能力

### Claude
- **支持情况**：Claude-3支持图像URL，文档URL支持待验证
- **特点**：对URL访问有安全限制
- **建议**：测试内部URL访问

## 📝 测试记录模板

```
测试时间：____
LLM类型：____
模型版本：____
测试结果：____
支持URL访问：是/否/不确定
推荐方案：____
备注：____
```

## 🚀 下一步行动

1. **运行测试脚本**：`bash test-llm-url-support.sh`
2. **记录测试结果**
3. **根据结果选择MinIO集成方案**
4. **开始实现选定的方案**

---

**注意**：测试结果可能因LLM版本、网络环境、安全策略等因素而有所不同。建议多次测试以确保结果的准确性。

