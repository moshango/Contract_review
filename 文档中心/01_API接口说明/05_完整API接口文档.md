# 一键审查后端API接口文档

> 📡 **纯后端接口说明**，适用于任何前端技术栈  
> 🔗 **RESTful API**，标准HTTP协议  
> 📋 **版本**: v1.1 | **最后更新**: 2025-11-04

---

## 📊 核心流程

```
步骤1: 上传文件 → POST /api/parse → 返回解析结果（甲乙方信息）
         ↓
步骤2: 提交审查 → POST /api/qwen/rule-review/one-click-review → 返回审查结果
         ↓
步骤3: 文档预览/下载
         方式A: 新预览API → GET /api/document-view/info (推荐)
         方式B: 代理下载 → GET /api/preview/proxy
```

**说明**：完整内容请参考原文档 `Contract_review/一键审查API对接Prompt.md`

---

**归档来源**：根目录 `一键审查API对接Prompt.md`  
**归档日期**：2025-11-04  
**分类**：API接口说明

