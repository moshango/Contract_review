# MinIO 文档问题诊断

## 问题描述

用户报告：本地保存的文档带批注，但从 MinIO 下载的文档是原文档，不带任何批注。短文档没有这个问题。

## 诊断结果

### 代码逻辑分析

根据 `QwenRuleReviewController.java` 的代码：

```java
// 第426行：本地保存
java.nio.file.Files.write(outputPath, annotatedDocBytes);

// 第454-458行：MinIO上传
minioUrl = minioFileService.uploadBytes(
    annotatedDocBytes,  // 使用同一个变量
    minioObjectName,
    contentType
);
```

**代码逻辑是正确的**：本地保存和 MinIO 上传使用的是**完全相同的 `annotatedDocBytes` 变量**。

### 日志验证

根据日志（第997-1008行）：
```
✓ 文档已保存到本地: D:\...\测试合同_C档_长文本版_一键审查_B_20251031_092116.docx
✓ 文档已保存到MinIO: http://localhost:9000/contract-review/reports/...
✓ 一键审查完成！总耗时: 55688ms, 检出 7 个问题
```

### Python 验证结果

脚本验证本地文档：
```
✓ 本地文档包含 19 个文件
  - document.xml.rels: ✓
  - comments.xml: ✓
  - comments.xml 大小: 6658 字符
```

## 可能的原因

### 1. MinIO 缓存问题

MinIO 或浏览器可能缓存了旧文件。

**验证方法**：
- 清除浏览器缓存
- 使用不同的浏览器下载
- 使用 MinIO 控制台直接下载

### 2. 文件名混乱

可能下载了错误的文件（旧版本或其他文档）。

**验证方法**：
- 对比日志中的文件名
- 检查文件大小
- 检查文件的最后修改时间

### 3. MinIO 配置问题

`getFileUrl()` 方法生成的 URL 可能不正确。

**检查**：
- URL 格式是否正确
- bucket 配置是否正确
- 权限设置是否正确

### 4. 前端下载逻辑

前端可能使用了错误的 URL 或文件。

**检查**：
- 前端使用的下载 URL
- 前端是否有文件缓存

## 已添加的诊断日志

在第456-474行添加了详细的诊断日志：

```java
// 【诊断】打印上传前信息
log.info("【诊断】准备上传到MinIO: objectName={}, size={}字节", 
        minioObjectName, annotatedDocBytes.length);

// 【诊断】验证上传后的文件大小
log.info("【诊断】MinIO文件验证: size={}字节, 本地={}字节, 匹配={}", 
        minioSize, annotatedDocBytes.length, 
        minioSize.equals((long)annotatedDocBytes.length));
```

## 下一步操作

### 1. 重新打包

```bash
cd Contract_review
mvn package -DskipTests
```

### 2. 重启应用并重新测试

### 3. 查看诊断日志

在新日志中查找：
- `【诊断】准备上传到MinIO`
- `【诊断】MinIO文件验证`

### 4. 验证文件一致性

日志会显示：
```
【诊断】准备上传到MinIO: objectName=..., size=38050字节
【诊断】MinIO文件验证: size=38050字节, 本地=38050字节, 匹配=true
```

如果匹配=false，说明上传过程中出现了问题。

### 5. 手动验证 MinIO

1. 访问 MinIO 控制台：http://localhost:9001
2. 登录（contractadmin/C0ntract!2025#）
3. 进入 `contract-review` bucket
4. 查看 `reports/` 目录
5. 下载最新的文件
6. 用 Word 打开验证

## 预期结果

如果修复成功，应该看到：
```
【诊断】准备上传到MinIO: objectName=reports/..., size=38050字节
✓ 字节数组上传成功
【诊断】MinIO文件验证: size=38050字节, 本地=38050字节, 匹配=true
✓ 文档已保存到MinIO
```

## 如果仍然失败

如果诊断日志显示匹配=false，需要：
1. 检查 MinIO 客户端配置
2. 检查上传过程中是否有异常
3. 检查 ByteArrayInputStream 是否正确传递数据

---

**当前状态**：已添加诊断日志，等待用户重新测试


