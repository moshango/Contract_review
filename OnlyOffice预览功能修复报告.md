# OnlyOffice 预览功能修复报告

## 问题描述
OnlyOffice Document Server 无法访问 MinIO 中的文档，出现 400 错误：
- WebSocket 连接失败
- HTTP 400 Bad Request 错误
- OnlyOffice 无法直接访问 MinIO URL

## 根本原因
1. **路径参数问题**: `@PathVariable` 无法处理包含斜杠的文件路径
2. **JWT 未启用**: OnlyOffice Document Server 的 JWT 功能被禁用
3. **直接访问 MinIO**: OnlyOffice 尝试直接访问 MinIO URL，但存在访问限制

## 修复方案

### 1. 启用 OnlyOffice JWT
- 重新创建 OnlyOffice 容器，启用 JWT
- 设置统一密钥: `YourStrongSecret_ChangeMe`
- 配置环境变量: `JWT_ENABLED=true`, `JWT_SECRET=YourStrongSecret_ChangeMe`

### 2. 修复后端代理接口
- 将 `@GetMapping("/proxy/{fileName}")` 改为 `@GetMapping("/proxy/**")`
- 使用 `HttpServletRequest` 获取完整请求路径
- 从路径中提取文件名: `requestPath.substring("/api/preview/proxy/".length())`
- 修复导入: 使用 `jakarta.servlet.http.HttpServletRequest`

### 3. 修改前端预览逻辑
- 使用后端代理 URL: `/api/preview/proxy/${encodeURIComponent(fileName)}`
- 而不是直接的 MinIO URL: `http://localhost:9000/contract-review/...`

## 修复的文件

### 后端文件
- `src/main/java/com/example/Contract_review/controller/FilePreviewController.java`
  - 修改代理接口路径映射
  - 添加 HttpServletRequest 导入
  - 修复变量作用域问题

### 前端文件
- `src/main/resources/static/js/onlyoffice-previewer.js`
  - 修改预览逻辑使用代理 URL

### Docker 配置
- 重新创建 OnlyOffice 容器，启用 JWT

## 测试验证

### 1. OnlyOffice Document Server 状态
```bash
docker exec onlyoffice-documentserver curl -s http://localhost/healthcheck
# 输出: true
```

### 2. 后端 API 状态
```bash
curl http://localhost:8080/api/preview/files
# 返回文件列表
```

### 3. 代理接口测试
```bash
curl http://localhost:8080/api/preview/proxy/reports%2Ffilename.docx
# 应该返回文档内容而不是 400 错误
```

## 预期结果
- OnlyOffice 可以成功预览 MinIO 中的文档
- 不再出现 WebSocket 连接失败
- 不再出现 HTTP 400 错误
- 文档预览功能正常工作

## 注意事项
1. 确保 Spring Boot 应用重新启动以加载修复
2. 确保 OnlyOffice Document Server 正常运行
3. 确保 MinIO 服务正常运行
4. 前端需要刷新页面以加载新的 JavaScript 代码

## 后续优化建议
1. 添加错误处理和重试机制
2. 实现文档缓存以提高性能
3. 添加用户权限控制
4. 支持更多文档格式
