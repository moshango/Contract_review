# matchPattern vs matchIndex - 可视化对比

---

## 🎯 一张图理解两个概念

```
原文本：
"甲方应在30天内交付初稿，30天内完成全部交付，60天内乙方应完成审核。"

matchPattern决定：你怎么找
matchIndex决定：找到的第几个

┌─────────────────────────────────────────────────────────────┐
│ targetText: "30天"                                          │
│ matchPattern: "CONTAINS"  ← 只要包含就行                    │
│                                                             │
│ 找到的结果：                                               │
│   [30天]①   ← matchIndex=1 (第1个)
│    "甲方应在30天内交付初稿，                               │
│             ↑                                              │
│   [30天]②   ← matchIndex=2 (第2个)
│    30天内完成全部交付，                                   │
│      ↑                                                     │
│   找不到60天 ← 因为targetText是"30天"                     │
└─────────────────────────────────────────────────────────────┘
```

---

## 📊 三种matchPattern的可视化对比

### 场景：
```
文档段落: "保密信息应包括所有商业机密，保密期限为5年。"

要批注的关键词: "保密"
```

### EXACT模式

```
寻找完全相同的 "保密"

✅ 匹配: "保密"
✅ 匹配: "保密"
❌ 不匹配: "保密信息" (不是完全相同，多了"信息")
❌ 不匹配: "商业机密" (虽然包含但不相同)

结果: 会找到2个匹配
```

### CONTAINS模式

```
寻找包含 "保密" 的任何文字

✅ 匹配: "保密"
✅ 匹配: "保密期限"
✅ 匹配: "保密信息"
✅ 匹配: "商业机密"
✅ 匹配: "保密期"

结果: 会找到多个匹配
```

### REGEX模式

```
寻找匹配正则表达式 "保.*密" 的文字

✅ 匹配: "保密"
✅ 匹配: "保护信息机密"
✅ 匹配: "保证保密"
❌ 不匹配: "机密" (没有"保"字开头)

结果: 根据正则规则找到匹配
```

---

## 🔄 matchIndex的实际应用

### 场景：同一文字出现3次

```
"甲方应在30天内交付，
 如超过30天乙方有权拒收，
 超过60天可以解除合同"

现在用: "30天"
```

```
EXACT模式 + 不同的matchIndex:

matchIndex=1 ← 第1个"30天"
  甲方应在[30天]内交付，
           ↑ 被批注

matchIndex=2 ← 第2个"30天"
  如超过[30天]乙方有权拒收，
          ↑ 被批注

❌ matchIndex=3 ← 没有第3个"30天"
  会出错或使用默认值
```

---

## 📋 matchPattern对应的Java实现

```java
if ("EXACT".equalsIgnoreCase(matchType)) {
    // 精确匹配：text.indexOf(pattern) + pattern.length()
    // 找不到下一个就停止

} else if ("CONTAINS".equalsIgnoreCase(matchType)) {
    // 包含匹配：text.indexOf(pattern) 但每次只前进1个字符
    // 允许重叠，所以可能找到更多匹配

} else if ("REGEX".equalsIgnoreCase(matchType)) {
    // 正则匹配：Pattern.compile(pattern).matcher(text).find()
    // 按正则规则查找
}

// 然后根据matchIndex选择第几个
int selectedMatch = positions.get(matchIndex - 1);  // matchIndex是1-based
```

---

## 🎁 快速选择表

### 场景 → 最佳配置

| 场景 | targetText | matchPattern | matchIndex | 说明 |
|------|-----------|--------------|-----------|------|
| 批注唯一标题 | "五、保密与数据安全" | EXACT | 1 | 标题唯一，精确即可 |
| 批注关键词（单次） | "全部经济损失" | CONTAINS | 1 | 只要包含，默认第1个 |
| 批注关键词（多次） | "30天" | CONTAINS | 2 | 同词多次，选第2个 |
| 批注模式（如时间） | `\d{1,2}天` | REGEX | 1 | 灵活匹配各种时间 |
| 批注模式（多次） | `\d{1,2}天` | REGEX | 3 | 同模式多次，选第3个 |

---

## 💡 常见错误与解决

### ❌ 错误1：matchIndex过大

```json
{
  "targetText": "30天",
  "matchPattern": "CONTAINS",
  "matchIndex": 5  // ❌ 文档中只有2个"30天"
}
```

**结果**: 会降级到段落级别批注（自动降级）

**解决**: 先用matchIndex=1测试，确认文字存在

---

### ❌ 错误2：EXACT模式太严格

```json
{
  "targetText": "在30天内",
  "matchPattern": "EXACT"
}
```

文档中如果是"在 30天内"（多了一个空格）就匹配不了

**解决**: 改用CONTAINS模式
```json
{
  "targetText": "30天内",
  "matchPattern": "CONTAINS"
}
```

---

### ❌ 错误3：REGEX写法错误

```json
{
  "targetText": "30天",      // ❌ 这不是REGEX，没有特殊字符
  "matchPattern": "REGEX"
}
```

应该这样写：
```json
{
  "targetText": "[0-9]{2}天",  // ✅ 这才是REGEX
  "matchPattern": "REGEX"
}
```

或者
```json
{
  "targetText": "\\d{2}天",    // ✅ 这也是REGEX
  "matchPattern": "REGEX"
}
```

---

## 🎯 决策树

```
我要批注一个文字

            ↓
      文字出现几次？
        ↙        ↘
      1次          多次
       ↓            ↓
   ↙ 何时？     匹配第几个？
EXACT    CONTAINS   ↙  ↓  ↘
  ↓         ↓      1  2  3...
只能      可能    都能用
一个      多个    CONTAINS

格式固定？
  ↙  ↘
 是   否
 ↓    ↓
EXACT CONTAINS
     ↓
  有特殊需求？
    ↙  ↘
   是   否
   ↓    ↓
  REGEX CONTAINS
```

---

## 📈 性能对比

| matchPattern | 性能 | 灵活性 | 匹配数量 |
|---|---|---|---|
| EXACT | ⚡⚡⚡ 最快 | 低 | 最少 |
| CONTAINS | ⚡⚡ 快 | 中 | 中等 |
| REGEX | ⚡ 较慢 | 高 | 最多 |

**建议**: 能用EXACT就用EXACT（性能最好）

---

## 🧪 实验：用不同模式批注同一段文字

```
原文: "甲方应在合理期限内交付，合理期限应不超过30天。"

实验1: EXACT模式
  targetText: "30天"
  结果: ✅ 1个匹配

实验2: CONTAINS模式
  targetText: "30"
  结果: ✅ 2个匹配（"30天"和"30"）

实验3: REGEX模式
  targetText: "\\d{1,2}天"
  结果: ✅ 1个匹配（只有"30天"符合\d{1,2}天）

实验4: REGEX模式（更灵活）
  targetText: "\\d+"
  结果: ✅ 2个匹配（"30"出现了2次）
```

---

## ✅ 核心要点

```
┌─────────────────────────────────────────┐
│ matchPattern = 匹配**方式**的严格程度      │
│   EXACT     ← 最严格（完全相同）        │
│   CONTAINS  ← 中等（只要包含）          │
│   REGEX     ← 最灵活（按规则）          │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│ matchIndex = 找到多个匹配时**选哪个**    │
│   1 ← 第1个（默认）                    │
│   2 ← 第2个                            │
│   3 ← 第3个                            │
│   ... ← 以此类推                        │
└─────────────────────────────────────────┘

两个字段配合使用，可以精确控制批注位置！
```

---

## 🎓 学习资源

**想深入了解？**
→ 查看 `MATCH_PATTERN_AND_INDEX_EXPLAINED.md` 的完整版

**想看代码实现？**
→ 查看 `PreciseTextAnnotationLocator.java` 的实现细节

**想立即测试？**
→ 使用 `annotate_PRECISE.json` 和curl命令测试
