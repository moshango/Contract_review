# AI 合同审查助手 (AI Contract Review Assistant)

基于 **Spring Boot 3.5.6 + Java 17** 的智能合同审查系统,支持对 Word 合同文件进行自动解析与批注。

## 功能特性

- 支持 `.docx` 和 `.doc` 格式合同文件
- 自动解析合同条款结构
- 生成锚点以精确定位批注位置
- 支持AI审查结果自动批注
- 无鉴权设计,适配 Coze、n8n 等自动化工具
- **Web可视化界面** - 支持拖拽上传和在线操作

## 技术栈

- Java 17
- Spring Boot 3.5.6
- Apache POI 5.2.5
- Maven
- Lombok
- HTML5 + CSS3 + Vanilla JavaScript

## 快速开始

### 1. 编译项目

```bash
mvn clean package
```

### 2. 运行项目

```bash
mvn spring-boot:run
```

或者运行打包后的 jar:

```bash
java -jar target/Contract_review-0.0.1-SNAPSHOT.jar
```

默认端口: **8080**

### 3. 访问Web界面

打开浏览器访问: **http://localhost:8080**

您将看到一个漂亮的 Web 界面,支持:
- 📄 **合同解析** - 上传合同文件,生成结构化JSON
- ✍️ **合同批注** - 根据AI审查结果添加批注
- 🎨 **拖拽上传** - 支持拖拽文件到页面
- 📥 **自动下载** - 处理完成自动下载结果文件

### 4. 健康检查 (API)

```bash
curl http://localhost:8080/health
```

## API 接口

### 📄 `/parse` - 合同解析接口

解析合同文档,提取条款结构并可选生成锚点。

**请求方式:** `POST`

**参数:**
- `file` (required): 上传的合同文件 (.docx / .doc)
- `anchors` (optional): 锚点模式
  - `none` - 不生成锚点 (默认)
  - `generate` - 生成锚点
  - `regenerate` - 重新生成锚点
- `returnMode` (optional): 返回模式
  - `json` - 仅返回JSON (默认)
  - `file` - 仅返回带锚点的文档
  - `both` - 返回JSON和文档

**示例:**

```bash
# 解析合同并返回JSON
curl -X POST "http://localhost:8080/parse" \
  -F "file=@contract.docx"

# 生成锚点并返回带锚点的文档
curl -X POST "http://localhost:8080/parse?anchors=generate&returnMode=file" \
  -F "file=@contract.docx" \
  -o parsed-with-anchors.docx
```

**响应示例 (JSON):**

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
    }
  ],
  "meta": {
    "wordCount": 5230,
    "paragraphCount": 140
  }
}
```

### ✍️ `/annotate` - 合同批注接口

根据审查结果在合同中插入批注。

**请求方式:** `POST`

**参数:**
- `file` (required): 原始合同文件 (.docx)
- `review` (required): 审查结果JSON字符串
- `anchorStrategy` (optional): 锚点定位策略
  - `preferAnchor` - 优先使用锚点,否则条款ID (默认)
  - `anchorOnly` - 仅使用锚点定位
  - `textFallback` - 允许文本匹配fallback
- `cleanupAnchors` (optional): 是否清理锚点 (默认: false)

**审查结果JSON格式:**

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

**示例:**

```bash
# 创建审查结果JSON文件
cat > review.json << 'EOF'
{
  "issues": [
    {
      "anchorId": "anc-c1-4f21",
      "clauseId": "c1",
      "severity": "HIGH",
      "category": "合作范围",
      "finding": "合作范围描述不够明确",
      "suggestion": "建议明确列举具体的合作项目和范围边界"
    }
  ]
}
EOF

# 添加批注并清理锚点
curl -X POST "http://localhost:8080/annotate?anchorStrategy=preferAnchor&cleanupAnchors=true" \
  -F "file=@contract.docx" \
  -F "review=$(cat review.json)" \
  -o annotated-contract.docx
```

**批注效果:**

文档中会插入红色斜体文本:
```
【AI审查批注】[高风险] 合作范围问题：
合作范围描述不够明确。
建议：建议明确列举具体的合作项目和范围边界
```

## 使用流程

### 典型工作流 (配合 LLM)

1. **上传并解析合同**
   ```bash
   curl -X POST "http://localhost:8080/parse?anchors=generate" \
     -F "file=@contract.docx" > parse-result.json
   ```

2. **将解析结果发送给 LLM (Coze/ChatGPT等)**
   - LLM 分析条款并生成审查意见
   - 返回包含 `issues` 的 JSON

3. **使用审查结果批注合同**
   ```bash
   curl -X POST "http://localhost:8080/annotate?cleanupAnchors=true" \
     -F "file=@contract.docx" \
     -F "review=@llm-review.json" \
     -o final-annotated.docx
   ```

4. **查看带批注的合同**
   - 在 Word 中打开 `final-annotated.docx`
   - 查看AI插入的审查批注

## 配置说明

### application.properties

```properties
# 服务器端口
server.port=8080

# 文件上传限制
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB

# 日志级别
logging.level.com.example.Contract_review=INFO
```

## 限制与说明

- 当前版本**不支持读取 Word 自动编号**
- 仅解析标题与正文文本
- 批注采用红色段落形式,非真实Word批注功能
- 文件大小限制: 50MB
- 无身份验证机制

## 项目结构

```
src/main/java/com/example/Contract_review/
├── controller/
│   └── ContractController.java         # HTTP接口
├── service/
│   ├── ContractParseService.java       # 解析服务
│   └── ContractAnnotateService.java    # 批注服务
├── model/
│   ├── Clause.java                     # 条款模型
│   ├── ReviewIssue.java               # 审查问题模型
│   ├── ParseResult.java               # 解析结果模型
│   └── ReviewRequest.java             # 审查请求模型
└── util/
    └── DocxUtils.java                 # Word文档工具类
```

## 开发指南

详细开发规范请参考项目根目录的 `CLAUDE.md` 文件。

## License

本项目遵循 MIT 许可证。
