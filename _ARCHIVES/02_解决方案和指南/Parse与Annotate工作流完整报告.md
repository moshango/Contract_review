# AI合同审查系统 - Parse与Annotate工作流完整报告

## 📋 文档概述

本报告详细说明AI合同审查系统中Parse（解析）与Annotate（批注）两个核心模块的：
- **输入输出JSON格式规范**
- **工作流程与数据流转**
- **锚点插入的完整逻辑**
- **所有关键变量的含义与用途**

---

## 第一部分：Parse（合同解析）模块

### 1.1 Parse模块概述

**功能：** 上传合同文件 → 解析文档结构 → 提取条款 → 生成锚点（可选）→ 返回结构化数据

**核心类位置：**
- 控制器：`src/main/java/.../controller/ContractController.java`
- 服务：`src/main/java/.../service/ContractParseService.java`
- 工具：`src/main/java/.../util/DocxUtils.java`
- 模型：`src/main/java/.../model/Clause.java`, `ParseResult.java`

---

### 1.2 Parse API端点

#### 请求信息

```
方法: POST
端点: /api/parse
内容类型: multipart/form-data
参数:
  - file (必填): Word文件 (.docx 或 .doc)
  - anchors (可选): 锚点模式
    * "none" - 不生成锚点（默认）
    * "generate" - 生成锚点
    * "regenerate" - 重新生成锚点
  - returnMode (可选): 返回模式
    * "json" - 仅返回JSON（默认）
    * "file" - 仅返回带锚点的.docx文件
    * "both" - 同时返回JSON和文件
```

---

### 1.3 Parse输入JSON格式

Parse处理的输入是**文件上传**，不涉及复杂JSON输入。

**示例请求（cURL）：**

```bash
# 1. 生成锚点，仅返回JSON
curl -X POST "http://localhost:8080/api/parse?anchors=generate&returnMode=json" \
  -F "file=@contract.docx"

# 2. 生成锚点，同时返回文件和JSON
curl -X POST "http://localhost:8080/api/parse?anchors=generate&returnMode=both" \
  -F "file=@contract.docx"

# 3. 不生成锚点，仅返回JSON
curl -X POST "http://localhost:8080/api/parse?anchors=none&returnMode=json" \
  -F "file=@contract.docx"
```

---

### 1.4 Parse输出JSON格式详解

#### 1.4.1 完整JSON响应示例

```json
{
  "filename": "技术合作协议.docx",
  "title": "技术合作协议",
  "clauses": [
    {
      "id": "c1",
      "heading": "第一条 合作范围",
      "text": "甲乙双方在以下范围内进行合作：\n1. 软件开发\n2. 技术咨询\n3. 数据处理",
      "tables": [],
      "anchorId": "anc-c1-4f21",
      "startParaIndex": 2,
      "endParaIndex": 5
    },
    {
      "id": "c2",
      "heading": "第二条 保密条款",
      "text": "双方应对涉及商业机密的资料予以保密，不向第三方披露。保密期限为本协议终止后5年。",
      "tables": [],
      "anchorId": "anc-c2-8f3a",
      "startParaIndex": 6,
      "endParaIndex": 8
    },
    {
      "id": "c3",
      "heading": "第三条 知识产权",
      "text": "本协议中涉及的知识产权归甲方所有。乙方不得在未获得甲方书面同意的情况下使用或转让相关知识产权。",
      "tables": [
        {
          "知识产权类型": ["软件代码", "技术文档"],
          "归属方": ["甲方", "甲方"],
          "使用权": ["乙方可使用", "乙方可使用"]
        }
      ],
      "anchorId": "anc-c3-9e7b",
      "startParaIndex": 9,
      "endParaIndex": 12
    }
  ],
  "meta": {
    "wordCount": 2150,
    "paragraphCount": 42
  }
}
```

#### 1.4.2 JSON字段详解

| 字段 | 类型 | 必填 | 含义说明 |
|------|------|------|--------|
| `filename` | String | ✅ | 原始上传的文件名 |
| `title` | String | ✅ | 从文档第一行自动提取的标题 |
| `clauses` | Array | ✅ | 条款数组，每个条款是一个Clause对象 |
| `meta` | Object | ✅ | 元数据，包含字数和段落数统计 |

#### 1.4.3 Clause（条款）对象详解

| 字段 | 类型 | 必填 | 含义说明 |
|------|------|------|--------|
| `id` | String | ✅ | **条款唯一标识符**<br/>格式：`c1`, `c2`, `c3` ...<br/>用途：后续批注时通过clauseId定位 |
| `heading` | String | ✅ | **条款标题**<br/>示例：`第一条 合作范围`<br/>来源：文档中的标题样式段落 |
| `text` | String | ✅ | **条款正文内容**<br/>包含该条款的所有段落文本<br/>去除了标题和表格（表格单独存储） |
| `tables` | Array | ✅ | **该条款包含的表格列表**<br/>每个表格是Map结构：key为列名，value为该列的行数据<br/>示例见下文 |
| `anchorId` | String | ⚠️ | **【关键】锚点ID**<br/>格式：`anc-{clauseId}-{shortHash}`<br/>示例：`anc-c1-4f21`<br/>仅当`anchors=generate/regenerate`时生成<br/>用途：后续批注时精确定位条款位置 |
| `startParaIndex` | Integer | ✅ | **条款在文档中的起始段落索引**<br/>从0开始计数<br/>包含标题所在的段落<br/>用途：精确定位条款在文档中的位置 |
| `endParaIndex` | Integer | ✅ | **条款在文档中的结束段落索引**<br/>从0开始计数<br/>包含条款最后一个段落<br/>用途：确定条款的文本范围 |

---

### 1.5 Parse工作流程图

```
┌─────────────────────────────────────────────────────────────┐
│                   上传Word文件                              │
│            (contract.docx 或 contract.doc)                 │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
          ┌──────────────────────────────┐
          │  判断文件格式                │
          │  .docx → XWPFDocument        │
          │  .doc  → HWPFDocument        │
          └──────────────┬───────────────┘
                         │
          ┌──────────────┴──────────────┐
          │                             │
          ▼                             ▼
    【.docx处理】                  【.doc处理】
    1. 加载DOCX文件              1. 加载DOC文件
    2. 提取标题                 2. 按段落解析
    3. 按标题分割条款           3. 识别条款边界
    4. 提取表格数据             4. 提取表格（基础）
    5. 生成段落索引             5. 计算统计信息
    6. 生成锚点（可选）
          │                             │
          └──────────────┬──────────────┘
                         │
                         ▼
            ┌────────────────────────────┐
            │  构建ParseResult对象       │
            │  ├─ filename               │
            │  ├─ title                  │
            │  ├─ clauses[]              │
            │  └─ meta{}                 │
            └────────────┬───────────────┘
                         │
          ┌──────────────┴──────────────┐
          │                             │
    returnMode=json?            returnMode=file?
          │                             │
          ▼                             ▼
    返回JSON响应              返回.docx文件
    application/json      application/vnd...
          │                             │
          └──────────────┬──────────────┘
                         │
                         ▼
                   ✅ 返回客户端
```

---

### 1.6 锚点生成逻辑详解

#### 1.6.1 锚点的含义

**锚点（Anchor）** 是在Word文档中插入的隐形标记，用于精确定位特定位置。

**格式规范：**
```
anc-{clauseId}-{shortHash}
│   │              │
│   │              └─ 短哈希值（取uuid前4位）
│   └─ 条款ID（如c1、c2等）
└─ 前缀标识
```

**示例：**
- `anc-c1-4f21` → 第1条的锚点
- `anc-c2-8f3a` → 第2条的锚点
- `anc-c15-a7c9` → 第15条的锚点

#### 1.6.2 锚点的生成过程

1. **计算条款唯一标识**
   ```java
   String clauseId = "c" + (clauseIndex + 1);  // c1, c2, c3...
   ```

2. **生成UUID并提取短哈希**
   ```java
   String uuid = UUID.randomUUID().toString();  // e.g., 4f217a8c-...
   String shortHash = uuid.substring(0, 4);     // 4f21
   ```

3. **组合锚点ID**
   ```java
   String anchorId = "anc-" + clauseId + "-" + shortHash;  // anc-c1-4f21
   ```

4. **在Word文档中插入锚点**
   ```
   使用Word书签（Bookmark）或隐藏占位符
   在条款标题所在位置插入
   保证全局唯一性
   ```

#### 1.6.3 锚点模式说明

| 模式 | 说明 | 使用场景 |
|------|------|--------|
| `none` | 不生成锚点 | 仅需要获取文本结构，不需要精确定位 |
| `generate` | 首次生成锚点 | 第一次解析文档，准备进行批注 |
| `regenerate` | 重新生成锚点 | 更新已有的锚点（用于修复损坏的锚点） |

---

## 第二部分：Annotate（合同批注）模块

### 2.1 Annotate模块概述

**功能：** 根据LLM审查结果 → 解析审查JSON → 在Word中精确定位 → 插入批注 → 返回带批注文件

**核心类位置：**
- 控制器：`src/main/java/.../controller/ContractController.java`
- 服务：`src/main/java/.../service/XmlContractAnnotateService.java` (推荐)
- 旧服务：`src/main/java/.../service/ContractAnnotateService.java` (已废弃)
- 工具：`src/main/java/.../util/WordXmlCommentProcessor.java`
- 模型：`src/main/java/.../model/ReviewIssue.java`

---

### 2.2 Annotate API端点

#### 请求信息

```
方法: POST
端点: /api/annotate
内容类型: multipart/form-data
参数:
  - file (必填): 原始Word文件 (.docx)
  - review (必填): 审查结果JSON字符串
  - anchorStrategy (可选): 锚点定位策略
    * "preferAnchor" (默认) - 优先使用anchorId，否则使用clauseId
    * "anchorOnly" - 仅使用anchorId
    * "textFallback" - 允许文本匹配（最后的兜底方案）
  - cleanupAnchors (可选): 是否清理锚点
    * true - 批注完成后清理锚点
    * false (默认) - 保留锚点供后续处理
```

---

### 2.3 Annotate输入JSON格式详解

#### 2.3.1 完整JSON示例

```json
{
  "issues": [
    {
      "clauseId": "c1",
      "anchorId": "anc-c1-4f21",
      "severity": "HIGH",
      "category": "合作范围",
      "finding": "合作范围描述不够明确，未明确列举具体项目",
      "suggestion": "建议明确列举具体的合作项目清单，并定义范围边界",
      "targetText": "软件开发、技术咨询、数据处理",
      "matchPattern": "EXACT",
      "matchIndex": 1
    },
    {
      "clauseId": "c2",
      "anchorId": "anc-c2-8f3a",
      "severity": "MEDIUM",
      "category": "保密条款",
      "finding": "保密期限过长，超过行业标准",
      "suggestion": "建议保密期限调整为3年，与市场惯例保持一致",
      "targetText": "保密期限为本协议终止后5年",
      "matchPattern": "EXACT",
      "matchIndex": 1
    },
    {
      "clauseId": "c3",
      "anchorId": "anc-c3-9e7b",
      "severity": "HIGH",
      "category": "知识产权",
      "finding": "乙方使用权限不明确，可能产生争议",
      "suggestion": "明确定义乙方对知识产权的具体使用范围和限制条件",
      "targetText": "乙方",
      "matchPattern": "CONTAINS",
      "matchIndex": 2
    },
    {
      "clauseId": "c4",
      "severity": "LOW",
      "category": "支付条款",
      "finding": "付款方式缺少具体细节",
      "suggestion": "补充银行账户、汇款路径等支付细节信息",
      "targetText": "按照约定时间支付费用",
      "matchPattern": "REGEX",
      "matchIndex": 1
    }
  ]
}
```

#### 2.3.2 ReviewIssue（审查问题）对象详解

| 字段 | 类型 | 必填 | 含义说明 |
|------|------|------|--------|
| `clauseId` | String | ✅ | **条款ID**<br/>来源：Parse模块返回的条款ID<br/>格式：`c1`, `c2`, `c3`...<br/>用途：定位问题所属的条款 |
| `anchorId` | String | ⚠️ | **【关键】锚点ID**<br/>来源：Parse模块返回的anchorId<br/>格式：`anc-c1-4f21`<br/>**强烈建议填写**（精确定位）<br/>优先级：通常优于clauseId使用 |
| `severity` | String | ✅ | **风险级别**<br/>可选值：`HIGH` \| `MEDIUM` \| `LOW`<br/>HIGH - 高风险，需立即修改<br/>MEDIUM - 中风险，建议修改<br/>LOW - 低风险，可选修改<br/>用途：批注前缀显示（🔴/🟡/🟢） |
| `category` | String | ✅ | **问题分类**<br/>示例：`合作范围` `保密条款` `知识产权` `支付条款`<br/>用途：批注中的问题分类标签<br/>建议与审查标准保持一致 |
| `finding` | String | ✅ | **发现的具体问题**<br/>示例：`合作范围描述不够明确，未明确列举具体项目`<br/>用途：批注内容的主体<br/>建议：详细、准确、可操作 |
| `suggestion` | String | ✅ | **修改建议**<br/>示例：`建议明确列举具体的合作项目清单，并定义范围边界`<br/>用途：批注内容的补充<br/>建议：提供具体、可行的修改方案 |
| `targetText` | String | ⚠️ | **【关键】要批注的精确文字**<br/>来源：从Parse结果中的条款文本精确复制<br/>格式：关键词形式（推荐），若整句有问题则退级到整句<br/>示例：`软件开发、技术咨询、数据处理`<br/>**强烈建议填写**（精确文字级批注）<br/>用途：在Word中精确定位批注位置 |
| `matchPattern` | String | ⚠️ | **文字匹配模式**<br/>可选值：<br/>- `EXACT` (默认) - 精确匹配整个targetText<br/>- `CONTAINS` - 包含匹配（targetText是大文本的子串）<br/>- `REGEX` - 正则表达式匹配<br/>用途：如何在条款中查找targetText |
| `matchIndex` | Integer | ⚠️ | **匹配序号**<br/>默认值：`1`<br/>说明：当targetText在条款中出现多次时，选择第N个<br/>示例：targetText="乙方"出现3次，matchIndex=2表示第2次出现<br/>用途：处理重复文字的精确定位 |

---

### 2.4 Annotate工作流程图

```
┌──────────────────────────────────────────┐
│  从LLM获取审查结果（JSON格式）         │
│  包含issues数组，每个issue包含问题信息 │
└────────────────┬───────────────────────┘
                 │
                 ▼
      ┌──────────────────────────┐
      │  解析ReviewRequest       │
      │  提取issues数组          │
      │  验证JSON格式正确性      │
      └────────────┬─────────────┘
                   │
    ┌──────────────┴──────────────┐
    │                             │
    ▼ 对每个ReviewIssue           │
┌─────────────────────────────┐  │
│ 1. 获取clauseId或anchorId   │◄─┘
│    - 若有anchorId，优先使用   │
│    - 否则，使用clauseId       │
└────────────┬────────────────┘
             │
             ▼
┌─────────────────────────────┐
│ 2. 确定批注位置             │
│    a) 若有targetText:      │
│       按matchPattern查找    │
│       (EXACT/CONTAINS/RE)   │
│    b) 否则:                │
│       按anchorId定位       │
│    c) 最终兜底:            │
│       按条款ID定位         │
└────────────┬────────────────┘
             │
             ▼
┌─────────────────────────────┐
│ 3. 构建批注内容             │
│ 格式:                       │
│ [severity] category:        │
│ finding                     │
│ 建议: suggestion            │
└────────────┬────────────────┘
             │
             ▼
┌─────────────────────────────┐
│ 4. 在Word中插入批注         │
│    使用XML修改Word文档      │
│    创建comment范围          │
│    绑定到具体位置           │
└────────────┬────────────────┘
             │
             ▼
┌─────────────────────────────┐
│ 5. 是否清理锚点?            │
│    若cleanupAnchors=true:   │
│      移除文档中的锚点标记   │
└────────────┬────────────────┘
             │
             ▼
        ✅ 返回批注后的.docx文件
```

---

## 第三部分：锚点插入的完整逻辑

### 3.1 锚点的核心作用

**锚点是什么？**
锚点是Word文档中的隐形标记，用于标记特定位置。系统使用锚点来：
1. **精确定位条款** - 每个条款有唯一的anchorId
2. **加速批注过程** - 直接跳转到条款位置，无需全文扫描
3. **处理文档更新** - 即使文档发生小的文本变化，锚点位置保持稳定

### 3.2 锚点的生成过程

#### 步骤1：解析文档并识别条款

```java
// Parse阶段
XWPFDocument doc = docxUtils.loadDocx(file.getInputStream());
List<Clause> clauses = docxUtils.extractClausesWithCorrectIndex(doc, generateAnchors=true);

// 结果：
// Clause #1: id="c1", heading="第一条...", text="...", anchorId="anc-c1-4f21"
// Clause #2: id="c2", heading="第二条...", text="...", anchorId="anc-c2-8f3a"
```

#### 步骤2：为每个条款生成唯一的anchorId

```java
// 在DocxUtils中
for (int i = 0; i < clauses.size(); i++) {
    Clause clause = clauses.get(i);
    String clauseId = "c" + (i + 1);  // c1, c2, c3...

    // 生成短UUID
    String uuid = UUID.randomUUID().toString();
    String shortHash = uuid.substring(0, 4);  // 取前4位

    // 组合anchorId
    String anchorId = "anc-" + clauseId + "-" + shortHash;  // anc-c1-4f21

    clause.setAnchorId(anchorId);
}
```

#### 步骤3：在Word文档中插入书签

```java
// 在条款标题位置插入Word书签
// 使用Word的Bookmark机制（书签）
XWPFParagraph titlePara = doc.getParagraphs().get(clause.getStartParaIndex());

// 创建书签范围
// 书签开始位置：标题段落
// 书签结束位置：条款最后段落
// 书签名称：anchorId（如 anc-c1-4f21）

CTBookmark bookmark = CTBookmark.Factory.newInstance();
bookmark.setName(anchorId);
// 将书签插入到Word XML结构中
```

#### 步骤4：返回包含anchorId的JSON（Parse返回结果）

```json
{
  "clauses": [
    {
      "id": "c1",
      "heading": "第一条 合作范围",
      "text": "...",
      "anchorId": "anc-c1-4f21",
      "startParaIndex": 2,
      "endParaIndex": 5
    }
  ]
}
```

### 3.3 锚点的使用过程（Annotate阶段）

#### 步骤1：LLM生成审查结果，包含anchorId

```json
{
  "issues": [
    {
      "clauseId": "c1",
      "anchorId": "anc-c1-4f21",  // 【关键】来自Parse返回结果
      "severity": "HIGH",
      "finding": "合作范围不明确",
      "suggestion": "需要明确具体项目"
    }
  ]
}
```

#### 步骤2：Annotate服务接收审查JSON

```java
// 在ContractController中
@PostMapping("/annotate")
public ResponseEntity<?> annotate(
    @RequestParam MultipartFile file,
    @RequestParam String review,
    @RequestParam(defaultValue = "preferAnchor") String anchorStrategy,
    @RequestParam(defaultValue = "false") boolean cleanupAnchors)
{
    byte[] result = xmlAnnotateService.annotateContractWithXml(
        file, review, anchorStrategy, cleanupAnchors
    );
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=annotated.docx")
        .body(result);
}
```

#### 步骤3：定位批注位置的三级策略

**优先级1：使用anchorId精确定位（推荐）**
```java
if (issue.getAnchorId() != null && !issue.getAnchorId().isEmpty()) {
    // 在Word中查找书签名为anchorId的位置
    CTBookmark bookmark = findBookmarkByName(doc, issue.getAnchorId());
    if (bookmark != null) {
        // 在书签位置插入批注 ✅ 最精确
        insertCommentAtBookmark(doc, bookmark, commentText);
        return;
    }
}
```

**优先级2：使用targetText精确文字定位**
```java
if (issue.getTargetText() != null && !issue.getTargetText().isEmpty()) {
    // 在条款文本中查找targetText
    String pattern = buildPattern(issue);  // 根据matchPattern构建正则
    int position = findTextPosition(clause.getText(), pattern, issue.getMatchIndex());
    if (position >= 0) {
        // 在文字位置插入批注 ✅ 精确文字级
        insertCommentAtTextPosition(doc, clause, position, commentText);
        return;
    }
}
```

**优先级3：使用clauseId条款级定位**
```java
if (issue.getClauseId() != null) {
    // 在条款位置插入批注（整个条款段落）
    // 使用startParaIndex和endParaIndex定位 ✅ 条款级
    insertCommentAtClause(doc, clause, commentText);
    return;
}
```

#### 步骤4：在Word中插入批注

**使用XML方式（推荐，精度高）：**

```java
// Word批注的XML结构
// 1. 在document.xml中创建comment range
<w:commentRangeStart w:id="0"/>
<w:r>
    <w:annotationRef/>
</w:r>
<w:commentRangeEnd w:id="0"/>

// 2. 在document.xml.rels中添加关系
<Relationship Id="rId1" Type="..." Target="comments.xml"/>

// 3. 在comments.xml中创建comment对象
<w:comment w:id="0" w:author="AI" w:date="2024-10-22T10:00:00Z">
    <w:p>
        <w:pPr>
            <w:pStyle w:val="CommentReference"/>
        </w:pPr>
        <w:r>
            <w:rStyle w:val="CommentReference"/>
            <w:annotationRef/>
        </w:r>
    </w:p>
    <w:p>
        <w:pPr>
            <w:pStyle w:val="CommentText"/>
        </w:pPr>
        <w:r>
            <w:t>[高风险] 保密条款: 保密期限过长...</w:t>
        </w:r>
    </w:p>
</w:comment>
```

#### 步骤5：清理锚点（可选）

```java
if (cleanupAnchors) {
    // 移除文档中的所有书签（锚点标记）
    removeAllBookmarks(doc);
    logger.info("✅ 已清理锚点");
}
```

---

## 第四部分：关键变量含义总结

### 4.1 Parse模块关键变量

| 变量名 | 类型 | 含义 | 示例 |
|--------|------|------|------|
| `file` | MultipartFile | 上传的Word文件 | contract.docx |
| `anchorMode` | String | 锚点生成模式 | "generate" |
| `returnMode` | String | 返回数据格式 | "json" 或 "file" 或 "both" |
| `filename` | String | 原始文件名 | "技术合作协议.docx" |
| `title` | String | 文档标题 | "技术合作协议" |
| `clauses` | List<Clause> | 解析得到的条款列表 | [Clause(c1), Clause(c2), ...] |
| `clauseId` | String | 条款唯一标识 | "c1", "c2" |
| `anchorId` | String | 锚点ID | "anc-c1-4f21" |
| `text` | String | 条款正文 | "甲乙双方..." |
| `startParaIndex` | Integer | 条款开始段落索引 | 2 |
| `endParaIndex` | Integer | 条款结束段落索引 | 5 |
| `meta` | Map | 文档元数据 | {wordCount: 2150, paragraphCount: 42} |

### 4.2 Annotate模块关键变量

| 变量名 | 类型 | 含义 | 示例 |
|--------|------|------|------|
| `file` | MultipartFile | 原始Word文件 | contract.docx |
| `review` | String | 审查结果JSON字符串 | "{\"issues\": [...]}" |
| `anchorStrategy` | String | 定位策略 | "preferAnchor" 或 "anchorOnly" 或 "textFallback" |
| `cleanupAnchors` | boolean | 是否清理锚点 | true 或 false |
| `issues` | List<ReviewIssue> | 审查问题列表 | [ReviewIssue(...), ...] |
| `clauseId` | String | 问题所属条款 | "c1" |
| `anchorId` | String | 锚点ID（用于定位） | "anc-c1-4f21" |
| `severity` | String | 风险级别 | "HIGH" 或 "MEDIUM" 或 "LOW" |
| `category` | String | 问题分类 | "保密条款" 或 "知识产权" |
| `finding` | String | 问题描述 | "保密期限过长..." |
| `suggestion` | String | 修改建议 | "建议期限改为3年..." |
| `targetText` | String | 要批注的文字 | "保密期限为5年" |
| `matchPattern` | String | 匹配模式 | "EXACT" 或 "CONTAINS" 或 "REGEX" |
| `matchIndex` | Integer | 匹配序号 | 1（第1个匹配）或 2（第2个匹配）... |

---

## 第五部分：完整工作流示例

### 5.1 从文件上传到批注完成的完整过程

```
【第一步】用户上传合同文件
┌─────────────────────────────────────────┐
│  POST /api/parse?anchors=generate       │
│  multipart/form-data                    │
│  ├─ file: contract.docx                 │
│  └─ anchors: "generate"                 │
└────────────────┬────────────────────────┘
                 │
                 ▼
        ┌──────────────────┐
        │ Parse处理        │
        │ 1. 加载docx      │
        │ 2. 提取条款      │
        │ 3. 生成anchorId  │
        │ 4. 插入书签      │
        └────────┬─────────┘
                 │
                 ▼
        ┌────────────────────────────────┐
        │ 返回JSON结果                  │
        │ {                              │
        │   "clauses": [                 │
        │     {                          │
        │       "id": "c1",              │
        │       "anchorId": "anc-c1-...",│
        │       "text": "..."            │
        │     }, ...                     │
        │   ]                            │
        │ }                              │
        └────────┬─────────────────────┘
                 │
【第二步】用户将条款发送给LLM审查（外部）
                 │
                 ▼
        ┌────────────────────────────────┐
        │ LLM分析条款并生成审查意见     │
        │ 返回JSON格式的issues数组       │
        └────────┬─────────────────────┘
                 │
【第三步】用户上传审查结果进行批注
                 │
                 ▼
        ┌────────────────────────────────┐
        │ POST /api/annotate             │
        │ multipart/form-data            │
        │ ├─ file: contract.docx         │
        │ ├─ review: "{JSON}"            │
        │ ├─ anchorStrategy: preferAnchor│
        │ └─ cleanupAnchors: true        │
        └────────┬─────────────────────┘
                 │
                 ▼
        ┌────────────────────────────────┐
        │ Annotate处理                  │
        │ 1. 解析review JSON             │
        │ 2. 对每个issue:                │
        │    a. 查找anchorId位置         │
        │    b. 或查找targetText位置     │
        │    c. 或按clauseId定位         │
        │ 3. 生成批注内容                │
        │ 4. 插入Word批注                │
        │ 5. 清理锚点（可选）            │
        └────────┬─────────────────────┘
                 │
                 ▼
        ┌────────────────────────────────┐
        │ 返回批注后的contract.docx      │
        │ - 保存所有AI意见               │
        │ - 用户可在Word中逐条查看      │
        └────────────────────────────────┘
```

### 5.2 数据流转关键节点

```
ParseResult (Parse输出)
    │
    ├─ filename: "contract.docx"
    ├─ title: "技术合作协议"
    ├─ clauses: [
    │   {
    │     id: "c1"
    │     heading: "第一条 合作范围"
    │     text: "甲乙双方在以下范围内..."
    │     anchorId: "anc-c1-4f21"  ◄─── 【关键】发送给LLM
    │     startParaIndex: 2
    │     endParaIndex: 5
    │   }
    │ ]
    └─ meta: {...}
         │
         ├─► LLM生成审查意见
         │
         ▼
ReviewIssue (Annotate输入)
    │
    ├─ clauseId: "c1"              ◄─── 来自Parse的id
    ├─ anchorId: "anc-c1-4f21"     ◄─── 来自Parse的anchorId
    ├─ severity: "HIGH"
    ├─ category: "合作范围"
    ├─ finding: "范围描述不明确"
    ├─ suggestion: "需要列举具体项目"
    ├─ targetText: "甲乙双方在以下范围内"  ◄─── 来自Parse的text
    ├─ matchPattern: "EXACT"
    └─ matchIndex: 1
         │
         ├─► 使用anchorId精确定位
         │   (优先级最高)
         │
         ▼
    【Word批注】
    ┌─────────────────────────┐
    │ 甲乙双方在以下范围内  ◄──┼─ 批注标记（红色，带数字）
    │ [高风险] 合作范围:    │
    │ 范围描述不明确        │
    │ 建议: 需要列举具体... │
    └─────────────────────────┘
```

---

## 第六部分：故障排查指南

### 6.1 常见问题

#### 问题1：批注位置不准确（落在错误的地方）

**原因分析：**
1. anchorId不匹配 - Parse生成的anchorId与Annotate接收的anchorId不一致
2. targetText不匹配 - 提供的targetText在条款中找不到
3. clauseId使用不当 - 条款ID对应错误

**排查步骤：**
```
1. 检查Parse返回的anchorId是否正确
2. 确保Annotate接收的anchorId与Parse返回的一致
3. 验证targetText是否从原文精确复制
4. 查看系统日志中的定位过程（日志会显示使用了哪种策略）
```

#### 问题2：anchorId为null或empty

**原因分析：**
1. Parse时未设置 `anchors=generate`
2. Parse时设置了 `anchors=none`
3. Word文件格式不支持书签

**排查步骤：**
```
1. 确保调用 /api/parse?anchors=generate
2. 检查返回的JSON中是否包含anchorId字段
3. 若为null，考虑使用targetText替代定位
```

#### 问题3：targetText找不到匹配

**原因分析：**
1. targetText与原文不完全一致（如多余空格、格式差异）
2. matchPattern设置不当
3. 文本在条款中出现多次，matchIndex设置错误

**排查步骤：**
```
1. 从Parse返回的text字段中精确复制targetText
2. 确认是否需要修改matchPattern（EXACT → CONTAINS → REGEX）
3. 若有多个匹配，调整matchIndex参数
```

---

## 附录A：API调用完整示例

### A.1 使用cURL的完整工作流

```bash
# 步骤1: 上传文件进行Parse
curl -X POST "http://localhost:8080/api/parse?anchors=generate&returnMode=json" \
  -F "file=@contract.docx" \
  -o parse_result.json

# 输出: parse_result.json
# {
#   "clauses": [
#     {
#       "id": "c1",
#       "anchorId": "anc-c1-4f21",
#       ...
#     }
#   ]
# }

# 步骤2: 将clauses发送给LLM进行审查（外部系统）
# （这一步在LLM系统中进行，不涉及API调用）

# 步骤3: 获得LLM的审查结果，格式如下：
cat > review_result.json << 'EOF'
{
  "issues": [
    {
      "clauseId": "c1",
      "anchorId": "anc-c1-4f21",
      "severity": "HIGH",
      "finding": "范围描述不明确",
      "suggestion": "需要列举具体项目"
    }
  ]
}
EOF

# 步骤4: 调用Annotate API添加批注
curl -X POST "http://localhost:8080/api/annotate?anchorStrategy=preferAnchor&cleanupAnchors=true" \
  -F "file=@contract.docx" \
  -F "review=@review_result.json" \
  -o annotated.docx

# 输出: annotated.docx（带批注的合同文件）
```

---

## 附录B：JSON Schema定义

### B.1 ParseResult Schema

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "ParseResult",
  "type": "object",
  "properties": {
    "filename": {"type": "string"},
    "title": {"type": "string"},
    "clauses": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "id": {"type": "string"},
          "heading": {"type": "string"},
          "text": {"type": "string"},
          "tables": {"type": "array"},
          "anchorId": {"type": "string"},
          "startParaIndex": {"type": "integer"},
          "endParaIndex": {"type": "integer"}
        },
        "required": ["id", "heading", "text", "anchorId", "startParaIndex", "endParaIndex"]
      }
    },
    "meta": {
      "type": "object",
      "properties": {
        "wordCount": {"type": "integer"},
        "paragraphCount": {"type": "integer"}
      }
    }
  },
  "required": ["filename", "title", "clauses", "meta"]
}
```

### B.2 ReviewRequest Schema

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "ReviewRequest",
  "type": "object",
  "properties": {
    "issues": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "clauseId": {"type": "string"},
          "anchorId": {"type": "string"},
          "severity": {"enum": ["HIGH", "MEDIUM", "LOW"]},
          "category": {"type": "string"},
          "finding": {"type": "string"},
          "suggestion": {"type": "string"},
          "targetText": {"type": "string"},
          "matchPattern": {"enum": ["EXACT", "CONTAINS", "REGEX"], "default": "EXACT"},
          "matchIndex": {"type": "integer", "default": 1}
        },
        "required": ["clauseId", "severity", "category", "finding", "suggestion"]
      }
    }
  },
  "required": ["issues"]
}
```

---

## 总结

本报告详细说明了AI合同审查系统的核心工作流：

1. **Parse模块** - 解析文档，生成结构化条款和锚点
2. **Annotate模块** - 根据审查结果在文档中插入批注
3. **锚点机制** - 提供精确定位的关键机制
4. **数据流转** - 从文件上传到最终带批注的文档

理解这些概念对于正确使用和集成本系统至关重要。

---

**文档生成时间**: 2025-10-22
**系统版本**: 2.1.0 (XML批注模式)
