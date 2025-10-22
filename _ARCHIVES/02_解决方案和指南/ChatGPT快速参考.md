# ChatGPT 集成 - 快速参考卡

## ✅ 三步完成合同批注

### 1️⃣ 上传文件 → 生成提示
```
UI: 点击"📁 选择合同文件" → 选择 .docx 文件
UI: 点击"🤖 生成ChatGPT提示"

后端: 解析文件 + 生成锚点 + 缓存文档
↓
返回: parseResultId + prompt

✅ 验证: 看到 toast "✅ 已生成锚点..."
✅ 验证: 浏览器控制台输出 "✅ 成功获取parseResultId..."
```

### 2️⃣ ChatGPT 审查
```
UI: 点击"📋 复制提示" → 自动复制到剪贴板
UI: 点击"🌐 打开ChatGPT" → 打开 https://chatgpt.com/
OR: 手动粘贴提示到 ChatGPT

ChatGPT: 粘贴提示 → 等待 ChatGPT 返回审查结果 (JSON)
ChatGPT: 复制返回的审查结果（包含 issues 数组）
```

### 3️⃣ 导入结果 → 下载批注文档
```
UI: 粘贴 ChatGPT 返回的结果到"ChatGPT审查结果"文本框
UI: 点击"📥 导入并生成批注文档"

后端: 【关键】使用缓存的带锚点文档进行批注
↓
返回: 批注后的 docx 文件

✅ 验证: 看到 toast "✅ ChatGPT审查结果导入成功"
✅ 验证: 完成提示显示 "✅ 使用缓存的带锚点文档进行批注"
✅ 文件自动下载: contract_ChatGPT审查.docx
```

---

## 🔑 关键参数

### 前端全局变量
```javascript
chatgptFile           // 用户选择的文件
chatgptPrompt         // ChatGPT 提示文本
chatgptParseResultId  // 【关键】缓存的文档 ID
```

### 后端缓存参数
```
parseResultId:        UUID 格式，如 "a1b2c3d4-e5f6-7890-..."
有效期:               4 小时
存储内容:             ParseResult + 带锚点的文档字节
```

---

## 🐛 常见故障排查

### 问题1: 批注位置不对
症状: 看到日志 "⚠️ 未找到anchorId对应的书签"
原因: 没有使用缓存的带锚点文档
修复: 检查浏览器 F12 是否输出 "✅ 成功获取parseResultId"

### 问题2: 无法解析 ChatGPT 响应  
症状: toast "❌ ChatGPT响应格式错误"
原因: JSON 格式不正确
修复: 确保响应包含 "issues" 字段，遵循标准 JSON 格式

### 问题3: 文件没有批注
症状: 文件下载成功但内容没有批注
原因: 使用了原始文件（不带锚点）
修复: 检查完成提示是否显示 "✅ 使用缓存的带锚点文档"

---

## 📊 日志关键词

✅ 成功: "✅ 成功获取parseResultId", "✅ [缓存命中]"
⚠️ 警告: "⚠️ [参数缺失]", "⚠️ [降级方案]"  
❌ 错误: "❌ 生成提示失败", "❌ ChatGPT响应格式错误"

---

**最后更新**: 2025-10-22
**编译**: ✅ SUCCESS
