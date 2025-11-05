# 双重URL编码问题修复

## 问题描述

前端URL中出现了双重编码：
```
http://localhost:9000/contract-review/reports%252F_____C_______%25E4%25B8%2580%25E9%2594%25AE%25E5%25AE%25A1%25E6%259F%25A5_B_20251031_095337_0e8c2781.docx
```

正确的应该是：
```
http://localhost:9000/contract-review/reports/_____C_______%E4%B8%80%E9%94%AE%E5%AE%A1%E6%9F%A5_B_20251031_095337_0e8c2781.docx
```

## 问题根源

### 编码流程

1. **后端生成URL**（已编码中文）：
   ```
   http://localhost:9000/contract-review/reports/_____C_______%E4%B8%80%E9%94%AE%E5%AE%A1%E6%9F%A5_B_...docx
   ```
   - 中文"一键审查"编码为：`%E4%B8%80%E9%94%AE%E5%AE%A1%E6%9F%A5`
   - 路径分隔符`/`保留（不编码）

2. **前端构造查询参数**（再次编码）：
   ```javascript
   const editorUrl = `/#/contract-review/editor?fileUrl=${encodeURIComponent(row.minioUrl)}`
   ```
   - `encodeURIComponent` 对整个URL进行编码
   - 已编码的 `%E4` 被编码为 `%25E4`
   - 路径分隔符 `/` 被编码为 `%2F`

3. **结果**（双重编码）：
   ```
   %E4 → %25E4
   %2F → %252F
   ```

## 修复方案

### 方案1：后端返回未编码URL（推荐）

**后端修改**：返回完全未编码的URL，只保留必要的格式。

**优点**：
- 前端统一编码，逻辑清晰
- 避免双重编码问题

**缺点**：
- MinIO直接访问时可能无法处理中文路径（但可以通过后端代理解决）

### 方案2：后端返回已编码URL，前端不编码

**前端修改**：检测URL是否已编码，如果已编码就不编码。

**优点**：
- 后端URL可以直接访问MinIO

**缺点**：
- 前端逻辑复杂，需要检测编码状态

### 方案3：后端返回已编码URL，前端先解码再编码

**前端修改**：先解码，再编码。

**问题**：
- 无法区分URL是否已编码

## 采用的修复方案

采用**方案1**的改进版本：

1. **后端**：返回URL时，只编码文件名中的中文，不编码路径分隔符
   - 已实现 ✅

2. **前端**：使用 `encodeURIComponent` 编码（标准做法）
   - 但Vue Router会自动解码查询参数
   - 所以传给后端时应该已经是正确的URL

3. **后端代理**：在 `FilePreviewController` 中正确处理URL解析
   - 需要支持处理已编码和未编码的URL

## 实际修复

### 后端修复（已完成）

```java
// MinioFileService.getFileUrl()
// 只编码非ASCII字符，保留路径分隔符
StringBuilder encoded = new StringBuilder();
for (int i = 0; i < objectName.length(); i++) {
    char c = objectName.charAt(i);
    if (c == '/') {
        encoded.append(c);  // 路径分隔符不编码
    } else if (c >= 32 && c < 127 && c != '%') {
        encoded.append(c);  // ASCII字符不编码
    } else {
        // 只编码中文等非ASCII字符
        String encodedChar = URLEncoder.encode(String.valueOf(c), UTF_8);
        encoded.append(encodedChar);
    }
}
```

### 前端修复（需要验证）

Vue Router会自动解码查询参数，所以 `route.query.fileUrl` 应该是正确解码的URL。

但后端 `FilePreviewController` 需要能处理：
1. 已编码的URL（从MinIO直接访问）
2. 未编码的URL（从前端路由参数获取）

## 测试验证

### 测试步骤

1. **重新打包并重启应用**
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
   - 日志应该显示：`reports/_____C_______%E4%B8%80%E9%94%AE%E5%AE%A1%E6%9F%A5_B_...`
   - 路径分隔符 `/` 不编码
   - 中文正确编码

4. **前端测试**
   - 点击打开文档
   - 检查浏览器地址栏的URL
   - 应该只有一层编码（Vue Router查询参数的编码）

5. **OnlyOffice预览**
   - 文档应该能正常加载
   - 批注应该正常显示

## 预期结果

修复后：
- ✅ 后端返回的URL格式：`http://localhost:9000/contract-review/reports/_____C_______%E4%B8%80%E9%94%AE%E5%AE%A1%E6%9F%A5_B_...`
- ✅ 前端查询参数：Vue Router自动解码，传给后端的是正确URL
- ✅ OnlyOffice能正常加载文档
- ✅ 批注正常显示

---

**修复时间**：2025-10-31  
**修复状态**：✅ 后端已修复，待测试验证  
**下一步**：重新测试文档预览功能


