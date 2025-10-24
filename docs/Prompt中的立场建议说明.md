# Prompt 中的立场建议 - 完整说明

## 问题回答

**用户问题**: "生成的prompt中没有包含对立场方的审查建议吗？请告诉我区分立场的功能体现在哪里"

**答案**: ✅ **已修复！现在 Prompt 中包含了完整的立场相关建议**

---

## 功能体现位置

### 1️⃣ Prompt 顶部的立场指导部分

**位置**: `PromptGeneratorNew.generateFullPrompt()` 中的"【立场审查指导】"部分

```
【合同信息】
合同类型: 采购合同
审查立场: 甲方                           ← 【关键】显示用户的立场
需要审查的条款数: 8

【立场审查指导】                          ← 【关键】根据立场显示不同的指导
您正在代表「甲方」进行合同审查。
请重点关注对甲方不利的条款，提出对甲方有利的修改建议。
特别注意：
- 那些可能增加甲方成本或责任的条款
- 那些限制甲方权利或灵活性的条款
- 那些对甲方不公平或风险较大的条款
```

### 2️⃣ 每个条款的【立场建议】部分

**位置**: `PromptGeneratorNew.generateClausePrompt()` 中每条规则的"【立场建议】"部分

```
【审查要点】
● 风险等级: HIGH
  匹配关键词: 付款方式, 支付周期, 付款条件
  检查清单:
  1. 确认付款方式
  2. 明确付款周期
  【立场建议】建议在合同中明确约定付款方式和周期    ← 【关键】针对甲方的建议

  【立场建议】建议要求甲方按时支付               ← 针对乙方的建议
```

---

## 代码实现细节

### 核心函数

#### 1. `PromptGeneratorNew.getSuggestionForStance()`

```java
private static String getSuggestionForStance(ReviewRule rule, ReviewStance stance) {
    if (stance == null || stance.getParty() == null) {
        return ""; // 中立立场不需要特殊建议
    }

    if ("A".equalsIgnoreCase(stance.getParty())) {
        return rule.getSuggestA() != null ? rule.getSuggestA() : "";  // 返回甲方建议
    } else if ("B".equalsIgnoreCase(stance.getParty())) {
        return rule.getSuggestB() != null ? rule.getSuggestB() : "";  // 返回乙方建议
    }

    return "";
}
```

**作用**: 根据用户的立场（A/B/Neutral）从规则中提取对应的建议

#### 2. `PromptGeneratorNew.generateClausePrompt(RuleMatchResult, ReviewStance)`

**关键改进**:
```java
// 【新增】添加针对立场的建议
String suggestion = getSuggestionForStance(rule, stance);
if (suggestion != null && !suggestion.isEmpty()) {
    prompt.append("  【立场建议】").append(suggestion).append("\n");
}
```

为每条匹配的规则添加针对用户立场的建议

#### 3. `PromptGeneratorNew.generateFullPrompt()`

**关键改进**:
```java
// 【新增】立场相关的审查指导
if (stance != null && stance.getParty() != null) {
    fullPrompt.append("【立场审查指导】\n");
    fullPrompt.append("您正在代表「").append(stance.getDescription()).append("」进行合同审查。\n");

    if ("A".equalsIgnoreCase(stance.getParty())) {
        fullPrompt.append("请重点关注对甲方不利的条款，提出对甲方有利的修改建议。\n");
        fullPrompt.append("特别注意：\n");
        fullPrompt.append("- 那些可能增加甲方成本或责任的条款\n");
        fullPrompt.append("- 那些限制甲方权利或灵活性的条款\n");
        fullPrompt.append("- 那些对甲方不公平或风险较大的条款\n");
    } else if ("B".equalsIgnoreCase(stance.getParty())) {
        fullPrompt.append("请重点关注如何保护乙方的利益，提出对乙方有利的修改建议。\n");
        fullPrompt.append("特别注意：\n");
        fullPrompt.append("- 那些可能增加乙方责任或风险的条款\n");
        fullPrompt.append("- 那些对乙方不公平的付款或交付条件\n");
        fullPrompt.append("- 那些限制乙方灵活性或权利的条款\n");
    }
}
```

---

## 示例对比

### 示例：采购合同中的"付款条款"规则

#### rules.xlsx 中的规则定义

| 字段 | 值 |
|------|-----|
| risk | high |
| keywords | 付款方式;支付周期;付款条件 |
| checklist | 1. 确认付款方式<br>2. 明确付款周期 |
| **suggestA** | **建议在合同中明确约定付款方式和周期，保护甲方资金流** |
| **suggestB** | **建议争取合理的付款期限（如30天），并明确付款责任** |

#### 甲方视角的 Prompt

```
【立场审查指导】
您正在代表「甲方」进行合同审查。
请重点关注对甲方不利的条款，提出对甲方有利的修改建议。
特别注意：
- 那些可能增加甲方成本或责任的条款
- 那些限制甲方权利或灵活性的条款
- 那些对甲方不公平或风险较大的条款

...

【条款】c1 - 第一条 付款条款

【原文】
[段落1] (anc-c1-p1-xxxx)
甲方应在收货后30天内完成付款...

【审查要点】
● 风险等级: HIGH
  匹配关键词: 付款方式, 支付周期, 付款条件
  检查清单:
  1. 确认付款方式
  2. 明确付款周期
  【立场建议】建议在合同中明确约定付款方式和周期，保护甲方资金流

【审查要求】
1. 根据上述检查要点对该条款进行审查
2. 如果发现问题，请从【原文】部分精确摘取需要修改的文字...
```

#### 乙方视角的 Prompt

```
【立场审查指导】
您正在代表「乙方」进行合同审查。
请重点关注如何保护乙方的利益，提出对乙方有利的修改建议。
特别注意：
- 那些可能增加乙方责任或风险的条款
- 那些对乙方不公平的付款或交付条件
- 那些限制乙方灵活性或权利的条款

...

【条款】c1 - 第一条 付款条款

【原文】
[段落1] (anc-c1-p1-xxxx)
甲方应在收货后30天内完成付款...

【审查要点】
● 风险等级: HIGH
  匹配关键词: 付款方式, 支付周期, 付款条件
  检查清单:
  1. 确认付款方式
  2. 明确付款周期
  【立场建议】建议争取合理的付款期限（如30天），并明确付款责任

【审查要求】
1. 根据上述检查要点对该条款进行审查
2. 如果发现问题，请从【原文】部分精确摘取需要修改的文字...
```

---

## 工作流程图

```
用户选择立场
    ↓
POST /api/review/analyze?party=A (或 B)
    ↓
【ApiReviewController.analyzeContract()】
    ├─ 获取用户立场: ReviewStance stance = reviewStanceService.getStance()
    │  （此时 stance.getParty() = "A" 或 "B"）
    ↓
    ├─ 规则匹配（按立场过滤）
    │  只返回 partyScope='A' 或 'Neutral' 的规则
    ↓
【PromptGeneratorNew.generateFullPrompt(results, contractType, stance)】
    ├─ 生成"【立场审查指导】"部分
    │  → 显示用户立场
    │  → 显示针对该立场的关注重点
    ↓
    ├─ 对每条规则调用 generateClausePrompt(result, stance)
    │  ├─ 调用 getSuggestionForStance(rule, stance)
    │  ├─ 根据立场返回 suggestA 或 suggestB
    │  └─ 添加"【立场建议】"部分
    ↓
【返回 Prompt】
    → 包含完整的立场相关信息
    → LLM 根据立场生成审查结果
```

---

## 验证方式

### 1. 查看代码

**新建文件**: `PromptGeneratorNew.java`
- 位置: `src/main/java/com/example/Contract_review/util/PromptGeneratorNew.java`
- 关键方法: `generateFullPrompt()`、`generateClausePrompt()`、`getSuggestionForStance()`

**修改文件**: `ApiReviewController.java` (第 152 行)
```java
String prompt = PromptGeneratorNew.generateFullPrompt(matchResults, contractType, stance);
```

### 2. 测试 Prompt 内容

上传合同，指定不同的立场（party=A 或 party=B），获取响应中的 `prompt` 字段：

```bash
# 甲方视角
curl -X POST "http://localhost:8080/api/review/analyze?contractType=采购&party=A" \
  -F "file=@contract.docx"

# 乙方视角
curl -X POST "http://localhost:8080/api/review/analyze?contractType=采购&party=B" \
  -F "file=@contract.docx"
```

对比两个响应的 `prompt` 字段，会发现：
1. "【立场审查指导】" 部分不同
2. 每条规则的"【立场建议】" 不同（suggestA vs suggestB）

---

## 相关字段说明

### ReviewRule 中的立场相关字段

| 字段 | 说明 | 示例 |
|------|------|------|
| `partyScope` | 规则适用范围 | `"A"` / `"B"` / `"Neutral"` |
| `suggestA` | **对甲方的建议** | "建议明确付款方式，保护甲方资金" |
| `suggestB` | **对乙方的建议** | "建议争取合理的付款期限" |

### ReviewStance 中的字段

| 字段 | 说明 | 值 |
|------|------|-----|
| `party` | 用户立场 | `"A"` / `"B"` / `null` |
| `description` | 立场描述 | `"甲方"` / `"乙方"` / `"中立"` |

---

## 三种立场的 Prompt 区别

### 立场 A（甲方）

```
【立场审查指导】
您正在代表「甲方」进行合同审查。
请重点关注对甲方不利的条款，提出对甲方有利的修改建议。
特别注意：
- 那些可能增加甲方成本或责任的条款
- 那些限制甲方权利或灵活性的条款
- 那些对甲方不公平或风险较大的条款
```

**规则建议**: 使用 `suggestA`

### 立场 B（乙方）

```
【立场审查指导】
您正在代表「乙方」进行合同审查。
请重点关注如何保护乙方的利益，提出对乙方有利的修改建议。
特别注意：
- 那些可能增加乙方责任或风险的条款
- 那些对乙方不公平的付款或交付条件
- 那些限制乙方灵活性或权利的条款
```

**规则建议**: 使用 `suggestB`

### 立场 Neutral（中立）

```
【立场审查指导】
（无）

【审查规则说明】
系统已通过关键字和规则识别出以下可能存在风险的条款...
```

**规则建议**: 无（或通用建议）

---

## 小结

### 区分立场的功能体现在：

✅ **1. Prompt 顶部** - "【立场审查指导】"部分清晰标明用户立场和关注重点

✅ **2. 每条规则下** - "【立场建议】"部分返回对应立场的建议（suggestA 或 suggestB）

✅ **3. 规则过滤** - 后端按立场过滤规则，只返回适用的规则

✅ **4. LLM 指引** - Prompt 中明确告诉 LLM 从哪个立场审查合同

### 工作验证：

- ✅ 编译成功：`BUILD SUCCESS`
- ✅ 新建文件：`PromptGeneratorNew.java`
- ✅ 修改文件：`ApiReviewController.java` 第 152 行已更新
- ✅ 函数签名：`generateFullPrompt(results, contractType, stance)` 支持立场参数

---

**更新时间**: 2025-10-23
**功能状态**: ✅ 完成并实现

