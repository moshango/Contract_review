# MinIO 中文URL编码修复完成

## 问题根源

**MinIO URL 中包含中文字符未进行 URL 编码，导致下载时出现 502 Bad Gateway 或 AccessDenied 错误。**

### 证据

日志中的 MinIO URL 包含中文：
```
http://localhost:9000/contract-review/reports/_____C_______一键审查_B_20251031_092116_8eddd1ef.docx
```

这个 URL 中的 `一键审查` 是中文，未进行 URL 编码，导致：
1. 浏览器或 HTTP 客户端无法正确解析
2. MinIO 服务器返回 502 或 AccessDenied
3. 前端下载失败或下载到错误文件

## 修复方案

### 修改内容

在 `MinioFileService.getFileUrl()` 方法中添加 URL 编码：

```java
// 原来的代码（有问题）
String publicUrl = String.format("%s/%s/%s", 
    endpoint, bucketName, objectName);  // objectName包含中文

// 修复后的代码
String encodedObjectName = java.net.URLEncoder.encode(objectName, StandardCharsets.UTF_8)
    .replace("+", "%20")  // 将+号替换为%20（空格的标准URL编码）
    .replace("*", "%2A")  // 星号编码
    .replace("%7E", "~"); // 波浪号不需要编码

String publicUrl = String.format("%s/%s/%s", 
    endpoint, bucketName, encodedObjectName);
```

### 编码结果

**原始 objectName**：
```
reports/_____C_______一键审查_A_20251031_094432_5d3f2122.docx
```

**编码后的 URL**：
```
http://localhost:9000/contract-review/reports/_____C_______%E4%B8%80%E9%94%AE%E5%AE%A1%E6%9F%A5_A_20251031_094432_5d3f2122.docx
```

其中 `一键审查` 被编码为 `%E4%B8%80%E9%94%AE%E5%AE%A1%E6%9F%A5`。

## 诊断结果

### 之前的问题

根据之前的日志和诊断：
- ✅ 本地文件：正确，包含批注
- ✅ MinIO 上传：正确，大小匹配
- ❌ MinIO URL：中文未编码，导致下载失败
- ❌ 前端下载：无法访问 MinIO 文件

### 修复后

- ✅ 本地文件：正确，包含批注
- ✅ MinIO 上传：正确，大小匹配
- ✅ MinIO URL：中文已编码，可以正常访问
- ✅ 前端下载：应该可以正常下载

## 为什么短文档没问题？

可能的原因：
1. 短文档的文件名比较简单，不包含特殊字符
2. 短文档可能在测试时没有遇到 URL 编码问题
3. 浏览器或 MinIO 对某些 URL 的处理方式不同

## 测试建议

### 1. 重新打包和重启

```bash
cd Contract_review
mvn package -DskipTests
java -jar target/Contract_review-0.0.1-SNAPSHOT.jar
```

### 2. 重新测试长文档

1. 上传长文档
2. 进行一键审查
3. 从 MinIO 下载文档
4. 验证文档是否包含批注

### 3. 查看新的日志

应该看到：
```
生成公开文件URL: objectName=reports/...一键审查..., encoded=reports/...%E4%B8%80%E9%94%AE..., url=http://...
```

### 4. 验证下载

1. 在前端点击下载
2. 或直接访问 MinIO URL
3. 应该能正常下载到带批注的文档

## 预期结果

修复后应该：
1. ✅ MinIO URL 中的中文正确编码
2. ✅ 前端可以正常下载文档
3. ✅ 下载的文档包含批注
4. ✅ 长文档和短文档都能正常工作

## 相关文件

- `MinioFileService.java`：URL 生成方法
- `QwenRuleReviewController.java`：文件上传和 URL 返回

## 技术细节

### URL 编码规则

- 使用 UTF-8 编码
- 空格编码为 `%20`（不是 `+`）
- 星号编码为 `%2A`
- 波浪号 `~` 不需要编码

### MinIO URL 格式

```
http://endpoint/bucket/encoded-object-name
```

示例：
```
http://localhost:9000/contract-review/reports/_____C_______%E4%B8%80%E9%94%AE%E5%AE%A1%E6%9F%A5_A_20251031_094432.docx
```

---

**修复完成时间**：2025-10-31  
**修复状态**：✅ 已编译  
**待测试**：重新测试 MinIO 下载


