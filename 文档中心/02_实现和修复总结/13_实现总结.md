# 规则审查模块 - 合同方提取功能实现总结

## 功能概述

实现了基于 Qwen 的合同方自动识别功能，取代原有的文本搜索逻辑。用户上传文件后，系统自动：
1. 解析合同文本
2. 调用 Qwen 识别甲乙方信息
3. 显示识别结果
4. 用户选择立场后进行规则审查

## 核心改动

### 后端改动

#### 1. 新增 DTO 模型

**PartyExtractionRequest.java**
- `contractText`: 合同文本（前3000字）
- `contractType`: 合同类型
- `parseResultId`: 可选的解析结果ID

**PartyExtractionResponse.java**
- `success`: 是否成功
- `partyA`: 甲方名称（A方）
- `partyB`: 乙方名称（B方）
- `partyARoleName`: 原始角色名称（如"甲方"、"买方"等）
- `partyBRoleName`: 原始角色名称（如"乙方"、"卖方"等）
- `recommendedStance`: 推荐立场（"A" 或 "B"）
- `stanceReason`: 推荐理由
- `errorMessage`: 错误消息
- `processingTime`: 处理耗时

#### 2. 新增 PartyExtractionService

核心服务类，功能包括：

**`extractPartyInfoWithQwen(PartyExtractionRequest)`**
- 调用 Qwen API 识别甲乙方
- 支持各种同义标签映射：
  - 映射到 A 方：甲方、买方、需方、发包人、客户、订购方、用户、委托方
  - 映射到 B 方：乙方、卖方、供方、承包人、服务商、承接方、受托方
- 只返回甲乙两方，忽略丙方及以后的方
- 返回识别结果和推荐立场

**Qwen 提示词特性：**
- 清晰的识别规则指导
- 多个示例展示不同场景
- 严格的 JSON 格式要求
- 3000 字上下文限制（优化 token 消耗）

#### 3. 新增 API 端点

**POST /api/review/extract-parties**

请求体：
```json
{
  "contractText": "合同文本内容...",
  "contractType": "采购合同",
  "parseResultId": "optional-id"
}
```

响应示例（成功）：
```json
{
  "success": true,
  "partyA": "ABC采购有限公司",
  "partyB": "XYZ供应股份公司",
  "partyARoleName": "甲方（采购方）",
  "partyBRoleName": "乙方（供应方）",
  "recommendedStance": "A",
  "stanceReason": "甲方作为买方/采购方，需要重点关注产品质量、交付期限和违约责任条款。",
  "processingTime": "2500ms",
  "timestamp": 1729788601000
}
```

#### 4. 修改 ApiReviewController

- 注入 `PartyExtractionService`
- 添加 `/api/review/extract-parties` 端点
- 验证 Qwen 服务可用性
- 返回结构化的合同方信息

### 前端改动

#### 1. 新增 JavaScript 模块 (party-extraction.js)

**主要函数：**

- `extractRuleReviewParties()`:
  - 第一步：解析合同文件
  - 第二步：调用后端提取合同方
  - 显示识别结果

- `displayPartyExtractionResult(result, contractType)`:
  - 隐藏原有立场选择单选框
  - 显示识别的甲乙方信息及其角色
  - 显示推荐理由
  - 提供两个选择按钮（选择甲方或乙方）

- `selectRuleReviewStance(stance)`:
  - 用户选择立场后调用
  - 继续进行规则审查分析
  - 显示审查结果

- `displayRuleReviewResults(analysisResult)`:
  - 显示统计信息、风险分布
  - 显示匹配的条款
  - 显示生成的 Prompt

- `displayRuleReviewClauses(clauses)`:
  - 格式化展示匹配的条款

#### 2. 修改 main.js

- 修改 `startRuleReview()` 函数
- 改为调用 `extractRuleReviewParties()` 而不是直接进行审查

#### 3. 修改 index.html

- 隐藏原有的立场选择单选框（设置 `display: none`）
- 保留原有的合同方信息展示区域
- 添加 party-extraction.js 脚本引入

## 工作流程

1. **用户上传文件**
   - 选择合同文件
   - 选择合同类型
   - 点击"开始规则审查"

2. **系统自动识别合同方**
   - 解析合同文件
   - 调用 Qwen 识别甲乙方和推荐立场
   - 隐藏立场选择单选框
   - 显示识别结果和两个选择按钮

3. **用户选择立场**
   - 查看识别的甲乙方名称和角色
   - 查看推荐理由
   - 点击"选择甲方立场"或"选择乙方立场"

4. **系统进行规则审查**
   - 根据用户选择的立场调用 `/api/review/analyze`
   - 显示规则匹配结果
   - 显示风险分布和条款详情
   - 生成 Prompt 供后续 LLM 审查

5. **用户导入 LLM 审查结果**
   - 复制 Prompt 到 ChatGPT/Qwen
   - 获取 JSON 格式的审查结果
   - 导入批注文档

## 关键特性

### 1. 同义标签映射

系统自动识别各种合同表述方式：

| 映射到 A 方 | 映射到 B 方 |
|-----------|-----------|
| 甲方 | 乙方 |
| 买方 | 卖方 |
| 需方 | 供方 |
| 发包人 | 承包人 |
| 客户 | 服务商 |
| 订购方 | 承接方 |
| 用户 | 受托方 |
| 委托方 | - |

### 2. 严格的甲乙方识别

- ✅ 只识别和返回甲乙两方
- ✅ 忽略丙方、丁方等其他方
- ✅ 不返回个人名字或职位名称，只返回公司名称
- ✅ 支持各种合同格式和标签方式

### 3. 智能推荐立场

Qwen 分析合同内容后，根据：
- 合同类型
- 合同方角色
- 条款内容特点

推荐对应方进行审查

### 4. 性能优化

- 文本限制在 3000 字以减少 token 消耗
- 异步处理，不阻塞 UI
- JSON 提取和验证机制
- 错误降级处理

## 文件清单

### Java 文件
- `src/main/java/.../model/PartyExtractionRequest.java` (新增)
- `src/main/java/.../model/PartyExtractionResponse.java` (新增)
- `src/main/java/.../service/PartyExtractionService.java` (新增)
- `src/main/java/.../controller/ApiReviewController.java` (修改)

### JavaScript 文件
- `src/main/resources/static/js/party-extraction.js` (新增)
- `src/main/resources/static/js/main.js` (修改)
- `src/main/resources/static/index.html` (修改)

## 使用 Qwen 提示词示例

系统生成的提示词包含：
1. 清晰的识别规则
2. 同义标签映射说明
3. 多个返回示例
4. JSON 格式要求
5. 重要提醒

确保 Qwen 返回的结果格式一致且可解析。

## 后续扩展

- 支持更多合同类型和标签变体
- 集成立场历史记录
- 缓存识别结果避免重复调用
- 支持自定义标签映射规则
- 提供识别结果的人工审核机制

## 测试建议

1. 测试不同合同类型（采购、外包、NDA）
2. 测试不同标签方式（甲乙方、买卖方、发承包人）
3. 测试识别失败的处理（合同缺少方信息）
4. 测试网络超时和异常处理
5. 性能测试（大文件处理）
