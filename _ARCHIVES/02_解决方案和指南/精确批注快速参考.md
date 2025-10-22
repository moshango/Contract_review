# 精确批注快速参考卡

**目标**: 实现Word右侧批注（关联到具体文字，而非文尾）

---

## 🎯 3步快速开始

### 第1步：使用提供的JSON
```
文件: annotate_PRECISE.json
特点: 所有9个issue都包含精确的targetText
```

### 第2步：调用API
```bash
curl -X POST "http://localhost:8080/annotate?anchorStrategy=preferAnchor" \
  -F "file=@parsed-测试合同_综合测试版.docx" \
  -F "review=@annotate_PRECISE.json" \
  -o result_precise.docx
```

### 第3步：打开结果
```
用Word打开 result_precise.docx
检查右侧批注是否关联到具体文字
```

---

## 📋 JSON关键字段

```json
{
  "anchorId": "anc-c1-4f21",           // 锚点ID（定位段落）
  "targetText": "30天内",               // ✅ 关键：要批注的具体文字
  "matchPattern": "EXACT",             // 匹配模式: EXACT|CONTAINS|REGEX
  "matchIndex": 1,                      // 多个匹配时选择第几个
  "finding": "时限不清晰",
  "suggestion": "建议改为15天",
  "severity": "MEDIUM",
  "category": "赔偿责任"
}
```

---

## 🎨 匹配模式选择

| 模式 | 用途 | 例子 |
|------|------|------|
| **EXACT** | 完整短语，唯一 | `"五、保密与数据安全"` |
| **CONTAINS** | 关键词，可能多次 | `"全部经济损失"` |
| **REGEX** | 复杂模式 | `"\d{1,3}天内"` |

---

## ⚡ 常见场景

### 场景1：精确短语唯一
```json
{
  "targetText": "五、保密与数据安全",
  "matchPattern": "EXACT"
}
```

### 场景2：关键词可能多次
```json
{
  "targetText": "全部经济损失",
  "matchPattern": "CONTAINS",
  "matchIndex": 1  // 第1个匹配
}
```

### 场景3：时间模式
```json
{
  "targetText": "30天",
  "matchPattern": "CONTAINS"
}
```

### 场景4：正则表达式
```json
{
  "targetText": "\\d{1,2}[个天]",
  "matchPattern": "REGEX"
}
```

---

## ✅ 效果对比

### ❌ 无targetText（段落级别）
```
正文: 甲方应在损害事实发生后30天内承担赔偿责任。
批注: 整段都可能被关联
```

### ✅ 有targetText（精确位置）
```
正文: 甲方应在损害事实发生后30天内承担赔偿责任。
                        ↑ targetText="30天"
批注: 仅这个位置关联批注
```

---

## 🔧 如果文字不匹配

**问题**：
```
WARN - 精确文字匹配失败，降级到段落级别
```

**解决**：
1. 检查targetText是否真的在文档中
2. 尝试改用CONTAINS模式
3. 缩短targetText只保留核心关键词
4. 查看日志判断文字位置

```json
// ❌ 不行（太长或格式不对）
{"targetText": "甲方应在损害事实发生后30天内承担赔偿责任。"}

// ✅ 改为
{"targetText": "30天", "matchPattern": "CONTAINS"}
```

---

## 📊 已提供的文件

| 文件 | 说明 |
|------|------|
| `annotate_PRECISE.json` | 9个issue的精确批注JSON |
| `annotate.json` | 原始JSON（可选） |
| `PRECISE_ANNOTATION_GUIDE.md` | 详细使用指南 |

---

## 🚀 完整命令

```bash
# 精确批注
curl -X POST "http://localhost:8080/annotate?anchorStrategy=preferAnchor&cleanupAnchors=false" \
  -F "file=@parsed-测试合同_综合测试版.docx" \
  -F "review=@annotate_PRECISE.json" \
  -o result_precise.docx

# 精确批注 + 清理锚点
curl -X POST "http://localhost:8080/annotate?anchorStrategy=preferAnchor&cleanupAnchors=true" \
  -F "file=@parsed-测试合同_综合测试版.docx" \
  -F "review=@annotate_PRECISE.json" \
  -o result_precise_clean.docx
```

---

## 💡 核心要点

✅ **所有关键功能已实现**：
- 锚点定位（通过anchorId）
- 精确文字匹配（通过targetText）
- 智能回退（匹配失败自动降级）

✅ **系统特点**：
- 支持3种匹配模式
- 自动处理多个匹配
- 智能降级保证稳定性

✅ **使用很简单**：
- 只需提供targetText
- 系统自动进行精确定位
- 文字自动关联批注

---

## 📞 快速Q&A

**Q: 需要修改代码吗？**
A: 不需要！系统已完全支持精确批注。

**Q: 一定要有targetText吗？**
A: 不一定。没有targetText时会使用段落级别批注。

**Q: 文字找不到会怎样？**
A: 自动降级到段落级别批注，不会出错。

**Q: 如何验证精确定位是否成功？**
A: 在Word中打开结果，看批注是否关联到具体文字。

---

**快速开始**: 立即使用 `annotate_PRECISE.json` 和上面的curl命令

**详细指南**: 查看 `PRECISE_ANNOTATION_GUIDE.md`
