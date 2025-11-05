# 01_API接口说明

## 📡 API 接口和开发规范

本目录包含项目的最新 API 接口文档和开发规范。

### 📄 文档列表

| 文档 | 说明 |
|-----|------|
| **00_项目开发规范.md** | 🔥 **核心文档** - 项目架构、技术栈、核心类、开发规范 |
| **01_项目简介.md** | 项目基本信息和快速启动指南 |
| **02_Qwen规则审查API参考.md** | Qwen 规则审查功能的完整 API 参考 |
| **03_一键式审查API快速参考.md** | ⭐ **新增** - 一键式审查 API 快速参考 |
| **04_一键式审查集成指南.md** | ⭐ **新增** - 前后端完整集成指南和代码示例 |

---

## 🚀 快速开始

### 第一步：了解项目结构
阅读 **00_项目开发规范.md**，包含：
- 项目概述和目标
- 技术栈说明
- 模块架构
- 核心类说明
- 开发规范和注意事项

### 第二步：学习 API 接口
从 **02_Qwen规则审查API参考.md** 中学习：
- `/parse` 接口：合同解析
- `/annotate` 接口：批注插入
- 参数说明和返回格式

### 第三步：运行项目
参考 **01_项目简介.md** 中的快速启动命令。

---

## 📌 关键接口速查

### `/parse` - 合同解析
```
POST /parse?anchors=generate&returnMode=both
```
**功能**：解析合同、生成锚点、返回条款结构

**参数**：
- `anchors`: `none|generate|regenerate` (默认: none)
- `returnMode`: `json|file|both` (默认: json)

### `/annotate` - 批注插入
```
POST /annotate?anchorStrategy=preferAnchor&cleanupAnchors=true
```
**功能**：在合同中插入批注

**参数**：
- `anchorStrategy`: `preferAnchor|anchorOnly|textFallback`
- `cleanupAnchors`: `true|false` (默认: false)

---

## 💼 技术栈速查

| 项目 | 版本 |
|-----|------|
| Java | 17 |
| Spring Boot | 3.5.6 |
| Maven | 最新 |
| Word 处理 | Apache POI / Docx4j |

---

## 📞 反馈和问题

- 📧 API 问题：查看接口文档或诊断报告
- 🔧 实现问题：查看实现和修复总结目录
- ❓ 常见问题：查看快速参考目录

---

**相关目录**：
- 实现细节 → [`02_实现和修复总结/`](../02_实现和修复总结/)
- 问题诊断 → [`03_诊断和分析/`](../03_诊断和分析/)
- 快速参考 → [`05_快速参考/`](../05_快速参考/)

**返回**：[📚 文档中心主页](../README.md)
