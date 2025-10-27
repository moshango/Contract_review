# CLAUDE.md

本文档用于指导 **Claude Code（claude.ai/code）** 在处理本项目代码时的开发与修改规则。

---

## 🧠 项目概述

本项目是一个基于 **Spring Boot 3.5.6 + Java 17** 的 **AI 合同审查助手（AI Contract Review Assistant）**，
用于对 Word 合同（`.docx` / `.doc`）文件进行解析与批注，结合大语言模型（LLM）实现合同智能审查与建议生成。

主要目标：

* 固化合同审查要点与标准；
* 统一评审逻辑，提高法务审查效率；
* 自动在合同原文中插入 AI 批注与修改建议；
* 支持锚点标记与回填，增强批注精度。

---

## ⚙️ 技术栈

| 组件   | 技术                       |
| ---- | ------------------------ |
| 编程语言 | Java 17                  |
| 框架   | Spring Boot 3.5.6        |
| 构建工具 | Maven                    |
| 文档处理 | Apache POI（XWPF）或 Docx4j |
| 接口协议 | RESTful HTTP（JSON）       |
| 安全策略 | 无鉴权（适配 Coze、n8n 等自动化调用）  |
| 文件支持 | 仅支持 `.docx` / `.doc`     |

---

## ⚠️ 限制与新增特性

**限制：**

* 当前版本 **不支持读取 Word 自动编号**（包括样式编号、段落编号、列表编号等）。
  仅解析标题与正文文本，不保留编号字段。

**新增特性：**

* `/parse` 与 `/annotate` 均支持 **锚点标记（anchor）与回填（cleanup）**：

  * `/parse` 可为每条条款生成稳定的锚点 ID 并返回；
  * `/annotate` 可使用锚点精确定位插入批注，并在完成后选择清理锚点。

---

## 🧩 模块说明

| 模块包          | 功能说明                                        |
| ------------ | ------------------------------------------- |
| `controller` | 定义 HTTP 接口：`/parse` 与 `/annotate`           |
| `service`    | 核心业务逻辑，包括文档解析、锚点生成与批注写入                     |
| `llm`        | LLM 接口层（可扩展以调用大模型）                          |
| `model`      | 数据模型，如 `Clause`、`ReviewIssue`、`ParseResult` |
| `util`       | Word 文件读写与锚点处理工具类                           |

---

## 📡 接口设计

### 1️⃣ `/parse` —— 合同解析接口

**功能：**
上传 `.docx` / `.doc` 文件，解析合同结构，生成条款列表与锚点信息。

**请求方式：**
`POST /parse`
**请求类型：** `multipart/form-data`（字段名：`file`）
**返回格式：** `application/json`

**支持参数：**

* `anchors`（可选，`none|generate|regenerate`，默认 `none`）

  * `generate`：为每个条款生成锚点（如 `anc-c2-9f1e`），并可写回文档；
  * `regenerate`：重新生成锚点；
  * `none`：不生成锚点。
* `returnMode`（可选，`json|file|both`，默认 `json`）

  * `file`/`both`：返回包含锚点标记的 `.docx`。

**响应示例：**

```json
{
  "filename": "合同示例.docx",
  "title": "技术合作协议",
  "clauses": [
    {
      "id": "c1",
      "heading": "第一条 合作范围",
      "text": "甲乙双方在以下范围内进行合作……",
      "anchorId": "anc-c1-4f21",
      "startParaIndex": 5,
      "endParaIndex": 9
    },
    {
      "id": "c2",
      "heading": "第二条 保密条款",
      "text": "双方应对涉及商业机密的资料予以保密……",
      "anchorId": "anc-c2-8f3a",
      "startParaIndex": 10,
      "endParaIndex": 16
    }
  ],
  "meta": {
    "wordCount": 5230,
    "paragraphCount": 140
  }
}
```

**说明：**

* 不解析任何编号；
* 若启用 `anchors=generate`，则在文档中写入锚点（通过书签或隐藏标记）；
* 锚点 ID 可在 `/annotate` 时使用以实现精确回填。

---

### 2️⃣ `/annotate` —— 合同批注接口

**功能：**
根据 LLM 或 Coze 的审查结果 JSON，在合同中插入批注。支持根据 `anchorId` 精确定位批注位置。

**请求方式：**
`POST /annotate`
**请求类型：** `multipart/form-data`

**参数说明：**

* `file`：原始 `.docx` 文件；
* `review`：LLM 审查结果 JSON；
* `anchorStrategy`（可选，`preferAnchor|anchorOnly|textFallback`，默认 `preferAnchor`）

  * `preferAnchor`：优先用 `anchorId`，若无则按 `clauseId`；
  * `anchorOnly`：仅按 `anchorId` 定位；
  * `textFallback`：允许按条款文本匹配；
* `cleanupAnchors`（可选，布尔，默认 `false`）

  * 若为 `true`，批注完成后清理锚点；
  * 若为 `false`，保留锚点供后续处理。

**返回格式：**
`application/vnd.openxmlformats-officedocument.wordprocessingml.document`

**审查 JSON 示例：**

```json
{
  "issues": [
    {
      "anchorId": "anc-c2-8f3a",
      "clauseId": "c2",
      "severity": "HIGH",
      "category": "保密条款",
      "finding": "未定义保密信息范围",
      "suggestion": "应增加保密信息的定义及披露条件。"
    }
  ]
}
```

**批注效果：**

```
[高风险] 保密条款问题：
未定义保密信息范围。
建议：应增加保密信息的定义及披露条件。
```

**示例命令：**

```bash
curl -X POST "http://localhost:8080/annotate?anchorStrategy=preferAnchor&cleanupAnchors=true" \
  -F "file=@/path/contract.docx" \
  -F "review=@/path/review.json" \
  -o annotated.docx
```

---

## 🧱 核心类说明

| 类名                             | 功能描述                          |
| ------------------------------ | ----------------------------- |
| `ContractController.java`      | 定义 `/parse` 与 `/annotate` 接口  |
| `ContractParseService.java`    | 解析文档、生成锚点与结构化条款               |
| `ContractAnnotateService.java` | 根据审查结果插入批注并支持锚点回填             |
| `DocxUtils.java`               | Word 操作工具：解析段落、创建书签、插入批注、清理锚点 |
| `Clause.java`                  | 合同条款模型，包含 `anchorId` 与段落索引    |
| `ReviewIssue.java`             | 审查问题模型，包含 `anchorId` 与建议信息    |
| `ParseResult.java`             | 解析结果封装                        |

---

## 🧠 LLM 集成逻辑（外部）

后端**不直接调用大模型**，由 Coze 或 n8n 负责审查：

1. `/parse` → 解析合同 → 返回结构化条款 + 锚点；
2. 前端将结果传给 LLM；
3. LLM 输出 JSON（含问题与建议）；
4. 前端调用 `/annotate` → 后端插入批注（可用锚点定位）；
5. 生成带批注的 `.docx` 文件。

---

## 🧰 开发规范与注意事项

* **不解析编号**：忽略 Word 自动编号，仅使用标题与正文；
* **锚点生成**：

  * 采用书签（bookmarkStart/bookmarkEnd）或隐藏占位；
  * 命名格式：`anc-<clauseId>-<shortHash>`；
  * 保证全局唯一；
* **回填策略**：

  * 若 `cleanupAnchors=true`，批注后移除锚点；
  * 否则保留用于后续增量审查；
* **接口**无需鉴权；
* **最大文件大小**：50MB；
* **日志**应记录请求 ID、时间、耗时、异常。

---

## ✅ 测试与验证流程

1. 上传 `.docx` 至 `/parse`；
2. 验证条款解析、锚点生成；
3. 将 JSON 传给 LLM，生成 `issues[]`；
4. 调用 `/annotate`；
5. 验证批注位置与锚点对应；
6. 如开启 `cleanupAnchors`，确认锚点清理。

---

## 🧪 示例命令

**生成锚点并返回带标记的 DOCX：**

```bash
curl -X POST "http://localhost:8080/parse?anchors=generate&returnMode=both" \
  -F "file=@/path/contract.docx" \
  -o parsed-with-anchors.docx
```

**带锚点精确批注：**

```bash
curl -X POST "http://localhost:8080/annotate?anchorStrategy=preferAnchor&cleanupAnchors=true" \
  -F "file=@/path/contract.docx" \
  -F "review=@/path/review.json" \
  -o annotated.docx
```

---

## 🚀 未来扩展方向

* 增加 `/review` 接口直接调用 LLM；
* 支持“修订模式”（Track Changes）；
* 多语言批注；
* 导出报告（PDF/Markdown）；
* 锚点同步增量更新机制。

---

## 👨‍💻 开发建议（Claude Code 指令）

* 严格遵循接口定义；
* 控制器层轻逻辑，主要处理入参校验；
* 所有公共方法需添加 Javadoc；
* 类命名遵循 Java 规范；
* 修改依赖时同步更新 `pom.xml`；
* 在 Service 层实现锚点写入与批注逻辑。

---

## ⚡ 快速上手

**运行：**

```bash
mvn spring-boot:run
```

**打包：**

```bash
mvn clean package
```

**默认端口：** `8080`
**接口示例：** `http://localhost:8080/parse`

---

✅ **Claude 提示：**
在实现代码时，严格遵循本文件规范；
特别注意锚点生成、定位与回填逻辑的一致性。
