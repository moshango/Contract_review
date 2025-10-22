# ChatGPT集成模块 UI 使用完全指南

## 📋 目录
1. [系统架构](#系统架构)
2. [工作流程](#工作流程)
3. [UI 使用步骤](#ui-使用步骤)
4. [前后端通信流程](#前后端通信流程)
5. [常见问题排查](#常见问题排查)
6. [日志解读指南](#日志解读指南)

---

## 🏗️ 系统架构

### 核心概念

```
┌─────────────────────────────────────────────────────────────┐
│                      UI 层 (前端)                            │
│  - 文件上传                                                  │
│  - 参数配置                                                  │
│  - parseResultId 存储和传递                                  │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                   API 层 (后端)                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ /chatgpt/generate-prompt (步骤1)                     │  │
│  │ - 生成带锚点的文档                                    │  │
│  │ - 返回 parseResultId                                │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ /chatgpt/import-result (步骤2)                       │  │
│  │ - 使用缓存的带锚点文档进行批注 (使用parseResultId)  │  │
│  │ - 返回批注后的文档                                    │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                  存储层 (缓存)                               │
│  - ParseResultCache: 存储带锚点的文档 (4小时有效期)         │
│  - 支持多个 parseResultId 并行处理                          │
└─────────────────────────────────────────────────────────────┘
```

### 关键修复点

| 组件 | 修复内容 | 效果 |
|------|--------|------|
| **后端 API** | `/generate-prompt` 返回 `parseResultId` | ✅ UI 可获得缓存 ID |
| **后端 API** | `/import-result` 支持 `parseResultId` 参数 | ✅ 使用带锚点文档 |
| **UI 全局变量** | 添加 `chatgptParseResultId` | ✅ 存储缓存 ID |
| **UI 生成流程** | `generateChatGPTPrompt()` 保存 ID | ✅ 自动关联 |
| **UI 导入流程** | `importChatGPTResult()` 传递 ID 参数 | ✅ 精确定位 |

---

## 🔄 工作流程

### 完整流程图

```
用户打开 UI
    ↓
[步骤1] 上传文件 (contract.docx)
    ↓
📁 handleChatGPTFileSelect()
    └─→ chatgptFile = file
    └─→ chatgptParseResultId = null (重置)
    ↓
[按钮] 生成ChatGPT提示
    ↓
🚀 generateChatGPTPrompt()
    ├─→ 调用 /chatgpt/generate-prompt?anchors=generate
    ├─→ 后端: 解析文件 + 生成锚点 + 缓存文档
    └─→ 返回响应包含: parseResultId + prompt + instructions
    ↓
💾 showChatGPTPrompt(data)
    ├─→ chatgptPrompt = data.chatgptPrompt
    ├─→ chatgptParseResultId = data.parseResultId  【关键！】
    └─→ 显示提示内容 + 导入区域
    ↓
[用户操作] 复制提示 → 打开ChatGPT → 进行审查
    ↓
[用户操作] 复制ChatGPT返回的JSON → 粘贴到文本框
    ↓
[按钮] 导入并生成批注文档
    ↓
🔄 importChatGPTResult()
    ├─→ 验证JSON格式
    ├─→ 检查 chatgptParseResultId 是否存在
    ├─→ 调用 /chatgpt/import-result?parseResultId=XXX&cleanupAnchors=true
    │   ├─ 【使用缓存文档】 ← 精确定位 ✅
    │   └─ 【使用原始文件】 ← 定位不精确 ⚠️
    └─→ 返回批注后的文档
    ↓
📥 downloadFile()
    ├─→ 下载 contract_ChatGPT审查.docx
    └─→ 显示完成提示 + 定位方式说明
    ↓
✅ 完成
```

---

## 🎯 UI 使用步骤

### 必读：完整操作步骤

#### 步骤 1️⃣: 上传文件并生成提示

```
1. 打开 http://localhost:8080
2. 切换到 "💬 ChatGPT集成" 标签
3. 在 "📁 步骤1" 区域：
   - 点击 "📁 选择合同文件"
   - 选择你的 .docx 文件
   - 文件名会显示在下方
4. 选择"合同类型"（默认: 技术服务合同）
5. 点击 "🤖 生成ChatGPT提示"
   ↓ 系统会自动：
   - 解析文件
   - 生成锚点
   - 缓存文档并返回 parseResultId
   - 显示 ChatGPT 提示内容
```

✅ **关键检查点**：
- 看到 toast 提示: `✅ 已生成锚点，parseResultId已保存用于后续批注`
- 浏览器控制台 (F12) 显示: `✅ 成功获取parseResultId: a1b2c3d4-...`
- 生成的提示内容显示在下方

#### 步骤 2️⃣: 在 ChatGPT 中进行审查

```
1. 点击 "🌐 打开ChatGPT" 按钮
   → 自动打开 https://chatgpt.com/
2. 或手动打开 https://chatgpt.com/
3. 点击 "📋 复制提示" 按钮
   → Prompt 已复制到剪贴板
   → 会看到 toast 提示: `请打开 https://chatgpt.com/ 并粘贴提示`
4. 在 ChatGPT 中粘贴提示
5. 等待 ChatGPT 返回审查结果（JSON 格式）
6. 复制完整的 ChatGPT 回复
```

✅ **关键检查点**：
- ChatGPT 返回包含 `"issues"` 字段的 JSON
- 每个 issue 最好包含 `"targetText"` 和 `"severity"` 字段
- 回复可以包含 markdown 代码块 (```json...```)，系统会自动清理

#### 步骤 3️⃣: 导入审查结果并生成批注文档

```
1. 在 "📥 步骤2: 导入ChatGPT审查结果" 区域
2. 点击 ChatGPT 回复文本框
3. 粘贴 ChatGPT 的完整回复
4. （可选）取消勾选 "批注完成后清理锚点"
   → 保留锚点以支持后续增量审查
5. 点击 "📥 导入并生成批注文档"
   ↓ 系统会自动：
   - 验证 JSON 格式
   - 【关键】使用缓存的带锚点文档进行批注
   - 返回批注后的文档并下载
```

✅ **关键检查点**：
- 看到 toast: `✅ ChatGPT审查结果导入成功! 文档已下载`
- 完成提示显示: `✅ 使用缓存的带锚点文档进行批注 - 定位精度最高`
- 文档自动下载: `contract_ChatGPT审查.docx`

---

## 🔗 前后端通信流程

### 请求/响应详解

#### 请求1: 生成提示

**前端 JavaScript:**
```javascript
const url = `/chatgpt/generate-prompt?contractType=technology&anchors=generate`;
// 【关键】anchors=generate 确保返回 parseResultId

const response = await fetch(url, {
    method: 'POST',
    body: formData  // 包含 file
});

const data = await response.json();
chatgptParseResultId = data.parseResultId;  // 【关键】保存ID
```

**后端 Java:**
```java
@PostMapping("/generate-prompt")
public ResponseEntity<?> generatePrompt(
    @RequestParam("file") MultipartFile file,
    @RequestParam(value = "anchors", defaultValue = "generate") String anchors) {

    // 1. 解析文件
    ParseResultWithDocument resultWithDoc =
        contractParseService.parseContractWithDocument(file, anchors);

    // 2. 生成锚点并缓存文档
    String parseResultId = parseResultCache.store(
        resultWithDoc.getParseResult(),
        resultWithDoc.getDocumentBytes(),
        file.getOriginalFilename()
    );

    // 3. 返回 parseResultId 给前端
    result.put("parseResultId", parseResultId);  // 【关键】
    result.put("chatgptPrompt", ...);
    return ResponseEntity.ok(result);
}
```

**响应数据结构:**
```json
{
  "parseResultId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "filename": "contract.docx",
  "chatgptPrompt": "请审查以下合同...",
  "instructions": ["复制提示", "粘贴到ChatGPT", ...],
  "parseResult": { "clauses": [...] },
  "documentWithAnchorsBase64": "UEsDBAoAAA...",
  "anchorsEnabled": true
}
```

---

#### 请求2: 导入审查结果

**前端 JavaScript:**
```javascript
// 【关键修复】传递 parseResultId 参数
let url = `/chatgpt/import-result?cleanupAnchors=true`;

if (chatgptParseResultId) {
    url += `&parseResultId=${encodeURIComponent(chatgptParseResultId)}`;
    // 使用缓存的带锚点文档 ✅
}

const response = await fetch(url, {
    method: 'POST',
    body: formData  // 包含 chatgptResponse (JSON 审查结果)
});
```

**后端 Java:**
```java
@PostMapping("/import-result")
public ResponseEntity<?> importResult(
    @RequestParam(value = "file", required = false) MultipartFile file,
    @RequestParam(value = "parseResultId", required = false) String parseResultId,
    @RequestParam("chatgptResponse") String chatgptResponse,
    @RequestParam(value = "cleanupAnchors", defaultValue = "true") boolean cleanupAnchors) {

    byte[] documentToAnnotate = null;

    // 【关键修复】优先使用缓存的带锚点文档
    if (parseResultId != null && !parseResultId.isEmpty()) {
        ParseResultCache.CachedParseResult cached =
            parseResultCache.retrieve(parseResultId);

        if (cached != null && cached.documentWithAnchorsBytes != null) {
            // ✅ 使用缓存的带锚点文档
            documentToAnnotate = cached.documentWithAnchorsBytes;
            logger.info("✅ [缓存命中] 成功使用缓存的带锚点文档");
        }
    }

    // 【降级】如果缓存失败，使用上传的文件
    if (documentToAnnotate == null && file != null) {
        documentToAnnotate = file.getBytes();
        logger.warn("⚠️ [降级方案] 使用用户上传的文件（不包含锚点）");
    }

    // 批注并返回
    byte[] annotatedDocument =
        xmlContractAnnotateService.annotateContractWithXml(
            mockFile, chatgptResponse, anchorStrategy, cleanupAnchors);

    return ResponseEntity.ok().body(new ByteArrayResource(annotatedDocument));
}
```

---

## 🐛 常见问题排查

### ❌ 问题 1: "defineIssues存在但都无法定位"

**症状：**
```
⚠️ 未找到anchorId对应的书签：anchorId=anc-c1-xxxx, 文档中总书签数=1
📌 锚点查找失败，回退到文本匹配
```

**原因：**
- 后端使用的是**不带锚点的原始文档**
- parseResultId 没有正确传递

**排查步骤：**
1. 打开浏览器 F12 控制台
2. 查看是否输出: `✅ 成功获取parseResultId: ...`
   - ✅ 有 → 问题在步骤2
   - ❌ 无 → 问题在步骤1

**解决方案：**
- ✅ 步骤1: 确保看到 `✅ 已生成锚点...` 的 toast
- ✅ 步骤2: 查看网络请求 (F12 Network)
  - 检查 `/import-result` 请求的 URL 参数
  - 应该包含: `?parseResultId=...&cleanupAnchors=...`

---

### ❌ 问题 2: "ChatGPT返回的JSON无法解析"

**症状：**
```
❌ ChatGPT响应格式错误，请检查JSON格式
```

**原因：**
- ChatGPT 返回的不是有效的 JSON
- 包含 markdown 代码块未正确清理

**排查步骤：**
1. 复制粘贴的内容包含了 markdown 标记?
   ```
   ```json
   { ... }
   ```
   ```
   → ✅ 系统会自动清理

2. JSON 字符串包含未转义的引号?
   → ❌ 需要在 ChatGPT 中修复

**解决方案：**
```javascript
// 系统自动清理以下格式：
// 1. ```json ... ``` → 移除 markdown 标记
// 2. ``` ... ``` → 移除 markdown 标记
// 3. 前后空白 → trim()

// 如果仍失败，检查 JSON 结构：
{
  "issues": [
    {
      "clauseId": "c1",
      "severity": "HIGH",
      "targetText": "要批注的文字",
      "finding": "找到的问题",
      "suggestion": "建议"
    }
  ]
}
```

---

### ❌ 问题 3: "下载的文件里没有批注"

**症状：**
- 文件下载成功
- 但打开后看不到 ChatGPT 的批注

**原因：**
1. 使用了原始文件（不带锚点）
2. targetText 与原文完全不匹配
3. ChatGPT 返回的 JSON 中没有 issues

**排查步骤：**
1. 检查完成提示：
   - ✅ `✅ 使用缓存的带锚点文档进行批注 - 定位精度最高`
   - ⚠️ `⚠️ 使用原始文件进行批注 - 定位精度可能降低`

2. 如果是 ⚠️ → 重新按步骤1生成（确保 parseResultId 被保存）

3. 查看后端日志：
   ```
   ✅ [缓存命中] 成功使用缓存的带锚点文档...
   或
   ⚠️ [降级方案] 使用用户上传的文件...
   ```

**解决方案：**
- 清空表单，重新从步骤1开始
- 确保 parseResultId 被正确保存和传递

---

### ❌ 问题 4: "parseResultId 已过期"

**症状：**
```
parseResultId 已过期且没有提供 file 参数
```

**原因：**
- parseResultId 超过 4 小时
- 缓存在服务器中被清理

**解决方案：**
- 重新调用 `/generate-prompt` 端点获取新的 parseResultId
- 缓存有效期为 4 小时，足以完成整个审查流程

---

## 📊 日志解读指南

### 前端日志 (浏览器控制台)

#### 生成提示时的日志

**✅ 成功情况：**
```javascript
✅ 成功获取parseResultId: a1b2c3d4-e5f6-7890-abcd-ef1234567890
```
→ parseResultId 已保存到 `chatgptParseResultId` 全局变量

**⚠️ 警告情况：**
```javascript
⚠️ 响应中未包含parseResultId，后续批注可能不精确
```
→ 后端没有返回 parseResultId，需要检查后端代码

#### 导入结果时的日志

**✅ 成功情况：**
```javascript
✅ 使用parseResultId进行批注: a1b2c3d4-e5f6-...
```
→ 使用了缓存的带锚点文档

**⚠️ 警告情况：**
```javascript
⚠️ parseResultId不存在，将使用原始文件进行批注（可能定位不精确）
⚠️ 无法使用parseResultId，批注定位精度可能降低
```
→ parseResultId 丢失或为空，降级到原始文件模式

---

### 后端日志 (服务器控制台)

#### 生成提示端点日志

```
🔍 [/generate-prompt] 请求参数:
   filename=contract.docx,
   contractType=technology,
   anchors=generate

【缓存】Parse 结果已存储:
   parseResultId=a1b2c3d4-e5f6-7890-abcd-ef1234567890,
   条款数=8,
   文档大小=45328 字节
```

#### 导入结果端点日志

**✅ 使用缓存文档：**
```
🔍 [/import-result] 请求参数:
   parseResultId=✓ a1b2c3d4-e5f6-7890-abcd-ef1234567890,
   hasFile=✓ contract.docx

🔍 [缓存检索] 尝试从缓存中检索parseResultId: a1b2c3d4-e5f6-...

✅ [缓存命中] 成功使用缓存的带锚点文档:
   parseResultId=a1b2c3d4-e5f6-...,
   大小=45328 字节,
   文件名=contract.docx
```

**⚠️ 使用原始文件：**
```
⚠️ [参数缺失] parseResultId 为空，将尝试使用 file 参数

⚠️ [降级方案] 使用用户上传的文件进行批注，可能不包含锚点。
   建议：请使用 parseResultId 参数以获得最佳效果。
```

#### 批注定位日志

**使用锚点定位（精确）：**
```
✅ [缓存命中] 成功使用缓存的带锚点文档...

开始查找目标段落：clauseId=c1, anchorId=anc-c1-4f21

✓ 通过anchorId找到对应的书签：anchorId=anc-c1-4f21, 文档中总书签数=8

使用精确文字匹配插入批注：...
```

**回退到文本匹配（可能不精确）：**
```
⚠️ [参数缺失] parseResultId 为空...

开始按anchorId查找段落：anchorId=anc-c1-4f21, 总段落数=60

? 未找到anchorId对应的书签：anchorId=anc-c1-4f21, 文档中总书签数=1
   锚点查找失败，回退到文本匹配

开始按文本匹配查找段落：clauseId=c1, 匹配模式数=10
```

---

## ✅ 完整验证清单

在批注后，检查以下项目确认成功：

- [ ] 前端日志显示 `✅ 成功获取parseResultId`
- [ ] 生成提示后看到 toast: `✅ 已生成锚点，parseResultId已保存`
- [ ] 导入结果后看到 toast: `✅ ChatGPT审查结果导入成功`
- [ ] 完成提示显示: `✅ 使用缓存的带锚点文档进行批注`
- [ ] 后端日志显示 `✅ [缓存命中]`
- [ ] 文件成功下载
- [ ] 打开文件后可看到 ChatGPT 的批注

---

## 🔄 系统演进及故障诊断

### 修复前的问题

| 环节 | 问题 | 症状 |
|------|------|------|
| 步骤1 | 返回 parseResultId ❌ | UI 无法获得缓存 ID |
| 步骤2 | 不支持 parseResultId 参数 ❌ | 只能使用原始文件 |
| UI 全局变量 | 没有存储机制 ❌ | ID 丢失无法传递 |
| 批注定位 | 全部回退文本匹配 | 定位不精确，遗漏批注 |
| 日志 | 缺少诊断信息 | 无法判断使用哪个文档 |

### 修复后的状态

| 环节 | 改进 | 验证方式 |
|------|------|--------|
| 步骤1 | ✅ 返回 parseResultId | 查看响应 JSON |
| 步骤2 | ✅ 支持 parseResultId 参数 | 查看 URL 参数 |
| UI 全局变量 | ✅ chatgptParseResultId | F12 输入检查 |
| 批注定位 | ✅ 优先使用锚点 | 查看后端日志 |
| 日志 | ✅ 详细诊断信息 | 查看 toast 和后端日志 |

---

## 📚 参考资源

### 相关文件

- **后端代码**: `ChatGPTIntegrationController.java` (第 60-312 行)
- **后端服务**: `XmlContractAnnotateService.java`
- **缓存服务**: `ParseResultCache.java`
- **前端代码**: `main.js` (第 770-1065 行)

### 相关端点

- `POST /chatgpt/generate-prompt` - 生成提示并缓存文档
- `POST /chatgpt/import-result` - 导入结果并批注【已修复】
- `POST /chatgpt/import-result-xml` - XML 专用端点（推荐）
- `GET /chatgpt/status` - 查看 ChatGPT 集成状态

---

**最后更新**: 2025-10-22
**状态**: ✅ 前后端已完整通联，支持 parseResultId 传递
