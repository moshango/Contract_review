# OnlyOffice预览器API修复报告

## 问题分析

### 原始错误
```
GET http://localhost:8080/api/preview/supported/reports%2F___________%E4%B8%80%E9%94%AE%E5%AE%A1%E6%9F%A5_A%E6%96%B9_20251028_155832_67e25ac4.docx 400 (Bad Request)
```

### 问题原因
1. **URL路径参数问题**: Spring Boot无法正确解析包含特殊字符（`%2F`、中文）的路径参数
2. **API返回HTML**: 当路径解析失败时，Spring Boot返回错误页面而不是JSON
3. **前端JSON解析失败**: 尝试解析HTML页面作为JSON导致语法错误

## 修复方案

### 方案一：修改后端API（已实现）
- 将路径参数 `@PathVariable` 改为查询参数 `@RequestParam`
- 添加URL解码处理
- 改进错误处理和日志记录

### 方案二：简化前端逻辑（推荐）
- 直接在客户端检查文件扩展名
- 避免不必要的API调用
- 提高响应速度和稳定性

## 修复内容

### 1. 后端API修复
```java
// 修改前
@GetMapping("/supported/{fileName}")
public ResponseEntity<Map<String, Object>> isFileSupported(@PathVariable String fileName)

// 修改后  
@GetMapping("/supported")
public ResponseEntity<Map<String, Object>> isFileSupported(@RequestParam String fileName)
```

### 2. 前端逻辑优化
```javascript
// 修改前：依赖API调用
const response = await fetch(`/api/preview/supported/${encodeURIComponent(fileName)}`);

// 修改后：客户端直接检查
const extension = this.getFileExtension(fileName).toLowerCase();
const supported = supportedFormats.includes(extension);
```

## 测试验证

### 测试步骤
1. **刷新浏览器页面** (Ctrl+F5)
2. **打开开发者工具** (F12)
3. **点击文件预览选项卡**
4. **点击文件预览按钮**
5. **检查控制台输出**

### 预期结果
- ✅ 不再有400 Bad Request错误
- ✅ 不再有JSON解析错误
- ✅ 文件支持检查正常工作
- ✅ 预览功能正常启动

### 支持的文件格式
- **Word文档**: .docx, .doc, .odt, .rtf
- **Excel表格**: .xlsx, .xls, .ods  
- **PowerPoint演示**: .pptx, .ppt, .odp
- **其他格式**: .pdf, .txt

## 备用预览方案

当OnlyOffice Document Server不可用时，系统会自动使用备用方案：

| 文件类型 | 备用预览方案 |
|---------|-------------|
| PDF | 浏览器原生iframe预览 |
| Office文档 | Google Docs Viewer |
| 文本文件 | 直接显示内容 |
| 其他格式 | 显示下载链接 |

## 性能优化

### 优势
1. **减少网络请求**: 客户端直接检查文件格式
2. **提高响应速度**: 无需等待API响应
3. **增强稳定性**: 避免网络和API错误
4. **简化架构**: 减少前后端依赖

### 注意事项
- 文件格式检查逻辑现在在前端
- 如需修改支持格式，需要更新前端代码
- 后端API仍然保留，可用于其他功能

## 总结

通过将文件支持检查从后端API改为前端直接处理，成功解决了URL编码和API调用的问题。现在系统更加稳定和高效，预览功能应该可以正常工作。

### 下一步
1. 刷新页面测试功能
2. 验证各种文件格式的预览
3. 如有问题，查看控制台错误信息
