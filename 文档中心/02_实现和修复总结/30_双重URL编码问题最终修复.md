# 双重URL编码问题最终修复

## 问题确认

前端URL显示双重编码：
```
http://localhost:4000/#/contract-review/editor?fileUrl=http://localhost:9000/contract-review/reports/_____C_______%25E4%25B8%2580%25E9%2594%25AE%25E5%25AE%25A1%25E6%259F%25A5_A_20251031_101048_d1d498d2.docx
```

其中 `%25E4` 说明 `%E4` 被再次编码为 `%25E4`（`%` 被编码为 `%25`）。

## 问题根源

### 编码流程分析

1. **后端生成URL**（第一次编码）：
   ```
   http://localhost:9000/contract-review/reports/_____C_______%E4%B8%80%E9%94%AE%E5%AE%A1%E6%9F%A5_A_...docx
   ```
   - 中文"一键审查"被编码为：`%E4%B8%80%E9%94%AE%E5%AE%A1%E6%9F%A5`

2. **前端构造查询参数**（第二次编码）：
   ```javascript
   encodeURIComponent(row.minioUrl)
   ```
   - 对整个URL进行编码
   - `%E4` → `%25E4`
   - `/` → `%2F`

3. **结果**（双重编码）：
   ```
   %E4 → %25E4
   %2F → %252F
   ```

## 修复方案

### 核心思路

**后端返回未编码的URL，前端统一编码（作为查询参数时）**

### 修复内容

#### 1. 后端修复：`MinioFileService.getFileUrl()`

**修改前**（已编码中文）：
```java
// 对objectName中的中文进行编码
String encodedObjectName = ...编码逻辑...;
String publicUrl = String.format("%s/%s/%s", endpoint, bucket, encodedObjectName);
// 返回: http://localhost:9000/contract-review/reports/_____C_______%E4%B8%80...
```

**修改后**（未编码）：
```java
// 直接使用objectName（不编码），由前端统一编码
String publicUrl = String.format("%s/%s/%s", endpoint, bucket, objectName);
// 返回: http://localhost:9000/contract-review/reports/_____C_______一键审查_A_...
```

#### 2. 后端修复：`FilePreviewController.getSignedEditorConfig()`

**增强URL解析和编码逻辑**：
- 添加调试日志，记录URL解析过程
- 确保从URL中正确解析出 `objectName`
- 对 `objectName` 进行编码用于MinIO代理URL

**关键逻辑**：
```java
// Spring自动解码查询参数，fileUrl是未编码的
java.net.URI uri = new java.net.URI(fileUrl);
String path = uri.getPath(); // /contract-review/reports/_____C_______一键审查_A_...
String objectName = path.substring(("/" + bucket + "/").length());

// 对objectName编码用于代理URL
String encoded = URLEncoder.encode(objectName, UTF_8).replace("+", "%20");
finalDocumentUrl = "http://host.docker.internal:8080/api/preview/proxy?fileName=" + encoded;
```

#### 3. 前端保持不变

前端代码已经正确使用 `encodeURIComponent`：
```javascript
const editorUrl = `/#/contract-review/editor?fileUrl=${encodeURIComponent(fileUrl)}`
```

## 修复后的流程

1. **后端生成URL**（未编码）：
   ```
   http://localhost:9000/contract-review/reports/_____C_______一键审查_A_20251031_101048_d1d498d2.docx
   ```

2. **前端编码**（作为查询参数）：
   ```javascript
   encodeURIComponent(fileUrl)
   ```
   结果：
   ```
   http://localhost:9000/contract-review/reports/_____C_______%E4%B8%80%E9%94%AE%E5%AE%A1%E6%9F%A5_A_20251031_101048_d1d498d2.docx
   ```

3. **Vue Router自动解码**：
   - `route.query.fileUrl` 自动解码为未编码的URL

4. **后端解析和代理**：
   - `FilePreviewController` 接收未编码的URL
   - 解析出 `objectName`（未编码）
   - 对 `objectName` 编码用于MinIO访问

## 测试验证

### 测试步骤

1. **重新打包并重启后端**
   ```bash
   cd Contract_review
   mvn package -DskipTests
   java -jar target/Contract_review-0.0.1-SNAPSHOT.jar
   ```

2. **重新生成审查报告**
   - 上传长文档
   - 执行一键审查
   - 查看日志中的MinIO URL

3. **验证URL格式**

   **预期日志**：
   ```
   生成公开文件URL: objectName=reports/_____C_______一键审查_A_..., url=http://localhost:9000/contract-review/reports/_____C_______一键审查_A_... (未编码，由前端统一编码)
   ```

4. **前端测试**
   - 点击打开文档
   - 检查浏览器地址栏的URL
   - 应该只包含一层编码（Vue Router查询参数的编码）

5. **检查后端日志**
   ```
   接收到的fileUrl参数: http://localhost:9000/contract-review/reports/_____C_______一键审查_A_...
   从URI解析的path: /contract-review/reports/_____C_______一键审查_A_...
   从path前缀匹配得到objectName: reports/_____C_______一键审查_A_...
   构造代理URL: objectName=..., encoded=..., finalUrl=http://host.docker.internal:8080/api/preview/proxy?fileName=...
   ```

6. **OnlyOffice预览**
   - 文档应该能正常加载
   - 批注应该正常显示

## 预期结果

修复后：
- ✅ 后端返回的URL：未编码（包含中文）
- ✅ 前端查询参数：正确编码一次
- ✅ Vue Router解码：恢复为未编码URL
- ✅ 后端解析：从URL中正确提取objectName
- ✅ 后端代理：对objectName编码用于MinIO访问
- ✅ OnlyOffice：能正常加载文档并显示批注

## 注意事项

1. **MinIO直接访问**：
   - 后端返回的URL包含中文，可能无法直接在浏览器访问
   - 但这不影响功能，因为我们使用后端代理URL访问MinIO

2. **兼容性**：
   - 修复后，旧的已编码URL可能无法正常工作
   - 需要重新生成审查报告

3. **日志级别**：
   - `FilePreviewController` 的调试日志使用 `logger.debug()`
   - 如果看不到日志，需要调整日志级别为 `DEBUG`

---

**修复完成时间**：2025-10-31  
**修复状态**：✅ 已编译，待测试验证  
**下一步**：重启应用并测试文档预览功能


