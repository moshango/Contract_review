# Qwen 一键审查功能 - 使用指南

## 🎯 功能一览

本项目已成功集成**阿里云Qwen AI模型**，实现**一键式合同智能审查**。

### 工作流程
```
上传合同 → 规则审查 → 一键Qwen审查 → 自动批注 → 下载文档
```

完整工作流程只需 **20-40秒**！

---

## ⚡ 快速开始 (5分钟)

### 第1步：配置API Key (1分钟)

编辑 `src/main/resources/application.properties`:

```properties
# 找到Qwen配置部分，添加您的API Key
qwen.api-key=sk-your-actual-api-key-here
qwen.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1
qwen.model=qwen-max-latest
qwen.timeout=30
```

> 获取API Key: https://dashscope.console.aliyun.com/

### 第2步：启动应用 (2分钟)

```bash
# 进入项目目录
cd Contract_review

# 方式1: Maven直接运行
mvn spring-boot:run

# 方式2: 打包后运行
mvn clean package
java -jar target/Contract_review-0.0.1-SNAPSHOT.jar
```

访问: http://localhost:8080

### 第3步：执行一键审查 (2分钟)

1. **点击** "规则审查" 标签页
2. **上传** 合同文件 (.docx)
3. **选择** 合同类型 (采购/外包/NDA等)
4. **选择** 审查立场 (中立/甲方/乙方)
5. **点击** "🔍 开始规则审查" 按钮
6. **等待** Prompt生成...
7. **点击** "⚡一键Qwen审查" 按钮 (新增！)
8. **等待** 15-30秒...
9. **自动填充** 审查结果到导入框 ✅
10. **点击** "📥 导入并生成批注文档"
11. **下载** 带批注的合同 ✅

完成！审查结果已自动集成到合同文档中。

---

## 📋 新增功能说明

### 一键Qwen审查按钮

位置：在规则审查结果的"LLM审查Prompt"下方

```
┌─────────────────────────────────────────┐
│ 📝 LLM审查Prompt                        │
│ [Prompt内容显示框]                      │
│                                         │
│ ┌─────────────────────────────────────┐│
│ │⚡一键Qwen审查│📋 复制│🌐 ChatGPT  ││
│ └─────────────────────────────────────┘│
└─────────────────────────────────────────┘
```

### 工作原理

1. **点击按钮** → 前端发送Prompt到后端
2. **后端接收** → 调用Qwen API进行审查
3. **Qwen处理** → AI智能分析合同条款 (15-30秒)
4. **返回结果** → JSON格式的审查问题列表
5. **自动处理** → 提取JSON并填充到导入框
6. **用户完成** → 点击导入生成批注文档

### 进度提示

审查过程中会显示实时进度：

```
⏳ 正在调用Qwen进行审查...
(约15-30秒，请耐心等待)

↓ (成功时)

✅ 审查完成！检出 5 个问题
(已自动填充到导入框)
```

---

## 🎨 UI界面更新

### 新增按钮样式

"⚡一键Qwen审查" 按钮采用渐变紫色设计：

```css
background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
color: white;
border: none;
```

### 进度提示样式

```
┌──────────────────────────────────┐
│ ⏳ 正在调用Qwen进行审查...      │
│ ⏱️ 审查需要15-30秒，请耐心等待  │
└──────────────────────────────────┘
```

---

## 📊 性能数据

| 操作 | 耗时 |
|------|------|
| 规则匹配 | < 2秒 |
| **Qwen AI审查** | **15-30秒** |
| 批注生成 | 2-5秒 |
| **总计** | **20-40秒** |

---

## ✨ 主要特性

### 1. 完全自动化
- ✅ 无需手动复制粘贴
- ✅ 自动填充审查结果
- ✅ 一键完成审查流程

### 2. 智能错误处理
- ✅ 自动修复JSON格式错误
- ✅ 支持多种响应格式
- ✅ 详细的错误提示

### 3. 实时反馈
- ✅ 进度图标动画 (⏳ → ✅)
- ✅ 问题计数显示
- ✅ 处理耗时统计

### 4. 审查立场化
- ✅ 支持甲方/乙方/中立三种立场
- ✅ 根据立场返回不同建议
- ✅ 符合法务专业需求

---

## 🔧 API 端点

### 一键审查接口

```
POST /api/qwen/rule-review/review
Content-Type: application/json

请求示例:
{
  "prompt": "根据规则审查...",
  "contractType": "采购合同",
  "stance": "A"
}

响应示例:
{
  "success": true,
  "issueCount": 5,
  "processingTime": "18234ms",
  "review": {
    "issues": [
      {
        "anchorId": "anc-c1-4f21",
        "clauseId": "c1",
        "severity": "HIGH",
        "category": "保密条款",
        "finding": "未定义保密信息范围",
        "suggestion": "应增加保密信息的定义..."
      }
    ]
  }
}
```

### 服务检查接口

```
GET /api/qwen/rule-review/status

响应示例:
{
  "success": true,
  "qwenAvailable": true,
  "message": "✓ Qwen服务已就绪"
}
```

---

## 🐛 故障排查

### Q: 一键按钮为什么是灰色的？

**A:** Qwen服务未配置。
```bash
# 检查配置
curl http://localhost:8080/api/qwen/rule-review/status

# 应该返回 qwenAvailable: true
```

检查清单：
- [ ] API Key已填写到 application.properties
- [ ] API Key不是占位符 (sk-xxx)
- [ ] 网络可以访问阿里云API
- [ ] 防火墙未屏蔽HTTPS出站连接

### Q: 为什么审查很慢？

**A:** 正常的。Qwen AI审查通常需要 15-30秒。

优化建议：
- 使用较小的Prompt (关键条款)
- 确保网络连接稳定
- 避免频繁刷新

### Q: JSON解析失败怎么办？

**A:** 可能是Qwen模型版本问题。
```properties
# 更新到最新模型
qwen.model=qwen-max-latest
```

---

## 📚 文档资源

| 文档 | 用途 |
|------|------|
| [QWEN_QUICK_START.md](docs/QWEN_QUICK_START.md) | 5分钟快速开始 |
| [QWEN_INTEGRATION_GUIDE.md](docs/QWEN_INTEGRATION_GUIDE.md) | 完整功能说明 |
| [QWEN_FEATURE_DEMO.md](docs/QWEN_FEATURE_DEMO.md) | 功能演示与集成 |
| [QWEN_API_REFERENCE.md](docs/QWEN_API_REFERENCE.md) | API详细参考 |

---

## 💡 使用技巧

### 技巧1: 快速切换审查立场

```
点击"规则审查" → 选择不同立场 → 点击"开始规则审查"
不同立场会得到不同的审查建议！
```

### 技巧2: 批量审查

虽然目前支持单文件审查，但可以：
1. 审查第一个合同
2. 下载批注结果
3. 上传第二个合同
4. 重复流程

### 技巧3: 自定义规则库

编辑 `rules.xlsx` 添加企业特定规则，提高审查准确度！

---

## 🚀 后续计划

### 计划中的功能
- [ ] 批量合同审查
- [ ] 审查历史记录
- [ ] 导出审查报告 (PDF)
- [ ] 自定义规则管理UI
- [ ] 审查模板库

---

## 📞 需要帮助？

### 检查清单

部署前：
- [ ] Qwen API Key已获取
- [ ] application.properties已更新
- [ ] 项目编译成功 (mvn clean compile)

部署后：
- [ ] 应用成功启动
- [ ] 可访问 http://localhost:8080
- [ ] 一键按钮可见且可用
- [ ] 成功完成至少一个审查流程

### 联系方式

- GitHub Issues: 提交问题或建议
- 技术讨论: 在项目Wiki中讨论

---

## 📖 更多信息

**完整实现文档**: [QWEN_IMPLEMENTATION_SUMMARY.md](docs/QWEN_IMPLEMENTATION_SUMMARY.md)

---

**版本**: 1.0
**最后更新**: 2025-10-24
**状态**: ✅ 生产就绪
