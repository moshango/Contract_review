# 故障排除 - 文档导航

本目录提供常见问题的诊断和解决方案。

## 🆘 常见问题快速查找

### 按现象分类

**一键按钮不可用**
→ 问题: Qwen服务未配置
→ 解决: [配置API Key](#配置api-key)

**审查耗时过长**
→ 问题: 网络缓慢或Prompt太长
→ 解决: [增加超时配置](#增加超时配置)

**JSON解析失败**
→ 问题: 响应格式不符
→ 解决: [更新模型版本](#更新模型版本)

**应用启动失败**
→ 问题: 配置错误
→ 解决: [检查配置](#检查配置)

**性能不足**
→ 问题: 资源限制
→ 解决: [性能优化](#性能优化)

## 🔍 诊断步骤

### 第1步: 检查服务状态

```bash
curl http://localhost:8080/api/qwen/rule-review/status
```

**正常响应**:
```json
{
  "success": true,
  "qwenAvailable": true,
  "message": "✓ Qwen服务已就绪"
}
```

**异常响应**:
```json
{
  "success": true,
  "qwenAvailable": false,
  "message": "✗ Qwen服务未配置"
}
```

### 第2步: 检查配置

```bash
curl http://localhost:8080/api/qwen/rule-review/config
```

**检查项**:
- ✓ API Key 已设置 (不是占位符)
- ✓ Base URL 正确
- ✓ 模型名称有效
- ✓ 超时设置合理

### 第3步: 查看日志

```bash
# 启用DEBUG日志
logging.level.com.example.Contract_review.service=DEBUG

# 查看日志输出
tail -f logs/application.log
```

## ❌ 常见问题与解决

### 问题1: 一键按钮是灰色的

**症状**: 按钮显示为灰色，不可点击

**原因**: Qwen服务未正确配置

**排查步骤**:
1. 检查 application.properties
2. 确认 API Key 不是占位符
3. 运行 `/api/qwen/rule-review/status` 检查

**解决方案**:
```properties
# 编辑 application.properties
qwen.api-key=sk-your-actual-api-key-here  ← 填写真实key
qwen.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1
qwen.model=qwen-max-latest
qwen.timeout=30
```

### 问题2: 审查超时

**症状**: 审查时间超过30秒或超时错误

**原因**: 网络缓慢或Prompt过长

**解决方案**:
```properties
# 方案1: 增加超时时间
qwen.timeout=60

# 方案2: 检查网络连接
ping dashscope.aliyuncs.com

# 方案3: 简化Prompt内容
# 减少匹配条款数量
```

### 问题3: JSON格式错误

**症状**: "JSON解析失败"错误

**原因**: Qwen返回格式不符或模型版本问题

**解决方案**:
```properties
# 更新到最新模型
qwen.model=qwen-max-latest

# 启用DEBUG查看原始响应
logging.level.com.example.Contract_review.service=DEBUG
```

### 问题4: API Key无效

**症状**: "401 Unauthorized" 错误

**原因**: API Key过期或格式错误

**排查步骤**:
1. 访问 https://dashscope.console.aliyun.com/
2. 确认API Key仍有效
3. 复制正确的Key并更新配置

### 问题5: 网络无法访问

**症状**: "连接超时"或"无法连接"错误

**原因**: 防火墙或网络限制

**解决方案**:
```bash
# 检查网络连接
ping dashscope.aliyuncs.com

# 测试HTTPS访问
curl -v https://dashscope.aliyuncs.com/

# 查看防火墙设置
# 确保允许443端口出站连接
```

## 📋 完整故障排查清单

### 配置检查
- [ ] API Key 已获取并配置
- [ ] API Key 不是占位符 (sk-xxxx)
- [ ] Base URL 正确无误
- [ ] 模型名称有效
- [ ] 超时设置合理

### 网络检查
- [ ] 能ping通 dashscope.aliyuncs.com
- [ ] HTTPS端口443可访问
- [ ] 防火墙未屏蔽出站连接
- [ ] DNS解析正常

### 应用检查
- [ ] 应用成功启动
- [ ] 日志无错误输出
- [ ] API端点可访问
- [ ] 数据库连接正常

### 功能检查
- [ ] 一键按钮可见
- [ ] 规则审查正常
- [ ] Prompt生成成功
- [ ] 审查接口响应

## 🔧 性能优化

### 优化1: 减少Prompt长度
```
策略: 只审查高风险条款
方法: 在规则审查时过滤低风险项
效果: 减少30-40%的审查时间
```

### 优化2: 增加超时时间
```properties
# 对于复杂合同
qwen.timeout=60
```

### 优化3: 启用结果缓存
```java
// 缓存相同Prompt的结果
// 避免重复审查
```

### 优化4: 并发处理
```java
// 支持10+用户并发审查
// 使用异步处理机制
```

## 📊 监控指标

### 关键指标
```
API响应时间: 目标 <50秒
错误率: 目标 <1%
服务可用性: 目标 >99%
```

### 监控命令
```bash
# 监控API响应
curl -w "时间: %{time_total}秒\n" \
  http://localhost:8080/api/qwen/rule-review/status

# 查看错误日志
grep ERROR logs/application.log

# 查看性能日志
grep processingTime logs/application.log
```

## 🆘 获取支持

### 自助排查
1. ✅ 按照本文档排查
2. ✅ 查看应用日志
3. ✅ 检查API状态
4. ✅ 尝试重启应用

### 收集诊断信息

遇到问题无法解决时，收集以下信息:
```
1. 错误日志输出
2. API状态接口返回值
3. 应用配置信息 (隐藏密钥)
4. 操作系统信息
5. 网络环境描述
```

### 联系方式
- 📖 查看完整文档
- 📧 发送问题描述和诊断信息
- 💬 在项目中提Issue

## 📚 相关文档

| 文档 | 用途 |
|------|------|
| [快速开始](../快速开始) | 配置和开始 |
| [API接口](../API接口) | API端点参考 |
| [功能说明](../功能说明) | 功能详解 |
| [实现总结](../实现总结) | 技术细节 |

## 🎯 问题解决流程

```
发现问题
  ↓
查看本文档中的快速查找
  ↓
按照诊断步骤操作
  ↓
检查配置、网络、日志
  ↓
应用解决方案
  ↓
问题解决? → 是 → ✅ 完成
           ↓ 否
       收集诊断信息
           ↓
       联系技术支持
```

---

**版本**: 1.0
**最后更新**: 2025-10-24
**状态**: ✅ 生产就绪
