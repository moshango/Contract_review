# 快速开始 - 规则审查新功能

## 功能说明

规则审查模块已升级，现在使用 Qwen AI 自动识别合同中的甲乙方信息，替代原有的文本搜索。

## 工作流程

```
1. 上传合同文件
   ↓
2. 系统自动解析并调用 Qwen 识别甲乙方
   ↓
3. 显示识别结果（甲方名称、乙方名称、推荐立场）
   ↓
4. 用户选择自己的身份（甲方或乙方）
   ↓
5. 系统根据选择的立场进行规则审查
   ↓
6. 显示审查结果、条款分析、生成 Prompt
```

## 前端使用

### 步骤 1：选择文件和合同类型
- 点击"📁 选择合同文件"选择 .docx 或 .doc 文件
- 选择合同类型（通用/采购/外包/NDA）

### 步骤 2：开始规则审查
- 点击"🔍 开始规则审查"按钮
- 系统自动识别甲乙方信息（需要 10-20 秒）

### 步骤 3：选择立场
- 查看识别的甲方和乙方名称
- 查看推荐立场和理由
- 点击"选择甲方立场"或"选择乙方立场"

### 步骤 4：查看审查结果
- 查看统计信息（总条款数、匹配条款数、高风险数）
- 查看风险分布（高/中/低）
- 查看匹配的条款详情和触发规则
- 复制生成的 Prompt 到 ChatGPT/Qwen 进行 AI 审查

## 后端 API

### 新增端点

**POST /api/review/extract-parties**

识别合同中的甲乙方信息。

**请求示例：**
```bash
curl -X POST http://localhost:8080/api/review/extract-parties \
  -H "Content-Type: application/json" \
  -d '{
    "contractText": "甲方：ABC采购公司\n乙方：XYZ供应商...",
    "contractType": "采购合同"
  }'
```

**响应示例（成功）：**
```json
{
  "success": true,
  "partyA": "ABC采购有限公司",
  "partyB": "XYZ供应股份公司",
  "partyARoleName": "甲方（采购方）",
  "partyBRoleName": "乙方（供应方）",
  "recommendedStance": "A",
  "stanceReason": "甲方作为买方/采购方，需要重点关注产品质量、交付期限和违约责任条款。",
  "processingTime": "2500ms"
}
```

## 系统配置

### 必须条件

确保 `application.properties` 中已配置 Qwen：

```properties
# Qwen 配置
qwen.api-key=sk-xxxxxxxxxxxx
qwen.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1
```

### 验证配置

访问以下端点检查 Qwen 服务状态：

```bash
GET http://localhost:8080/api/qwen/rule-review/status
```

## 同义标签支持

系统自动识别以下标签变体：

| 原始表述 | 映射到 |
|--------|--------|
| 甲方 | A 方 |
| 买方、需方、发包人、客户、用户、委托方 | A 方 |
| 乙方 | B 方 |
| 卖方、供方、承包人、服务商、承接方、受托方 | B 方 |

## 常见问题

### Q1: 如果无法识别甲乙方怎么办？
A: 系统会返回错误信息。请确保：
- 合同文本清晰包含甲乙方名称
- Qwen 服务正常配置并可访问
- 合同类型正确选择

### Q2: 识别需要多长时间？
A: 通常需要 10-20 秒，取决于网络和 Qwen 服务响应时间。

### Q3: 可以修改识别的甲乙方名称吗？
A: 当前版本不支持手动修改。识别结果直接用于后续规则匹配。

### Q4: 如果合同中有多于两方怎么办？
A: 系统只识别甲乙两方，丙方及以后的方会被忽略。

### Q5: 同一个合同可以用不同立场审查吗？
A: 可以。重新点击"开始规则审查"，选择不同立场进行分析。

## 日志查看

前端控制台（F12）中可以查看详细日志：

```javascript
// 查看识别的甲乙方
console.log(window.currentPartyExtractionResult);

// 查看审查结果
console.log(window.ruleReviewResult);

// 查看 parseResultId（用于后续批注）
console.log(window.ruleReviewParseResultId);
```

## 性能指标

- 文件解析：1-3 秒
- Qwen 识别：8-15 秒
- 规则审查：2-5 秒
- **总耗时：11-23 秒**（取决于文件大小和网络状况）

## 故障排除

### 问题：提示"Qwen 服务未配置或不可用"

**解决方案：**
1. 检查 `application.properties` 中的 Qwen 配置
2. 验证 API Key 和 Base URL 是否正确
3. 测试网络连接到 Qwen 服务
4. 查看后端日志获取详细错误信息

### 问题：无法识别甲乙方

**解决方案：**
1. 确保合同文本中有明确的甲乙方标识
2. 检查合同类型是否正确
3. 查看浏览器控制台的详细错误信息
4. 尝试简化合同文本（仅保留前几个段落）

### 问题：请求超时

**解决方案：**
1. 检查网络连接
2. 增加请求超时时间设置
3. 减少合同文本大小
4. 检查 Qwen 服务状态

## 文件位置

- 后端代码：`src/main/java/com/example/Contract_review/service/PartyExtractionService.java`
- 前端代码：`src/main/resources/static/js/party-extraction.js`
- API 端点：`src/main/java/com/example/Contract_review/controller/ApiReviewController.java`

## 相关文档

- CLAUDE.md - 项目总体规范
- IMPLEMENTATION_SUMMARY.md - 详细实现总结
