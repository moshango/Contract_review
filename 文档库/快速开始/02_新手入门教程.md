# Qwen 一键审查 - 快速开始指南

## 5分钟快速开始

### 第1步: 配置Qwen API Key (1分钟)

编辑 `src/main/resources/application.properties`:

```properties
# 阿里云Qwen配置
qwen.api-key=sk-your-actual-api-key-here
qwen.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1
qwen.model=qwen-max-latest
qwen.timeout=30
```

> 📌 获取API Key: https://dashscope.console.aliyun.com/

### 第2步: 启动应用 (2分钟)

```bash
# 方式1: Maven运行
cd Contract_review
mvn spring-boot:run

# 方式2: 已打包的JAR
java -jar target/Contract_review-0.0.1-SNAPSHOT.jar
```

访问: http://localhost:8080/

### 第3步: 执行一键审查 (2分钟)

1. 点击 **"规则审查"** 标签
2. 📁 上传合同文件 (.docx)
3. 选择 **合同类型** 和 **审查立场**
4. 点击 **"开始规则审查"** 按钮
5. 等待Prompt生成...
6. 点击 **"⚡一键Qwen审查"** 按钮
7. 等待15-30秒，审查完成自动填充
8. 点击 **"导入并生成批注文档"**
9. ✅ 文档自动下载完成!

## 工作流程示意图

```
┌─────────────────────┐
│  上传合同文件       │
│   (.docx / .doc)    │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  执行规则审查       │
│  (关键字/正则匹配)  │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────────────┐
│  生成结构化Prompt           │
│  (规则检查清单 + 条款内容)  │
└──────────┬──────────────────┘
           │
           ▼
    ┌─────────────────┐
    │ 点击一键按钮    │
    └────────┬────────┘
             │
             ▼
┌───────────────────────────┐
│ 调用Qwen AI审查           │
│ (POST /api/qwen/rule-     │
│  review/review)           │
│                           │
│ 耗时: 15-30秒            │
└────────┬──────────────────┘
         │
         ▼
┌──────────────────────────┐
│ 解析审查JSON结果         │
│ 自动填充到导入框         │
└────────┬─────────────────┘
         │
         ▼
┌──────────────────────────┐
│ 点击导入并生成批注文档   │
│ 自动生成带批注的DOCX     │
└────────┬─────────────────┘
         │
         ▼
    ┌─────────────────┐
    │ ✅ 下载文档    │
    │   审查完成!     │
    └─────────────────┘
```

## 核心特性

### ⚡ 一键操作
- 无需复制粘贴Prompt到ChatGPT
- 一个按钮完成从审查到批注的全流程
- 自动处理JSON解析和错误修复

### 🎯 智能审查
- 支持多种合同类型识别
- 支持甲方/乙方/中立三种审查立场
- 根据立场返回针对性的修改建议

### 📊 实时反馈
- 进度提示 (⏳ → ✅)
- 问题计数
- 处理耗时统计

### 🛡️ 容错能力
- JSON格式自动修复
- 支持多种响应格式
- 详细的错误提示

## 常见问题

### Q: 为什么一键按钮是灰色的?

**A:** Qwen服务未配置。检查:
```bash
# 1. 查看API Key是否正确
GET http://localhost:8080/api/qwen/rule-review/config

# 2. 查看服务状态
GET http://localhost:8080/api/qwen/rule-review/status

# 3. 确认配置文件中有:
qwen.api-key=sk-xxxx
```

### Q: 审查时间太长?

**A:** 正常情况下需要15-30秒。如果超时:
```properties
# 增加超时时间
qwen.timeout=60
```

### Q: 返回JSON解析失败?

**A:** 可能是Qwen模型版本问题。尝试:
```properties
# 更新到最新模型
qwen.model=qwen-max-latest
```

### Q: 如何查看详细日志?

**A:** 启用DEBUG日志:
```properties
logging.level.com.example.Contract_review.service=DEBUG
logging.level.com.example.Contract_review.controller=DEBUG
logging.level.com.example.Contract_review.qwen=DEBUG
```

## 后续操作

### 获得更好的审查结果

1. **调整合同类型**: 选择更精确的类型以加载相关规则
2. **选择审查立场**: 甲方/乙方立场会返回针对性建议
3. **自定义规则**: 可编辑 `rules.xlsx` 添加企业特定规则

### 集成到自动化流程

```bash
# 使用curl调用API
curl -X POST http://localhost:8080/api/qwen/rule-review/review \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "根据规则审查合同...",
    "contractType": "采购合同",
    "stance": "A"
  }'
```

### 扩展功能

- 支持批量审查 (多个文件)
- 集成到n8n/Zapier自动化
- 与企业法务系统对接
- 生成审查分析报告

## 故障排除

### 检查清单

- [ ] Qwen API Key已配置
- [ ] 网络可以访问 https://dashscope.aliyuncs.com/
- [ ] 防火墙允许出网
- [ ] 合同文件格式正确 (.docx)
- [ ] 规则文件已加载 (rules.xlsx)
- [ ] 日志中没有报错信息

### 查看状态接口

```bash
# 检查Qwen服务是否就绪
curl http://localhost:8080/api/qwen/rule-review/status

# 查看配置信息
curl http://localhost:8080/api/qwen/rule-review/config

# 查看规则加载情况
curl http://localhost:8080/api/review/rules
```

## 性能数据

| 操作 | 耗时 |
|------|------|
| 规则匹配 | < 2秒 |
| Qwen审查 | 15-30秒 |
| 批注生成 | 2-5秒 |
| 文件下载 | < 1秒 |
| **总计** | **20-40秒** |

## 下一步

### 推荐阅读

1. 📖 [完整功能文档](QWEN_INTEGRATION_GUIDE.md)
2. 🔧 [API接口文档](https://dashscope.console.aliyun.com/)
3. 💡 [规则配置指南](../src/main/resources/review-rules/README.md)

### 获取帮助

- 查看服务器日志: `logs/` 目录
- 浏览器控制台 (F12): Console标签
- 检查配置: `application.properties`
- API测试: Postman / curl

---

**版本**: v1.0
**最后更新**: 2025-10-24
**状态**: ✅ 生产就绪
