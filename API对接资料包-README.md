# 一键审查API对接资料包

## 📦 资料包内容

本资料包包含一键审查功能前后端对接的**完整文档、代码示例、测试工具**。

---

## 📚 文档清单

### 1️⃣ **快速开始**（5分钟上手）

📄 **前端对接超简洁清单.md**
- ✅ 60秒理解核心流程
- ✅ 2个API接口说明
- ✅ 20行代码极简实现
- ✅ 快速复制使用

**适合人群**: 需要快速理解的前端工程师

---

### 2️⃣ **开发Prompt**（15分钟阅读）

📄 **一键审查API对接Prompt.md**
- ✅ 完整的开发任务描述
- ✅ API调用示例代码
- ✅ TypeScript类型定义
- ✅ Vue3 Composition API实现
- ✅ 常见坑点提醒
- ✅ UI设计建议

**适合人群**: 负责实现的前端工程师

---

### 3️⃣ **完整技术文档**（按需查阅）

📄 **一键审查前后端对接文档.md**
- ✅ 详细的流程图
- ✅ 完整的API规格说明
- ✅ 请求/响应示例
- ✅ 错误处理方案
- ✅ Pinia状态管理示例
- ✅ Mock数据示例

**适合人群**: 需要深入了解的技术负责人

---

### 4️⃣ **项目任务书**（项目管理）

📄 **前端对接开发任务书.md**
- ✅ 任务目标和范围
- ✅ 技术接口定义
- ✅ 实现要求
- ✅ 测试用例
- ✅ 验收标准
- ✅ 时间安排

**适合人群**: 项目经理、技术负责人

---

## 🛠️ 工具清单

### 5️⃣ **Postman测试集合**

📄 **一键审查API.postman_collection.json**
- ✅ 6个完整测试用例
- ✅ 导入即用
- ✅ 包含成功/失败示例
- ✅ 预设环境变量

**使用方法**:
1. 打开Postman
2. Import → 选择此文件
3. 选择环境变量（localhost:8080）
4. 点击Send测试

---

### 6️⃣ **API快速测试脚本**

📄 **测试一键审查API.bat**
- ✅ 一键测试所有API
- ✅ 自动检查服务状态
- ✅ 生成测试报告
- ✅ Windows批处理脚本

**使用方法**:
```bash
# 确保后端已启动
cd Contract_review
双击运行: 测试一键审查API.bat
```

---

## 🚀 快速开始指南

### 方式1：给前端工程师（推荐）

**Step 1**: 阅读 `前端对接超简洁清单.md`（5分钟）  
**Step 2**: 复制 `一键审查API对接Prompt.md` 中的代码  
**Step 3**: 开始开发（2小时）  
**Step 4**: 使用 `测试一键审查API.bat` 验证  

---

### 方式2：自己快速验证

**Step 1**: 导入 `一键审查API.postman_collection.json` 到Postman  
**Step 2**: 准备一个测试.docx文件  
**Step 3**: 依次测试6个API  
**Step 4**: 查看响应是否正确  

---

### 方式3：命令行快速测试

**Step 1**: 确保后端启动（http://localhost:8080）  
**Step 2**: 运行测试脚本  
```bash
cd Contract_review
测试一键审查API.bat
```
**Step 3**: 查看生成的 `parse_result.json` 和 `review_result.json`

---

## 📊 核心API速查表

### API #1: 解析合同

```http
POST /api/parse
Content-Type: multipart/form-data

参数:
  - file: [文件]
  - anchors: "generate"
  - returnMode: "json"

响应:
  {
    partyA: "甲方公司",
    partyB: "乙方公司",
    clauses: [...]
  }
```

### API #2: 一键审查

```http
POST /api/qwen/rule-review/one-click-review
Content-Type: multipart/form-data

参数:
  - file: [文件]
  - stance: "A方" | "B方" | "neutral"

响应:
  {
    success: true,
    minioUrl: "预览URL",
    issuesCount: 5
  }
```

---

## ⚡ 最小可行代码

如果只想最快实现，复制这段代码：

```javascript
// 3步完成一键审查
async function quickReview(file, stance) {
  // 1. 解析
  const fd1 = new FormData()
  fd1.append('file', file)
  fd1.append('anchors', 'generate')
  const parse = await fetch('/api/parse', {method: 'POST', body: fd1})
  const {partyA, partyB} = await parse.json()
  
  console.log('甲方:', partyA, '乙方:', partyB)
  
  // 2. 审查
  const fd2 = new FormData()
  fd2.append('file', file)
  fd2.append('stance', stance)
  const review = await fetch('/api/qwen/rule-review/one-click-review', {
    method: 'POST', 
    body: fd2
  })
  const result = await review.json()
  
  // 3. 预览
  if (result.success) {
    window.open(`/#/contract-review/editor?fileUrl=${encodeURIComponent(result.minioUrl)}`)
  }
}

// 使用
const fileInput = document.querySelector('input[type=file]')
quickReview(fileInput.files[0], 'A方')
```

---

## 🎯 3个关键点

### 1. stance参数值必须正确

```javascript
✅ "A方"  （不是"A"或"甲方"）
✅ "B方"  （不是"B"或"乙方"）
✅ "neutral"
```

### 2. 需要保存File对象

```javascript
// 两次API调用都需要同一个文件
const uploadedFile = ref<File>()

// 第一次
formData1.append('file', uploadedFile.value)

// 第二次（复用）
formData2.append('file', uploadedFile.value)
```

### 3. 审查需要5-10秒

```javascript
// 设置足够的超时时间
fetch(url, { 
  method: 'POST', 
  body: formData,
  signal: AbortSignal.timeout(60000)  // 60秒
})
```

---

## 🧪 验证方法

### 快速验证（5分钟）

```bash
# 1. 启动后端
cd Contract_review
mvn spring-boot:run

# 2. 新开终端，运行测试
cd Contract_review
测试一键审查API.bat

# 3. 查看结果
type review_result.json
```

### 完整验证（30分钟）

1. 导入Postman集合
2. 测试所有6个API
3. 检查响应格式
4. 记录测试结果

---

## 📂 文件位置

```
Contract_review/
├── API对接资料包-README.md                   ← 本文档
├── 前端对接超简洁清单.md                      ← ⚡快速参考
├── 一键审查API对接Prompt.md                   ← 📝开发Prompt
├── 一键审查前后端对接文档.md                  ← 📖完整文档
├── 前端对接开发任务书.md                      ← 📋项目任务
├── 一键审查API.postman_collection.json       ← 🧪Postman测试
└── 测试一键审查API.bat                        ← 🔧快速测试
```

---

## 🎓 学习路径

### 新手前端工程师

1. 阅读：`前端对接超简洁清单.md`
2. 测试：导入Postman集合测试API
3. 开发：参考 `一键审查API对接Prompt.md` 中的代码
4. 验证：使用测试脚本检查

### 有经验的前端工程师

1. 阅读：`一键审查API对接Prompt.md`
2. 开发：直接实现
3. 参考：`一键审查前后端对接文档.md`（遇到问题时）

### 技术负责人/架构师

1. 阅读：`一键审查前后端对接文档.md`
2. 评估：技术方案和工作量
3. 分配：使用 `前端对接开发任务书.md` 分配任务

---

## 💡 推荐使用流程

```
【第1天】
  9:00  - 阅读文档（前端对接超简洁清单.md）
  9:30  - 使用Postman测试API
  10:00 - 开始编码（参考API对接Prompt）
  12:00 - 完成基础功能

【第2天】
  9:00  - UI美化和错误处理
  10:00 - 完整测试
  11:00 - 修复Bug
  12:00 - 验收交付
```

---

## ✅ 验收标准

### 最低标准

- ✅ 能上传文件并显示甲乙方
- ✅ 能选择立场并提交审查
- ✅ 能查看审查结果

### 优秀标准

- ✅ 以上所有功能
- ✅ 有完善的错误处理
- ✅ 有流畅的进度显示
- ✅ UI美观专业
- ✅ 代码规范清晰

---

## 📞 获取帮助

### 问题分类

**API调用问题** → 查看 `一键审查前后端对接文档.md`  
**代码实现问题** → 查看 `一键审查API对接Prompt.md`  
**快速参考问题** → 查看 `前端对接超简洁清单.md`  
**后端API问题** → 运行 `测试一键审查API.bat` 诊断  

---

**资料包版本**: v1.0  
**创建时间**: 2025-11-03  
**维护人员**: 开发团队  
**下次更新**: 根据反馈优化

---

## 🎉 开始吧！

所有资料已准备就绪，祝您对接顺利！

如有任何问题，请随时查阅对应文档或寻求技术支持。

