# API 路由修复 - 解决404错误

## 🐛 问题诊断

### 错误现象
```
2025-10-20 13:42:45 WARN NoResourceFoundException: No static resource parse
```

### 根本原因
前端JavaScript调用API时缺少`/api`前缀：

| 调用 | 期望 | 实际 |
|------|------|------|
| 解析接口 | `/api/parse` | `/parse` ❌ |
| 批注接口 | `/api/annotate` | `/annotate` ❌ |
| 健康检查 | `/api/health` | `/health` ❌ |

**为什么？**
- 后端 `ContractController` 的 `@RequestMapping("/api")` 设置了基础路径
- 前端没有考虑这个前缀

## ✅ 修复方案

### 修改文件
**文件**: `src/main/resources/static/js/main.js`

### 修复的API调用

1. **解析合同** (第74行)
```javascript
// 修复前
const url = `/parse?anchors=${anchorMode}&returnMode=${returnMode}`;
// 修复后
const url = `/api/parse?anchors=${anchorMode}&returnMode=${returnMode}`;
```

2. **批注合同** (第145行)
```javascript
// 修复前
const url = `/annotate?anchorStrategy=${anchorStrategy}&cleanupAnchors=${cleanupAnchors}`;
// 修复后
const url = `/api/annotate?anchorStrategy=${anchorStrategy}&cleanupAnchors=${cleanupAnchors}`;
```

3. **健康检查** (第258行)
```javascript
// 修复前
const response = await fetch('/health');
// 修复后
const response = await fetch('/api/health');
```

### 未修改的API调用（已正确）

这些API已经正确，无需修改：

```javascript
// AutoReviewController (@RequestMapping("/auto-review"))
/auto-review/status
/auto-review

// ChatGPTIntegrationController (@RequestMapping("/chatgpt"))
/chatgpt/generate-prompt
/chatgpt/import-result

// ReviewStandardController (@RequestMapping("/standards"))
/standards/contract-type/{contractType}
/standards/generate-prompt
```

## 📊 后端路由映射

| Controller | 基础路径 | 端点 | 完整URL |
|-----------|---------|------|---------|
| ContractController | /api | /parse | /api/parse |
| | | /annotate | /api/annotate |
| | | /health | /api/health |
| AutoReviewController | /auto-review | /status | /auto-review/status |
| | | (其他) | /auto-review/* |
| ChatGPTIntegrationController | /chatgpt | /generate-prompt | /chatgpt/generate-prompt |
| | | /import-result | /chatgpt/import-result |
| ReviewStandardController | /standards | /contract-type/* | /standards/contract-type/* |
| | | /generate-prompt | /standards/generate-prompt |

## 🧪 测试验证

### 编译结果
```
✅ mvn clean compile -DskipTests
BUILD SUCCESS
Total time: 8.676 s
No errors found
```

### 预期效果
修复后，当用户：
1. 上传合同文件 → 调用 `/api/parse` ✅
2. 输入审查结果 → 调用 `/api/annotate` ✅
3. 系统启动 → 检查 `/api/health` ✅

所有API调用应该返回200 OK而不是404。

## 📝 提交信息

```
提交: edff11b
消息: 修复前端API路由：添加/api前缀
变更: 1 file changed, 3 insertions(+)
推送: ✅ 成功
```

## 🚀 后续步骤

1. **重新启动服务**
   ```bash
   mvn spring-boot:run
   ```

2. **测试功能**
   - 访问 http://localhost:8080
   - 上传合同文件测试
   - 检查浏览器控制台日志

3. **验证API**
   ```bash
   # 测试健康检查
   curl http://localhost:8080/api/health

   # 测试解析接口
   curl -X POST -F "file=@contract.docx" http://localhost:8080/api/parse
   ```

## 💡 学习要点

### API路由设计最佳实践
1. ✅ 使用 `@RequestMapping` 统一管理基础路径
2. ✅ 前端需要完整的URL路径
3. ✅ 定期审计前后端路由一致性

### 调试技巧
1. 查看浏览器开发者工具 (F12)
   - Network 标签查看实际请求URL
   - Console 查看错误信息

2. 查看后端日志
   - 找到404错误的stack trace
   - 定位到具体的路由问题

3. 使用curl验证API
   ```bash
   curl -v http://localhost:8080/api/parse
   ```

## 📌 总结

**问题**: 前端API调用缺少 `/api` 前缀，导致404错误

**解决**: 在前端所有ContractController的API调用前添加 `/api` 前缀

**影响**:
- ✅ 解析功能 (parse)
- ✅ 批注功能 (annotate)
- ✅ 系统健康检查 (health)

**状态**: ✅ 已修复并推送

