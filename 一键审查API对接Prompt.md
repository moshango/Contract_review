# 一键审查后端API接口文档

> 📡 **纯后端接口说明**，适用于任何前端技术栈  
> 🔗 **RESTful API**，标准HTTP协议  
> 📋 **版本**: v1.0 | **最后更新**: 2025-11-03

---

## 📊 核心流程

```
步骤1: 上传文件 → POST /api/parse → 返回解析结果（甲乙方信息）
         ↓
步骤2: 提交审查 → POST /api/qwen/rule-review/one-click-review → 返回审查结果
         ↓
步骤3: 使用返回的minioUrl进行在线预览或下载
```

---

## 🔌 API接口列表

### 接口1: 合同解析

#### 基本信息

| 项目 | 值 |
|-----|---|
| **接口地址** | `POST /api/parse` |
| **Content-Type** | `multipart/form-data` |
| **用途** | 解析合同文件，提取甲乙方信息和条款列表 |
| **耗时** | 约1-2秒 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 | 示例值 |
|--------|------|------|------|--------|
| file | File | ✅ | 合同文件 | test.docx |
| anchors | String | ✅ | 锚点模式 | "generate" |
| returnMode | String | ❌ | 返回格式 | "json" |

**参数说明**:
- `file`: 支持 .docx 和 .doc 格式，大小不超过50MB
- `anchors`: 固定传值 `"generate"`，用于生成批注锚点
- `returnMode`: 固定传值 `"json"`，返回JSON格式

#### 成功响应 (200 OK)

```json
{
  "filename": "技术服务协议.docx",
  "title": "技术服务协议",
  "partyA": "广西商誉猫网络科技有限公司",
  "partyB": "中国银联股份有限公司广西分公司",
  "clauses": [
    {
      "id": "clause_1",
      "anchorId": "anc-c1-a1b2c3d4",
      "heading": "第一条 服务内容",
      "fullText": "甲方委托乙方提供技术开发服务...",
      "startParagraphIndex": 5,
      "endParagraphIndex": 7
    },
    {
      "id": "clause_2",
      "anchorId": "anc-c2-e5f6g7h8",
      "heading": "第二条 服务期限",
      "fullText": "服务期限自合同签订之日起12个月...",
      "startParagraphIndex": 8,
      "endParagraphIndex": 10
    }
  ],
  "meta": {
    "wordCount": 3500,
    "paragraphCount": 45,
    "anchorSourceFilename": "技术服务协议.docx",
    "convertedFromDoc": false
  }
}
```

**字段说明**:
- `filename`: 原始文件名
- `title`: 合同标题（从文档中提取）
- `partyA`: 甲方名称（自动识别）
- `partyB`: 乙方名称（自动识别）
- `clauses[]`: 条款列表
  - `id`: 条款唯一ID
  - `anchorId`: 锚点ID（用于精确批注定位）
  - `heading`: 条款标题
  - `fullText`: 条款完整文本
- `meta`: 元数据信息

#### 错误响应

**400 Bad Request** - 文件格式错误:
```json
{
  "error": "文件格式不支持",
  "message": "仅支持 .docx 和 .doc 格式"
}
```

**500 Internal Server Error** - 解析失败:
```json
{
  "error": "文件解析失败",
  "message": "文档可能已损坏或格式异常"
}
```

#### cURL测试示例

```bash
curl -X POST http://localhost:8080/api/parse \
  -F "file=@test.docx" \
  -F "anchors=generate" \
  -F "returnMode=json" \
  | jq .
```

---

### 接口2: 一键审查

#### 基本信息

| 项目 | 值 |
|-----|---|
| **接口地址** | `POST /api/qwen/rule-review/one-click-review` |
| **Content-Type** | `multipart/form-data` |
| **用途** | 执行完整的合同审查流程 |
| **耗时** | 约5-10秒（取决于合同长度） |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 | 可选值 |
|--------|------|------|------|--------|
| file | File | ✅ | 合同文件（与接口1相同的文件） | - |
| stance | String | ❌ | 审查立场 | "A方", "B方", "neutral" |

**参数说明**:
- `file`: 必须是.docx或.doc格式，大小不超过50MB
- `stance`: 默认值为"neutral"
  - `"neutral"` - 中立角度，客观全面审查
  - `"A方"` 或 `"甲方"` - 从甲方视角审查，重点关注甲方风险
  - `"B方"` 或 `"乙方"` - 从乙方视角审查，重点关注乙方风险

#### 成功响应 (200 OK)

**场景1: 命中规则，审查完成**

```json
{
  "success": true,
  "message": "合同审查完成",
  "filename": "技术服务协议_一键审查_A方_20251103_145030.docx",
  "fileSize": 245678,
  "issuesCount": 5,
  "processingTime": 8234,
  "minioUrl": "http://127.0.0.1:9000/contract-review/reports/技术服务协议_一键审查_A方_20251103_145030.docx",
  "originalUrl": "http://127.0.0.1:9000/contract-review/contracts/技术服务协议.docx",
  "savedToMinio": true
}
```

**场景2: 未命中规则，跳过AI审查**

```json
{
  "success": true,
  "message": "未命中任何规则，已跳过LLM并保存原文档",
  "issuesCount": 0,
  "processingTime": 1234,
  "minioUrl": "http://127.0.0.1:9000/contract-review/reports/技术服务协议_一键审查_未命中规则.docx",
  "originalUrl": "http://127.0.0.1:9000/contract-review/contracts/技术服务协议.docx",
  "savedToMinio": true
}
```

**字段说明**:
- `success`: 操作是否成功
- `message`: 操作结果消息
- `filename`: 生成的文件名（本地保存）
- `fileSize`: 文件大小（字节）
- `issuesCount`: 检出的问题数量
- `processingTime`: 处理耗时（毫秒）
- `minioUrl`: MinIO存储的审查结果URL（用于在线预览）
- `originalUrl`: 原始文件的MinIO URL
- `savedToMinio`: 是否成功保存到MinIO

#### 错误响应

**400 Bad Request** - 参数错误:

```json
{
  "success": false,
  "error": "文件不能为空"
}
```

```json
{
  "success": false,
  "error": "仅支持 .docx 和 .doc 格式"
}
```

**500 Internal Server Error** - 服务错误:

```json
{
  "success": false,
  "error": "一键审查失败: Qwen服务不可用",
  "timestamp": 1698734567890
}
```

```json
{
  "success": false,
  "error": "文档解析失败：带锚点文档生成失败",
  "timestamp": 1698734567890
}
```

#### cURL测试示例

**测试甲方立场**:
```bash
curl -X POST http://localhost:8080/api/qwen/rule-review/one-click-review \
  -F "file=@test.docx" \
  -F "stance=A方" \
  | jq .
```

**测试乙方立场**:
```bash
curl -X POST http://localhost:8080/api/qwen/rule-review/one-click-review \
  -F "file=@test.docx" \
  -F "stance=B方" \
  | jq .
```

**测试中立立场**:
```bash
curl -X POST http://localhost:8080/api/qwen/rule-review/one-click-review \
  -F "file=@test.docx" \
  -F "stance=neutral" \
  | jq .
```

---

### 接口3: 检查服务状态（可选）

#### 基本信息

| 项目 | 值 |
|-----|---|
| **接口地址** | `GET /api/qwen/rule-review/status` |
| **用途** | 检查Qwen AI服务是否可用 |
| **耗时** | <100ms |

#### 请求参数

无需参数

#### 成功响应 (200 OK)

```json
{
  "success": true,
  "qwenAvailable": true,
  "message": "✓ Qwen服务已就绪",
  "timestamp": 1698734567890,
  "config": {
    "model": "qwen-max-latest",
    "hasApiKey": true,
    "hasBaseUrl": true
  },
  "endpoints": {
    "review": "POST /api/qwen/rule-review/review",
    "status": "GET /api/qwen/rule-review/status",
    "config": "GET /api/qwen/rule-review/config"
  }
}
```

#### cURL测试示例

```bash
curl -X GET http://localhost:8080/api/qwen/rule-review/status | jq .
```

---

### 接口4: 文件代理下载（辅助接口）

#### 基本信息

| 项目 | 值 |
|-----|---|
| **接口地址** | `GET /api/preview/proxy` |
| **用途** | 通过后端代理下载MinIO文件（避免跨域） |
| **耗时** | 取决于文件大小 |

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 | 示例值 |
|--------|------|------|------|--------|
| fileName | String | ✅ | MinIO对象名称 | "reports/xxx.docx" |

**参数说明**:
- `fileName`: 从 `minioUrl` 中提取的对象名称（不含bucket和域名）
- 例如: minioUrl为 `http://localhost:9000/contract-review/reports/test.docx`
- 则fileName为 `reports/test.docx`

#### 成功响应 (200 OK)

返回文件的二进制流，Content-Type为文件的MIME类型。

#### cURL测试示例

```bash
curl -X GET "http://localhost:8080/api/preview/proxy?fileName=reports/test.docx" \
  -o downloaded.docx
```

---

## 📋 完整调用流程示例

### 使用cURL完整测试

```bash
#!/bin/bash

# 步骤1: 解析合同
echo "步骤1: 解析合同..."
curl -X POST http://localhost:8080/api/parse \
  -F "file=@test.docx" \
  -F "anchors=generate" \
  -F "returnMode=json" \
  -o parse_result.json

# 提取甲乙方信息
cat parse_result.json | jq '{partyA, partyB}'

# 步骤2: 执行一键审查（甲方立场）
echo "步骤2: 执行一键审查..."
curl -X POST http://localhost:8080/api/qwen/rule-review/one-click-review \
  -F "file=@test.docx" \
  -F "stance=A方" \
  -o review_result.json

# 提取审查结果
cat review_result.json | jq '{success, issuesCount, minioUrl}'

# 步骤3: 下载审查结果文档
echo "步骤3: 下载审查结果..."
MINIO_URL=$(cat review_result.json | jq -r '.minioUrl')
OBJECT_NAME=$(echo $MINIO_URL | sed 's|.*/contract-review/||')

curl -X GET "http://localhost:8080/api/preview/proxy?fileName=$OBJECT_NAME" \
  -o result.docx

echo "完成！审查结果已保存到 result.docx"
```

### 使用Python测试

```python
import requests

BASE_URL = "http://localhost:8080"

# 步骤1: 解析合同
print("步骤1: 解析合同...")
with open('test.docx', 'rb') as f:
    files = {'file': f}
    data = {'anchors': 'generate', 'returnMode': 'json'}
    response = requests.post(f'{BASE_URL}/api/parse', files=files, data=data)
    parse_result = response.json()

print(f"甲方: {parse_result['partyA']}")
print(f"乙方: {parse_result['partyB']}")

# 步骤2: 执行一键审查
print("\n步骤2: 执行一键审查...")
with open('test.docx', 'rb') as f:
    files = {'file': f}
    data = {'stance': 'A方'}
    response = requests.post(
        f'{BASE_URL}/api/qwen/rule-review/one-click-review',
        files=files,
        data=data,
        timeout=60
    )
    review_result = response.json()

print(f"审查成功: {review_result['success']}")
print(f"检出问题: {review_result['issuesCount']}个")
print(f"耗时: {review_result['processingTime']}ms")
print(f"文件URL: {review_result['minioUrl']}")

# 步骤3: 下载结果
print("\n步骤3: 下载结果...")
minio_url = review_result['minioUrl']
object_name = minio_url.split('/contract-review/')[-1]
proxy_url = f'{BASE_URL}/api/preview/proxy?fileName={object_name}'

response = requests.get(proxy_url)
with open('result.docx', 'wb') as f:
    f.write(response.content)

print("完成！文件已保存到 result.docx")
```

### 使用Java测试

```java
import java.io.*;
import java.net.http.*;
import java.nio.file.*;

public class OneClickReviewTest {
    
    private static final String BASE_URL = "http://localhost:8080";
    
    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        
        // 步骤1: 解析合同
        System.out.println("步骤1: 解析合同...");
        HttpRequest parseRequest = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/parse"))
            .header("Content-Type", "multipart/form-data; boundary=----Boundary")
            .POST(buildMultipartBody("test.docx", 
                Map.of("anchors", "generate", "returnMode", "json")))
            .build();
        
        HttpResponse<String> parseResponse = client.send(
            parseRequest, 
            HttpResponse.BodyHandlers.ofString()
        );
        
        System.out.println("解析结果: " + parseResponse.body());
        
        // 步骤2: 执行一键审查
        System.out.println("\n步骤2: 执行一键审查...");
        HttpRequest reviewRequest = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/qwen/rule-review/one-click-review"))
            .header("Content-Type", "multipart/form-data; boundary=----Boundary")
            .POST(buildMultipartBody("test.docx", 
                Map.of("stance", "A方")))
            .timeout(Duration.ofSeconds(60))
            .build();
        
        HttpResponse<String> reviewResponse = client.send(
            reviewRequest, 
            HttpResponse.BodyHandlers.ofString()
        );
        
        System.out.println("审查结果: " + reviewResponse.body());
    }
}
```

---

## 🔐 安全说明

### 文件大小限制

后端默认限制：**50MB**

如需调整，修改 `application.properties`:
```properties
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB
```

### 超时配置

建议前端设置请求超时：
- 解析API: 10秒
- 一键审查API: 60秒

### CORS配置

后端已配置CORS，支持跨域请求。

允许的来源：
- `http://localhost:*`
- `http://127.0.0.1:*`
- `https://ai.matetrip.cn`

---

## 📊 性能指标

### 典型合同（10页，3000字）

| 阶段 | 耗时 | 说明 |
|------|------|------|
| 文件上传 | 200-500ms | 取决于网络 |
| 合同解析 | 800-1200ms | 接口1 |
| 规则匹配 | 200-400ms | 接口2步骤1 |
| AI审查 | 3000-5000ms | 接口2步骤2 |
| 文档批注 | 800-1200ms | 接口2步骤3 |
| MinIO保存 | 300-600ms | 接口2步骤4 |
| **总计** | **6-9秒** | 完整流程 |

### 并发支持

- 最大并发: 10个请求
- 超过并发数将排队处理

---

## 🧪 Postman测试集合

### 导入说明

1. 打开Postman
2. 点击 **Import**
3. 选择文件: `一键审查API.postman_collection.json`
4. 导入成功后即可测试

### 测试用例列表

| 序号 | 名称 | 说明 |
|-----|------|------|
| 1 | 合同解析（获取甲乙方） | 测试解析API |
| 2 | 一键审查-甲方立场 | 测试甲方视角审查 |
| 3 | 一键审查-乙方立场 | 测试乙方视角审查 |
| 4 | 一键审查-中立立场 | 测试中立视角审查 |
| 5 | 检查Qwen服务状态 | 测试服务可用性 |
| 6 | 文件代理下载 | 测试文件下载 |

---

## ⚠️ 重要注意事项

### 1. stance参数值必须准确

```
✅ 正确值:
  - "A方"
  - "B方"  
  - "neutral"

❌ 错误值（不会生效）:
  - "A"
  - "B"
  - "甲方"
  - "乙方"
  - "NEUTRAL"
```

### 2. 文件对象需要复用

两次API调用需要使用**同一个文件对象**：
- 第一次: `/api/parse` 用于解析
- 第二次: `/api/qwen/rule-review/one-click-review` 用于审查

### 3. minioUrl的使用

返回的 `minioUrl` 有两种使用方式：

**方式A: 直接访问（需要MinIO公开访问）**
```
直接使用: http://localhost:9000/contract-review/reports/xxx.docx
```

**方式B: 通过代理访问（推荐，避免跨域）**
```
从minioUrl提取objectName: reports/xxx.docx
使用代理: /api/preview/proxy?fileName=reports/xxx.docx
```

### 4. 响应时间较长

一键审查API耗时5-10秒，前端需要：
- 设置合理的超时时间（60秒）
- 显示加载状态
- 避免重复提交

---

## 📈 错误码说明

| HTTP状态码 | 含义 | 可能原因 |
|-----------|------|---------|
| 200 | 成功 | 操作正常完成 |
| 400 | 参数错误 | 文件格式不对、参数缺失 |
| 413 | 文件过大 | 文件超过50MB |
| 500 | 服务器错误 | Qwen服务不可用、文档解析失败 |
| 503 | 服务不可用 | 后端服务未启动 |
| 504 | 网关超时 | 处理时间超过60秒 |

---

## 🔄 业务逻辑说明

### 审查流程（后端自动执行）

```
1. 验证文件 → 检查格式和大小
2. 保存原文件 → 上传到MinIO contracts目录
3. 解析合同 → 提取条款、生成锚点
4. 规则匹配 → 匹配审查规则
5. AI审查 → 调用Qwen API（如果命中规则）
6. 文档批注 → 将审查结果插入文档
7. 保存结果 → 本地+MinIO
8. 返回响应 → JSON格式
```

### 规则匹配逻辑

- 如果**未命中任何规则**: 跳过AI审查，直接保存原文档
- 如果**命中规则**: 执行完整AI审查流程

### 立场对审查的影响

| 立场 | 审查重点 | 建议来源 |
|-----|---------|---------|
| **neutral** | 全面客观 | 通用建议 |
| **A方/甲方** | 甲方风险 | suggestA字段 |
| **B方/乙方** | 乙方风险 | suggestB字段 |

---

## 🛠️ 后端配置说明

### 必需配置

在 `application.properties` 中必须配置：

```properties
# Qwen API配置（必需）
qwen.api-key=sk-xxxxxxxxxxxxx
qwen.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1
qwen.model=qwen-max-latest

# MinIO配置（可选，用于文件存储）
minio.endpoint=http://127.0.0.1:9000
minio.access-key=minioadmin
minio.secret-key=minioadmin
minio.bucket-name=contract-review
```

### 可选配置

```properties
# 文件大小限制
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB

# Aspose转换配置
aspose.conversion-timeout-seconds=30

# 规则配置
rules.default-contract-type=ALL
```

---

## 📝 Postman环境变量

在Postman中设置环境变量：

| 变量名 | 值 | 说明 |
|--------|---|------|
| baseUrl | http://localhost:8080 | 后端基础URL |
| testFile | /path/to/test.docx | 测试文件路径 |

---

## 🐛 常见问题

### Q1: 调用返回400错误

**原因**: 参数错误

**检查**:
- file参数是否正确传递
- stance参数值是否准确（"A方"/"B方"/"neutral"）
- Content-Type是否为multipart/form-data

### Q2: 调用返回500错误

**原因**: 服务器内部错误

**检查**:
- Qwen API配置是否正确
- 后端日志错误信息
- MinIO服务是否启动

### Q3: 请求超时

**原因**: 审查时间过长或网络问题

**解决**:
- 增加前端超时时间到60秒
- 检查网络连接
- 检查Qwen API响应速度

### Q4: minioUrl无法访问

**原因**: MinIO服务未启动或跨域问题

**解决**:
- 使用代理接口: `/api/preview/proxy?fileName=...`
- 检查MinIO服务状态
- 检查MinIO bucket权限

---

## 📞 技术支持

### 后端日志关键字

调试时在后端日志中搜索：

| 关键字 | 含义 |
|--------|------|
| "开始一键审查流程" | 请求已到达 |
| "文件: xxx, 立场: xxx" | 参数接收正常 |
| "规则匹配完成" | 规则匹配阶段 |
| "Qwen审查完成" | AI审查完成 |
| "文档批注完成" | 批注生成完成 |
| "MinIO URL" | 文件保存成功 |
| "一键审查完成" | 整个流程完成 |

### API测试工具

**Windows**:
```bash
cd Contract_review
测试一键审查API.bat
```

**Linux/Mac**:
```bash
curl -X POST http://localhost:8080/api/parse \
  -F "file=@test.docx" \
  -F "anchors=generate" \
  -F "returnMode=json"
```

---

## 📚 相关文档

### 后端详细文档

- `Contract_review/文档中心/01_API接口说明/03_一键式审查API快速参考.md`
- `Contract_review/文档中心/02_实现和修复总结/09_一键式审查功能实现.md`

### 测试工具

- `一键审查API.postman_collection.json` - Postman测试集合
- `测试一键审查API.bat` - 自动化测试脚本

---

## 🎯 快速参考

### 最小请求示例

```http
POST /api/qwen/rule-review/one-click-review HTTP/1.1
Host: localhost:8080
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary

------WebKitFormBoundary
Content-Disposition: form-data; name="file"; filename="test.docx"
Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document

[文件二进制数据]
------WebKitFormBoundary
Content-Disposition: form-data; name="stance"

A方
------WebKitFormBoundary--
```

### 最小响应示例

```json
{
  "success": true,
  "minioUrl": "http://xxx/reports/xxx.docx",
  "issuesCount": 5
}
```

---

## 📋 对接检查清单

### 后端准备

- [ ] 后端服务已启动（http://localhost:8080）
- [ ] Qwen API已配置（application.properties）
- [ ] MinIO服务已启动（可选）
- [ ] 测试文件已准备（.docx格式）

### API测试

- [ ] `/api/parse` 返回200
- [ ] `/api/qwen/rule-review/one-click-review` 返回200
- [ ] `/api/qwen/rule-review/status` 返回qwenAvailable=true
- [ ] 响应格式符合文档说明

### 前端对接

- [ ] 能正确调用两个API
- [ ] 能正确解析响应JSON
- [ ] 能处理错误响应
- [ ] 能使用minioUrl预览文档

---

**文档版本**: v1.0（纯后端接口版）  
**适用范围**: 任何前端技术栈（React/Vue/Angular/原生等）  
**最后更新**: 2025-11-03
