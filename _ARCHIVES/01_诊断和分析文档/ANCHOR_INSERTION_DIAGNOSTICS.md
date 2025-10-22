# 🔍 锚点插入诊断指南

**修改时间**: 2025-10-21
**目的**: 诊断为什么锚点不被保存到文档字节数组
**编译状态**: ✅ SUCCESS

---

## 📋 修改内容

### 1. 增强 `insertAnchors()` 日志 (DocxUtils.java)

**位置**: Line 657-688

**新增诊断输出**:
```
【锚点插入】开始插入锚点: 文档段落数={}, 条款数={}
【锚点插入】为条款添加书签: clauseId={}, anchorId={}, paraIndex={}
【锚点插入】段落索引超出范围: ...
【锚点插入】完成: 成功插入={}个, 跳过={}
```

**作用**:
- 显示有多少个书签成功添加
- 显示是否有索引超出范围的情况
- 显示跳过的条款数量

---

### 2. 增强 `addBookmarkToParagraph()` 日志 (DocxUtils.java)

**位置**: Line 696-716

**新增诊断输出**:
```
【书签添加】段落当前书签数: {}
【书签添加】书签已添加: bookmarkName={}, bookmarkId={}, 段落现在有{}个书签
```

**作用**:
- 显示每个段落中的书签数量
- 验证书签是否真的被添加到 CTP 对象

---

### 3. 增强 `writeToBytes()` 日志 (DocxUtils.java)

**位置**: Line 1076-1105

**新增诊断输出**:
```
【文档保存】即将保存文档: 总段落数={}, 总书签数={}, 书签列表: {}
【文档保存】文档已保存到字节数组: 文档大小={} 字节
```

**关键点**:
- **总书签数 > 0** ✅ = 书签已在文档中，会被保存到字节数组
- **总书签数 = 0** ❌ = 书签未被添加到文档，需要检查 insertAnchors()

---

### 4. 增强 `parseContractWithDocument()` 日志 (ContractParseService.java)

**位置**: Line 133-169

**新增诊断输出**:
```
【工作流】开始插入锚点到文档中
【工作流】锚点插入完成
【工作流】开始将修改的文档保存为字节数组
【工作流】文档字节数组生成完成: 大小={} 字节
【工作流】未启用锚点生成，documentBytes=null
```

**作用**:
- 追踪完整的 Parse 工作流
- 显示最终生成的文档大小

---

## 🧪 如何使用这些诊断信息

### 场景 1: 锚点未被插入

**日志特征**:
```
【锚点插入】开始插入锚点: 文档段落数=120, 条款数=8
【锚点插入】完成: 成功插入=0个, 跳过=8
【文档保存】即将保存文档: 总段落数=120, 总书签数=0
```

**问题**: 所有条款都被跳过
**可能原因**:
1. `clause.getAnchorId()` 返回 null
2. `clause.getStartParaIndex()` 返回 null
3. 段落索引超出范围

**解决步骤**:
- 检查 `extractClausesWithCorrectIndex()` 是否正确生成了 anchorId 和 startParaIndex
- 确认条款数量正确

---

### 场景 2: 锚点被插入但未持久化

**日志特征**:
```
【锚点插入】开始插入锚点: 文档段落数=120, 条款数=8
【锚点插入】为条款添加书签: clauseId=c1, anchorId=anc-c1-xxxx, paraIndex=5
【锚点插入】完成: 成功插入=8个, 跳过=0
【文档保存】即将保存文档: 总段落数=120, 总书签数=0  ❌
```

**问题**: 书签被添加到了内存中的 CTP 对象，但保存时丢失
**可能原因**:
1. Apache POI 的 XWPFDocument 对象未正确序列化书签
2. 书签被添加到了错误的 CTP 对象

**解决步骤**:
- 需要检查 Apache POI 的版本是否支持书签序列化
- 可能需要更换书签实现方式

---

### 场景 3: 锚点被正确插入和保存 ✅

**日志特征**:
```
【锚点插入】开始插入锚点: 文档段落数=120, 条款数=8
【锚点插入】为条款添加书签: clauseId=c1, anchorId=anc-c1-xxxx, paraIndex=5
【锚点插入】为条款添加书签: clauseId=c2, anchorId=anc-c2-xxxx, paraIndex=12
...
【锚点插入】完成: 成功插入=8个, 跳过=0
【文档保存】即将保存文档: 总段落数=120, 总书签数=8, 书签列表: [Para5: anc-c1-xxxx] [Para12: anc-c2-xxxx] ...
【文档保存】文档已保存到字节数组: 文档大小=150000 字节
```

**结果**: ✅ 锚点会被保存到文档字节数组中，Annotate 阶段可以找到它们

---

## 🚀 测试步骤

### 步骤 1: 启动服务

```bash
cd D:\工作\合同审查系统开发\spring boot\Contract_review
mvn clean package -DskipTests
java -jar target/Contract_review-0.0.1-SNAPSHOT.jar
```

### 步骤 2: 上传文件并观察日志

```bash
curl -X POST "http://localhost:8080/chatgpt/generate-prompt" \
  -F "file=@path/to/contract.docx" \
  -F "anchors=generate" \
  -o response.json
```

### 步骤 3: 查看关键日志

在服务输出中搜索以下日志：
- `【锚点插入】开始插入锚点`
- `【文档保存】即将保存文档`

**关键指标**:
- `总书签数` 应该 > 0
- `成功插入` 应该等于条款数

---

## 📊 预期行为

### 修复前 (当前问题)

日志显示:
```
【文档保存】即将保存文档: 总段落数=120, 总书签数=0
```

结果:
- Annotate 阶段找不到任何锚点
- "文档中总书签数=1" (仅 _GoBack)

### 修复后 (预期)

日志应该显示:
```
【文档保存】即将保存文档: 总段落数=120, 总书签数=8, 书签列表: [Para5: anc-c1-xxxx] [Para12: anc-c2-xxxx] ...
```

结果:
- Annotate 阶段能找到所有锚点
- "文档中总书签数=8"（8个条款 + 1个 _GoBack = 9个）

---

## 🔧 下一步行动

1. **立即执行**: 部署新代码并运行测试，查看诊断日志
2. **根据日志判断**:
   - 如果 `总书签数 > 0`，说明锚点已被保存，问题在 Annotate 阶段的查找逻辑
   - 如果 `总书签数 = 0`，说明锚点未被保存，需要进一步诊断原因

3. **如果锚点仍未被保存**:
   - 考虑使用替代方案（如 XML 直接操作而非 POI）
   - 或者检查 Apache POI 版本兼容性

---

## 📌 关键代码位置

| 文件 | 行号 | 功能 |
|------|------|------|
| DocxUtils.java | 657-688 | insertAnchors() - 锚点插入逻辑 |
| DocxUtils.java | 696-716 | addBookmarkToParagraph() - 书签添加 |
| DocxUtils.java | 1076-1105 | writeToBytes() - 文档序列化 |
| ContractParseService.java | 133-169 | parseContractWithDocument() - 完整工作流 |

---

**编译状态**: ✅ BUILD SUCCESS
**下一步**: 部署并观察诊断日志，根据输出确定问题根源
