# 🧪 锚点插入问题诊断步骤

**更新时间**: 2025-10-21
**目的**: 帮助用户通过日志诊断为什么锚点未被保存到文档

---

## 📌 关键信息

我已经在代码中添加了大量诊断日志。现在当您调用 `/generate-prompt` 端点时，系统会输出详细的日志，帮助我们确定锚点未被保存的具体原因。

---

## 🚀 执行诊断的步骤

### 步骤 1: 编译并启动服务

```bash
cd D:\工作\合同审查系统开发\spring boot\Contract_review

# 编译
mvn clean compile

# 打包
mvn clean package -DskipTests

# 运行
java -jar target/Contract_review-0.0.1-SNAPSHOT.jar
```

等待服务启动成功，看到类似输出：
```
Started Contract_review in X.XXX seconds
Tomcat started on port(s): 8080 (http)
```

### 步骤 2: 上传测试文件

用任何 `.docx` 文件测试：

```bash
curl -X POST "http://localhost:8080/chatgpt/generate-prompt" \
  -F "file=@你的合同.docx" \
  -F "anchors=generate" \
  > response.json 2>&1
```

### 步骤 3: 查看完整日志输出

这很重要！在终端中查看 Java 进程的标准输出，寻找以下日志行：

---

## 📊 诊断日志解读

### 第 1 层: 条款提取 - 检查是否正确解析了条款

**日志示例**:
```
【修复版本】开始提取条款（使用正确的真实段落索引）
总段落数=120, 生成锚点=true
✓ 发现条款 [clauseId=c1] 在真实段落索引 [5]，标题: '第一条 合作范围'
  生成锚点: anchorId=anc-c1-a1b2c3d4
✓ 发现条款 [clauseId=c2] 在真实段落索引 [12]，标题: '第二条 保密条款'
  生成锚点: anchorId=anc-c2-b2c3d4e5
【修复版本】条款提取完成: 共找到 2 个条款
```

**诊断**:
- ✅ 如果看到 `共找到 X 个条款` 且 X > 0，说明条款提取成功
- ❌ 如果看到 `共找到 0 个条款`，说明文档中没有识别出任何条款

**常见原因**:
- 文档中的条款标题格式与代码中的 `isClauseHeading()` 不匹配

---

### 第 2 层: 锚点生成 - 检查是否生成了锚点 ID

**日志示例**:
```
生成锚点: anchorId=anc-c1-a1b2c3d4
```

**诊断**:
- ✅ 如果每个条款都有 `anchorId=anc-cX-...` 格式，说明锚点生成成功
- ❌ 如果看到 `anchorId=null`，说明 `generateAnchorId()` 方法返回了 null

---

### 第 3 层: 工作流检查 - 确认工作流执行

**日志示例**:
```
【工作流】开始插入锚点到文档中
【工作流】锚点插入完成
【工作流】开始将修改的文档保存为字节数组
【工作流】文档字节数组生成完成: 大小=150000 字节
```

**诊断**:
- ✅ 如果看到所有这些日志，说明工作流正常执行
- ❌ 如果缺少某些日志，说明在某处出现了异常

---

### 第 4 层: 锚点插入详情 - **【最关键】** 检查每个条款的锚点信息

**日志示例**:
```
【锚点插入】开始插入锚点: 文档段落数=120, 条款数=2
【锚点插入】条款[0]: id=c1, anchorId=anc-c1-a1b2c3d4, startParaIndex=5, heading='第一条 合作范围'
【锚点插入】条款[1]: id=c2, anchorId=anc-c2-b2c3d4e5, startParaIndex=12, heading='第二条 保密条款'
【锚点插入】为条款添加书签: clauseId=c1, anchorId=anc-c1-a1b2c3d4, paraIndex=5
【锚点插入】为条款添加书签: clauseId=c2, anchorId=anc-c2-b2c3d4e5, paraIndex=12
【锚点插入】完成: 成功插入=2个, 跳过=0
```

**关键诊断**:
- ✅ **成功插入=2个, 跳过=0** - 所有条款的锚点都被成功添加到内存文档
- ❌ **成功插入=0个, 跳过=2** - 所有条款都被跳过了（所有条款的 anchorId 或 startParaIndex 为 null）
- ❌ **段落索引超出范围** - startParaIndex 值 >= 文档段落数

---

### 第 5 层: 书签添加 - 检查单个书签是否被添加到了 CTP 对象

**日志示例** (DEBUG 级别，需要启用 DEBUG 日志):
```
【书签添加】段落当前书签数: 0
【书签添加】书签已添加: bookmarkName=anc-c1-a1b2c3d4, bookmarkId=12345, 段落现在有1个书签
【书签添加】段落当前书签数: 0
【书签添加】书签已添加: bookmarkName=anc-c2-b2c3d4e5, bookmarkId=12346, 段落现在有1个书签
```

**诊断**:
- ✅ 如果 `段落现在有X个书签` 数量不断增加，说明书签被成功添加到 CTP 对象
- ❌ 如果始终显示 `段落现在有0个书签`，说明 `addNewBookmarkStart()` 没有成功工作

---

### 第 6 层: 文档保存 - **【最重要】** 检查文档序列化时是否包含书签

**日志示例**:
```
【文档保存】即将保存文档: 总段落数=120, 总书签数=2, 书签列表: [Para5: anc-c1-a1b2c3d4] [Para12: anc-c2-b2c3d4e5]
【文档保存】文档已保存到字节数组: 文档大小=150000 字节
```

**关键诊断**:
- ✅ **总书签数=2** 且书签列表中有多个书签 → 书签已被正确序列化到字节数组 ✅
- ❌ **总书签数=0** → **这是关键问题！** 书签已被添加到内存对象，但在序列化时丢失了
- ⚠️ **书签数不等于条款数** → 某些条款的书签在保存时没有被正确序列化

---

## 🔍 问题诊断流程

```
用户上传文件
    ↓
【修复版本】条款提取完成: 共找到 X 个条款?
    ├─ YES (X > 0) → 进入第 2 层检查
    │
    └─ NO (X = 0) → 问题: 文档格式不匹配或无条款
                    解决: 调整 isClauseHeading() 方法

生成锚点: anchorId=anc-cX-...?
    ├─ YES → 进入第 3 层检查
    │
    └─ NO → 问题: 锚点生成失败
            解决: 检查 generateAnchorId() 方法

【工作流】完整执行?
    ├─ YES → 进入第 4 层检查
    │
    └─ NO → 问题: 工作流中途出现异常
            解决: 查看异常栈跟踪

【锚点插入】成功插入=条款数 且 跳过=0?
    ├─ YES → 进入第 6 层检查
    │
    └─ NO → 问题: 条款信息不完整或索引超出范围
            解决: 检查 extractClausesWithCorrectIndex() 方法

【文档保存】总书签数 > 0?
    ├─ YES ✅ → 锚点已成功保存！
    │          Annotate 阶段应该能找到锚点
    │          如果找不到，问题在 Annotate 端
    │
    └─ NO ❌ → 关键问题：书签未被序列化
                可能原因：
                - Apache POI 版本问题
                - CTP 对象的书签未被正确序列化
                - 需要替代方案
```

---

## 📋 汇总信息表单

在诊断时，请记下以下信息并共享给我：

```
【诊断信息表】

1. 条款提取结果:
   共找到的条款数: ___

2. 锚点生成结果:
   第一个条款的 anchorId: ___

3. 锚点插入结果:
   成功插入: ___ 个
   跳过: ___ 个

4. 文档保存结果:
   总书签数: ___
   书签列表: ___
   文档大小: ___ 字节

5. 完整的日志输出:
   （复制粘贴所有 【锚点插入】、【文档保存】 相关的日志行）
```

---

## 🎯 基于诊断结果的可能修复

### 情况 A: 书签数 > 0 ✅
**问题不在 Parse 阶段**
- 锚点已成功保存到文档
- 检查 Annotate 阶段的锚点查找逻辑
- 问题可能在 `WordXmlCommentProcessor.findParagraphByAnchor()` 方法

### 情况 B: 书签数 = 0，但成功插入 > 0 ❌
**关键问题：序列化失败**
- 书签被添加到内存，但未被序列化
- 可能原因：
  1. Apache POI 的 `doc.write()` 方法未正确处理书签
  2. 需要检查 POI 版本和兼容性
  3. 可能需要使用 XML 直接操作而非 POI

### 情况 C: 成功插入 = 0 ❌
**条款信息不完整**
- 所有条款的 anchorId 或 startParaIndex 为 null
- 检查 `extractClausesWithCorrectIndex()` 是否正确设置了这些字段

---

## 🚀 启用 DEBUG 日志 (可选)

如果需要更详细的日志，编辑 `application.properties`:

```properties
logging.level.com.example.Contract_review.util.DocxUtils=DEBUG
logging.level.com.example.Contract_review.service.ContractParseService=DEBUG
```

然后重启服务。

---

## 📞 我需要的信息

请运行诊断后，提供：
1. 完整的 【锚点插入】 日志输出
2. 完整的 【文档保存】 日志输出
3. 【诊断信息表】中的各项值

这样我可以精确定位问题并提供修复方案。

