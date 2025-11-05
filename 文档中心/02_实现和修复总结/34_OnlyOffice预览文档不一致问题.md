# OnlyOffice预览文档不一致问题分析

## 日志验证结果

根据最新的日志（2025-10-31 10:33:58）：

✅ **所有验证都通过**：
- 上传前文档包含批注: **true**
- 上传前文档MD5: `6c377af635d5b2425e48a3eba5378693`
- 下载后文档MD5: `6c377af635d5b2425e48a3eba5378693`
- MD5匹配: **true**
- 下载后文档包含批注: **true**

**结论**：MinIO存储和下载的文档是正确的，包含批注。

## 问题定位

如果文档仍然不一致，问题可能在于：

### 1. OnlyOffice预览时的文档加载

**可能原因**：
- OnlyOffice在加载文档时可能缓存了旧版本
- 代理URL返回的文档可能有问题
- OnlyOffice渲染时可能修改了文档格式

**检查点**：
```
构造代理URL: 
objectName=reports/_____C_______一键审查_A_20251031_103358_7b475346.docx
encoded=reports%2F_____C_______%E4%B8%80%E9%94%AE%E5%AE%A1%E6%9F%A5_A_20251031_103358_7b475346.docx
finalUrl=http://host.docker.internal:8080/api/preview/proxy?fileName=...
```

### 2. 代理URL的下载逻辑

**检查 `FilePreviewController.proxyDocumentByQuery`**：
```java
@GetMapping("/proxy")
public ResponseEntity<byte[]> proxyDocumentByQuery(@RequestParam String fileName) {
    String decodedFileName = URLDecoder.decode(fileName, "UTF-8");
    byte[] fileData = minioFileService.downloadFile(decodedFileName);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(contentType))
        .body(fileData);
}
```

这个逻辑看起来是正确的，应该能正确下载MinIO中的文件。

### 3. OnlyOffice缓存问题

**可能原因**：
- OnlyOffice可能缓存了文档的旧版本
- 需要使用不同的文档key来强制刷新

**检查 `FilePreviewController.getSignedEditorConfig`**：
```java
// 使用MD5生成稳定且较短的文档key
String key = MD5(fileName);
document.put("key", key);
```

如果文件名相同，key也会相同，可能导致OnlyOffice使用缓存。

### 4. 浏览器下载vs OnlyOffice预览

**可能原因**：
- 直接浏览器下载的文档是正确的
- 但通过OnlyOffice预览的文档不一致

这可能说明问题在OnlyOffice的文档加载或渲染环节。

## 诊断步骤

### 步骤1：直接下载MinIO文档验证

1. **使用MinIO URL直接下载**
   ```
   http://localhost:9000/contract-review/reports/_____C_______一键审查_A_20251031_103358_7b475346.docx
   ```

2. **对比下载的文档和本地文档**
   - 使用之前的对比脚本
   - 检查MD5是否一致
   - 检查是否包含批注

### 步骤2：检查代理URL返回的文档

1. **直接访问代理URL**
   ```
   http://localhost:8080/api/preview/proxy?fileName=reports%2F_____C_______%E4%B8%80%E9%94%AE%E5%AE%A1%E6%9F%A5_A_20251031_103358_7b475346.docx
   ```

2. **下载并验证**
   - 保存下载的文档
   - 检查MD5
   - 检查是否包含批注

### 步骤3：检查OnlyOffice的文档key

如果文档key相同，OnlyOffice可能会使用缓存。可以在key中加入时间戳或其他唯一标识：

```java
// 修改key生成逻辑，加入时间戳避免缓存
String key = MD5(fileName + "_" + System.currentTimeMillis());
```

或者直接使用时间戳：

```java
String key = String.valueOf(System.currentTimeMillis());
```

## 修复建议

### 修复1：在代理URL下载时添加验证

在 `FilePreviewController.proxyDocumentByQuery` 中添加诊断：

```java
@GetMapping("/proxy")
public ResponseEntity<byte[]> proxyDocumentByQuery(@RequestParam String fileName) {
    try {
        String decodedFileName = java.net.URLDecoder.decode(fileName, "UTF-8");
        logger.debug("代理下载: fileName={}", decodedFileName);
        
        byte[] fileData = minioFileService.downloadFile(decodedFileName);
        
        // 【诊断】验证下载的文档是否包含批注
        boolean hasComments = validateDocHasComments(fileData);
        logger.info("【诊断】代理下载文档包含批注: {}", hasComments);
        
        if (!hasComments) {
            logger.error("⚠️ 【严重警告】代理下载的文档不包含批注！fileName={}", decodedFileName);
        }
        
        String contentType = getContentType(decodedFileName);
        return ResponseEntity.ok()
            .contentType(org.springframework.http.MediaType.parseMediaType(contentType))
            .body(fileData);
    } catch (Exception e) {
        logger.error("代理文档访问失败: fileName={}", fileName, e);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
```

### 修复2：修改OnlyOffice文档key避免缓存

在 `FilePreviewController.getSignedEditorConfig` 中：

```java
// 使用时间戳确保每次都是新文档，避免OnlyOffice缓存
String key = String.valueOf(System.currentTimeMillis());
// 或者：String key = MD5(fileName + "_" + timestamp);
document.put("key", key);
```

### 修复3：添加代理下载的MD5验证

```java
// 计算下载文档的MD5
MessageDigest md5 = MessageDigest.getInstance("MD5");
byte[] hash = md5.digest(fileData);
String md5Str = bytesToHex(hash);
logger.debug("【诊断】代理下载文档MD5: {}", md5Str);
```

## 下一步操作

1. **添加代理下载的诊断代码**（如上所述）

2. **重新生成文档并测试**
   - 通过OnlyOffice预览文档
   - 查看日志中的诊断信息

3. **对比三个文档**：
   - 本地保存的文档
   - 直接浏览器下载的MinIO文档
   - 通过OnlyOffice预览后下载的文档（如果有下载功能）

4. **检查OnlyOffice日志**（如果可用）
   - 查看OnlyOffice的文档加载日志
   - 检查是否有错误信息

---

**问题状态**：MinIO存储正常，问题可能在OnlyOffice预览环节  
**下一步**：添加代理下载的诊断代码，并检查OnlyOffice缓存问题


