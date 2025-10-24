# Qwen 一键审查 - 功能演示与使用指南

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                        前端 (HTML/CSS/JS)                    │
│  ┌──────────────────┐  ┌──────────────────┐                 │
│  │  规则审查面板    │  │ Qwen一键审查按钮 │                 │
│  └────────┬─────────┘  └────────┬─────────┘                 │
│           │                     │                            │
│           └─────────────┬───────┘                            │
│                         │                                    │
└─────────────────────────┼────────────────────────────────────┘
                          │
                    API 调用 (JSON)
                          │
┌─────────────────────────┼────────────────────────────────────┐
│                 后端 (Spring Boot)                           │
│                         │                                    │
│              ┌──────────▼─────────┐                          │
│              │ QwenRuleReview     │                          │
│              │   Controller       │                          │
│              │ (API端点定义)      │                          │
│              └──────────┬─────────┘                          │
│                         │                                    │
│              ┌──────────▼─────────┐                          │
│              │ QwenRuleReview     │                          │
│              │    Service         │                          │
│              │ (业务逻辑)         │                          │
│              └──────────┬─────────┘                          │
│                         │                                    │
│              ┌──────────▼─────────┐                          │
│              │   QwenClient       │                          │
│              │ (API调用)          │                          │
│              └──────────┬─────────┘                          │
│                         │                                    │
└─────────────────────────┼────────────────────────────────────┘
                          │
                    HTTPS API Call
                          │
┌─────────────────────────┼────────────────────────────────────┐
│         Qwen AI Service (阿里云)                             │
│              dashscope.aliyuncs.com                          │
│                                                              │
│         审查 → JSON结果返回                                  │
└──────────────────────────────────────────────────────────────┘
```

## 详细使用步骤

### 步骤1: 规则审查阶段

```
操作:
1. 点击"规则审查"标签页
2. 点击"📁 选择合同文件"上传 .docx 文件
3. 选择合同类型: 采购/外包/NDA/通用合同
4. 选择审查立场: 中立/甲方/乙方
5. 点击"🔍 开始规则审查"

系统执行:
- 解析Word文档结构，提取所有条款
- 逐条匹配审查规则库 (rules.xlsx)
- 筛选出匹配的高风险条款
- 为Qwen生成结构化Prompt

输出:
✓ 统计信息 (总条款、匹配数、高风险数)
✓ 风险分布 (高/中/低)
✓ 匹配条款列表 (含规则详情)
✓ LLM审查Prompt (完整的检查清单)
```

### 步骤2: 一键Qwen审查

```
操作:
1. 在LLM审查Prompt下方，点击"⚡一键Qwen审查"按钮
   (紫色渐变按钮，位于"📋 复制Prompt"按钮左侧)

系统执行:
1. 显示进度提示: "⏳ 正在调用Qwen进行审查..."
2. 发送HTTP POST请求:
   POST /api/qwen/rule-review/review
   {
     "prompt": "根据以下规则审查合同条款...",
     "contractType": "采购合同",
     "stance": "A"
   }

3. Qwen处理 (约15-30秒):
   - 解析Prompt中的规则和条款
   - 逐项审查合同内容
   - 生成JSON格式的审查结果

4. 后端处理Qwen返回:
   - 提取JSON内容
   - 自动修复格式错误
   - 解析为ReviewIssue对象列表

5. 前端更新:
   - 显示成功提示: "✅ 审查完成！检出 N 个问题"
   - 自动填充JSON到"审查结果JSON"文本框
   - 更新进度图标: ⏳ → ✅
   - 自动滚动到导入部分

用户无需操作！全自动完成
```

### 步骤3: 导入与批注

```
操作:
1. 系统已自动填充Qwen的审查结果JSON
2. 确保勾选"☑ 批注完成后清理锚点" (推荐)
3. 点击"📥 导入并生成批注文档"

系统执行:
1. 验证JSON格式合法性
2. 调用批注API:
   POST /api/annotate
   - 使用带锚点的文档 (缓存中)
   - 使用JSON中的anchorId精确定位
   - 自动插入批注到对应位置

3. 批注完成后:
   - 可选清理锚点标记
   - 生成最终的DOCX文件
   - 自动下载到本地

输出:
✓ 带详细批注的Word文档
✓ 每个问题都精确定位到原文位置
✓ 包含风险等级、问题描述和修改建议
✓ 文件名: 原文件名_规则审查批注.docx
```

## 审查结果JSON格式说明

Qwen返回的JSON结构:

```json
{
  "issues": [
    {
      "anchorId": "anc-c2-8f3a",          // 锚点ID (精确定位)
      "clauseId": "c2",                   // 条款ID
      "severity": "HIGH",                 // 风险等级: HIGH/MEDIUM/LOW
      "category": "保密条款",              // 问题分类
      "finding": "未定义保密信息范围",      // 发现的问题
      "suggestion": "应增加保密信息的定义及披露条件。"  // 修改建议
    },
    {
      "anchorId": "anc-c5-7f2b",
      "clauseId": "c5",
      "severity": "MEDIUM",
      "category": "违约条款",
      "finding": "违约金计算方式不明确",
      "suggestion": "建议明确违约金的计算基数和比例"
    }
  ]
}
```

### 风险等级说明

| 等级 | 颜色 | 含义 | 处理 |
|------|------|------|------|
| HIGH | 🔴 红 | 高风险，必须修改 | 在签署前必须解决 |
| MEDIUM | 🟠 橙 | 中等风险，建议修改 | 争取修改，难以接受时需评估 |
| LOW | 🟡 黄 | 低风险，可考虑修改 | 可根据优先级选择修改 |

## 功能特性演示

### 特性1: 自动格式修复

```javascript
// 如果Qwen返回带有代码块的响应
Qwen返回:
"以下是审查结果:\n```json\n{\"issues\": [...]}\n```"

系统自动处理:
✓ 识别 ```json ... ``` 代码块
✓ 提取JSON内容
✓ 验证和修复格式错误
✓ 转换为JavaScript对象

用户感受: 无缝处理，完全透明
```

### 特性2: 立场化建议

```
甲方 (party=A):
- 更关注条款对自己的不利
- 倾向于争取有利条款
- 建议具有攻击性

乙方 (party=B):
- 更关注自我保护
- 倾向于接受合理条款
- 建议更为谨慎

中立 (Neutral):
- 客观分析条款
- 只指出问题所在
- 不倾向任何一方
```

### 特性3: 实时进度反馈

```
阶段1: 规则审查 (< 2秒)
✓ 条款解析完成
✓ 规则匹配完成
✓ Prompt生成完成

阶段2: Qwen审查 (15-30秒)
⏳ 正在调用Qwen...
✓ 接收到审查结果
✓ JSON解析完成

阶段3: 批注生成 (2-5秒)
✓ 锚点定位完成
✓ 批注插入完成
✓ 文档生成完成

进度提示: ⏳ → 💭 → ✅
```

## 集成示例

### 使用cURL调用API

```bash
# 1. 生成规则审查Prompt
curl -X POST http://localhost:8080/api/review/analyze \
  -F "file=@contract.docx" \
  -F "contractType=采购合同" \
  -F "party=A" > rule_review_result.json

# 2. 提取Prompt并调用Qwen
PROMPT=$(jq -r '.prompt' rule_review_result.json)

curl -X POST http://localhost:8080/api/qwen/rule-review/review \
  -H "Content-Type: application/json" \
  -d "{\"prompt\": \"$PROMPT\", \"contractType\": \"采购合同\", \"stance\": \"A\"}" \
  > qwen_result.json

# 3. 导入审查结果生成批注
REVIEW=$(jq -r '.review' qwen_result.json)
PARSE_ID=$(jq -r '.parseResultId' rule_review_result.json)

curl -X POST "http://localhost:8080/chatgpt/import-result?parseResultId=$PARSE_ID" \
  -H "Content-Type: application/json" \
  -d "{\"review\": $REVIEW, \"cleanupAnchors\": true}" \
  --output contract_annotated.docx
```

### Python集成示例

```python
import requests
import json
import time

# 1. 规则审查
with open('contract.docx', 'rb') as f:
    files = {'file': f}
    data = {'contractType': '采购合同', 'party': 'A'}
    r1 = requests.post('http://localhost:8080/api/review/analyze',
                       files=files, data=data)

rule_result = r1.json()
prompt = rule_result['prompt']
parse_id = rule_result['parseResultId']

# 2. 一键Qwen审查
payload = {
    'prompt': prompt,
    'contractType': '采购合同',
    'stance': 'A'
}
r2 = requests.post('http://localhost:8080/api/qwen/rule-review/review',
                   json=payload)

qwen_result = r2.json()
print(f"检出 {qwen_result['issueCount']} 个问题")
print(f"处理耗时: {qwen_result['processingTime']}")

# 3. 生成批注文档
headers = {'Content-Type': 'application/json'}
import_payload = {
    'review': qwen_result['review'],
    'cleanupAnchors': True
}
r3 = requests.post(
    f'http://localhost:8080/chatgpt/import-result?parseResultId={parse_id}',
    json=import_payload,
    headers=headers
)

with open('contract_annotated.docx', 'wb') as f:
    f.write(r3.content)
    print("✓ 批注文档已保存")
```

### JavaScript集成示例

```javascript
// 1. 规则审查
async function analyzeContract(file) {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('contractType', '采购合同');
    formData.append('party', 'A');

    const r1 = await fetch('/api/review/analyze', {
        method: 'POST',
        body: formData
    });
    return await r1.json();
}

// 2. 一键Qwen审查
async function reviewWithQwen(prompt, contractType = '采购合同') {
    const r2 = await fetch('/api/qwen/rule-review/review', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            prompt: prompt,
            contractType: contractType,
            stance: 'A'
        })
    });
    return await r2.json();
}

// 3. 使用示例
const file = document.getElementById('file-input').files[0];
const analysis = await analyzeContract(file);
const qwenResult = await reviewWithQwen(analysis.prompt);

console.log(`检出 ${qwenResult.issueCount} 个问题`);
console.log(`耗时: ${qwenResult.processingTime}`);
```

## 监控和日志

### 关键日志消息

```
启动时:
INFO  - Qwen服务已就绪
DEBUG - 配置: model=qwen-max-latest, timeout=30s

规则审查:
INFO  - 开始合同审查分析
DEBUG - 解析完成，共 42 个条款
DEBUG - 规则加载完成，共 25 条规则
DEBUG - 匹配完成，检出 12 个需要审查的条款

Qwen审查:
INFO  - 收到Qwen规则审查请求
INFO  - Qwen返回内容长度: 1234 字符
DEBUG - 从大括号中提取JSON
INFO  - 成功解析 8 个审查问题

批注生成:
INFO  - 开始生成批注文档
DEBUG - 锚点定位成功: anc-c2-8f3a
INFO  - 批注完成，共 8 个问题已批注
```

### 性能监控

```javascript
// 前端性能监控
const metrics = {
    ruleReviewTime: 0,     // 规则审查耗时
    qwenReviewTime: 0,     // Qwen审查耗时
    annotationTime: 0,     // 批注生成耗时
    totalTime: 0           // 总耗时
};

// 在请求前后记录时间戳
const start = performance.now();
// ... 执行操作 ...
metrics.qwenReviewTime = performance.now() - start;

// 输出到控制台
console.table(metrics);
```

## 故障诊断

### 问题1: "Qwen服务未配置"

**症状**: 一键按钮灰色不可用

**排查步骤**:
```bash
# 步骤1: 检查配置
curl http://localhost:8080/api/qwen/rule-review/config

# 步骤2: 查看服务状态
curl http://localhost:8080/api/qwen/rule-review/status

# 步骤3: 检查日志
tail -f logs/application.log | grep -i qwen

# 步骤4: 确认application.properties
cat src/main/resources/application.properties | grep qwen
```

**解决方案**:
```properties
# application.properties
qwen.api-key=sk-your-real-key-here    # ← 必须填写真实key
qwen.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1
qwen.model=qwen-max-latest
qwen.timeout=30
```

### 问题2: "请先执行规则审查"

**症状**: 点击一键按钮报错

**排查步骤**:
1. 检查是否执行了"开始规则审查"
2. 检查Prompt框中是否有内容
3. 查看浏览器控制台 (F12 → Console)

**解决方案**:
```javascript
// 在浏览器控制台检查
const prompt = document.getElementById('rule-review-prompt').textContent;
console.log('Prompt长度:', prompt.length);
console.log('Prompt内容:', prompt.substring(0, 100));
```

### 问题3: "超时错误"

**症状**: 审查超过30秒无响应

**排查步骤**:
```bash
# 步骤1: 检查网络连接
ping dashscope.aliyuncs.com

# 步骤2: 测试API连接
curl -v https://dashscope.aliyuncs.com/

# 步骤3: 增加超时时间
# 在application.properties中修改:
qwen.timeout=60
```

**解决方案**:
1. 检查网络连接
2. 增加timeout配置
3. 检查Prompt长度（过长会导致审查慢）

## 最佳实践

### ✅ 建议做法

1. **选择精确的合同类型**
   ```
   采购合同 → 加载采购相关规则 → 更准确的审查
   ```

2. **根据需求选择立场**
   ```
   甲方 → 关注对自己的不利条款
   乙方 → 关注对乙方的不合理要求
   ```

3. **定期更新规则库**
   ```
   编辑rules.xlsx → 添加企业特定规则 → 更贴近实际需求
   ```

4. **使用批注清理**
   ```
   ☑ 清理锚点 → 最终文档更干净
   ```

### ❌ 避免做法

1. **不要上传错误格式**
   - 只支持 .docx 和 .doc
   - 确保文件不损坏

2. **不要跳过规则审查直接用Qwen**
   - 规则审查能减少80%的token消耗
   - 提高审查效率

3. **不要频繁重复审查同一文件**
   - 结果不会变化
   - 浪费API调用

4. **不要忽视错误提示**
   - 每个错误都有原因
   - 按照提示排查问题

## 扩展功能建议

### 近期 (1-2周)
- [ ] 支持批量审查 (多文件)
- [ ] 审查历史记录
- [ ] 导出审查报告 (PDF)

### 中期 (1-3个月)
- [ ] 自定义规则管理UI
- [ ] 审查模板库
- [ ] 集成企业知识库

### 长期 (3-6个月)
- [ ] 与法务系统对接
- [ ] AI驱动的风险评分
- [ ] 合同智能修改建议
- [ ] 多语言支持

---

**文档版本**: v1.0
**最后更新**: 2025-10-24
**维护者**: AI Contract Review Team
