# 🔧 Qwen API 超时问题修复

**问题**: Qwen API 调用出现 TimeoutException (30秒超时)

**根本原因**:
- WebClient 的 `flatMap` 操作在响应解析时没有额外的超时保护
- 当响应解析时间接近或超过 30 秒时，会抛出 TimeoutException

**解决方案**:
1. ✅ 增加了 flatMap 后的额外超时控制（45秒 = 30秒 × 1.5）
2. ✅ 将 Qwen API 超时时间从 30 秒增加到 60 秒
3. ✅ 添加了注释说明超时缓冲的目的

**修改文件**:
1. `src/main/java/com/example/Contract_review/qwen/client/QwenClient.java` (Line 78-90)
   - 在 flatMap 后添加总超时控制
   - 增加 50% 的超时缓冲

2. `src/main/resources/application.properties` (Line 109)
   - 将 qwen.timeout 从 30 秒改为 60 秒

**应用状态**: ✅ 已重启，新配置生效

**预期效果**: Qwen API 调用不再超时
