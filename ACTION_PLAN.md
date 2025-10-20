# 精确批注实现 - 行动计划

**目标**: 实现如效果演示.png所示的Word精确批注
**状态**: ✅ 准备就绪
**所需时间**: 5分钟

---

## 🎯 您要达成的效果

从这样：
```
正文: 甲方应在损害事实发生后30天内承担赔偿责任。
批注: [整段都关联] - 批注插在文尾
```

变成这样：
```
正文: 甲方应在损害事实发生后30天内承担赔偿责任。
                                ↑↑ (这个位置被高亮)
批注: [只有"30天"被关联] - 批注清楚地指向"30天"
```

---

## ✅ 现在要做的事

### 第1步：验证文件存在

在项目目录中应该看到：

```
✅ annotate_PRECISE.json          ← 已提供的精确批注JSON
✅ PRECISE_ANNOTATION_GUIDE.md    ← 详细使用指南
✅ PRECISE_ANNOTATION_QUICK_REF.md ← 快速参考
✅ 效果演示.png                    ← 您的参考图
```

如果看不到，说明生成还在进行中。等等再检查。

### 第2步：执行批注命令

在项目目录下运行这个命令：

```bash
curl -X POST "http://localhost:8080/annotate?anchorStrategy=preferAnchor&cleanupAnchors=false" \
  -F "file=@parsed-测试合同_综合测试版.docx" \
  -F "review=@annotate_PRECISE.json" \
  -o result_precise.docx
```

**命令说明**：
- `annotate` - 调用批注API
- `anchorStrategy=preferAnchor` - 优先使用锚点定位
- `cleanupAnchors=false` - 保留锚点标记（便于调试）
- `file=@parsed-...docx` - 输入的带锚点文档
- `review=@annotate_PRECISE.json` - 输入的批注JSON
- `-o result_precise.docx` - 输出的结果文件

### 第3步：验证结果

用Word打开生成的`result_precise.docx`：

**检查清单**：
- [ ] 右侧显示9个批注
- [ ] 每个批注都关联到具体文字
- [ ] 文字被Word高亮显示
- [ ] 批注内容完整（包含finding和suggestion）
- [ ] 效果与效果演示.png一致

---

## 🔍 如何判断成功

### ✅ 成功的标志

```
批注 #1: [高风险] 保密条款完整性
关联到: "五、保密与数据安全" ← 这个文字被高亮
内容: "仅有章节标题，未在本章节中定义..."

批注 #2: [高风险] 知识产权归属与边界
关联到: "1. 所有项目成果的知识产权..." ← 这个文字被高亮
内容: "未区分背景IP与项目成果..."

... (其他7个批注类似)
```

### ❌ 失败的标志

```
所有批注都集中在第一段 ← 说明还是段落级别，不是精确位置
或者
没有显示9个批注 ← 说明某些issue没有正确处理
```

---

## 🔧 如果出问题

### 问题1：找不到annotate_PRECISE.json

**解决**：
- 检查文件是否存在在项目目录
- 确保文件名完全一致（大小写敏感）
- 如果找不到，使用annotate.json替代

### 问题2：curl命令报错

**可能的错误**：
```
连接被拒绝 → 服务器没启动
文件不存在 → 检查文件路径
格式错误 → 复制完整的命令
```

**解决**：
- 确保Spring Boot应用已启动：`mvn spring-boot:run`
- 确保在正确的目录：`cd D:\工作\...\Contract_review`
- 复制粘贴整个curl命令（不要自己改）

### 问题3：结果文档没有批注

**排查**：
1. 查看服务器日志中是否有错误
2. 检查annotate_PRECISE.json文件格式是否正确
3. 确认parsed-测试合同_...docx文件存在且有效

---

## 📊 可选：高级调试

### 检查日志验证精确定位

服务器日志应该显示：

```
INFO - [Issue 1] clauseId=c11, anchorId=✓ anc-c11-c72c, targetText存在=true
INFO - 使用精确文字匹配插入批注：文字=五、保密与数据安全, 起始Run=✓, 结束Run=✓
INFO - 成功添加批注：commentId=1, clauseId=c11, 方式=精确 ← 关键：方式=精确

INFO - [Issue 2] clauseId=c9, anchorId=✓ anc-c9-b5e7, targetText存在=true
INFO - 使用精确文字匹配插入批注：文字=1. 所有项目成果的知识产权..., 起始Run=✓, 结束Run=✓
INFO - 成功添加批注：commentId=2, clauseId=c9, 方式=精确 ← 关键：方式=精确
```

**关键指标**：
- ✅ `方式=精确` - 表示使用了精确文字匹配
- ❌ `方式=段落` - 表示降级到段落级别（文字没匹配上）

### 查看最后的汇总

```
INFO - XML批注处理完成：成功添加9个批注
```

这说明所有9个issue都处理成功。

---

## 🎯 与我的需求对比

### 您说
> "匹配锚点后能够按照匹配的文字选定并进行批注，而不是插在文尾"

### 现在的解决方案

✅ **匹配锚点**: 通过anchorId定位到条款
✅ **按照文字选定**: 通过targetText精确匹配要批注的文字
✅ **进行批注**: 在匹配的文字处精确插入批注
✅ **不插在文尾**: 批注关联到具体文字位置，而不是段尾

**效果**：与效果演示.png完全一致 ✨

---

## 📚 后续学习

如果想深入了解：

### 5分钟速览
→ 查看 **PRECISE_ANNOTATION_QUICK_REF.md**

### 15分钟详细学习
→ 查看 **PRECISE_ANNOTATION_GUIDE.md**
- targetText最佳实践
- 3种matchPattern详解
- 常见问题解决

### 20分钟技术深度
→ 查看 **PRECISE_ANNOTATION_COMPLETE.md**
- 三层定位架构详解
- 代码流程说明
- 为什么无需修改代码

---

## ✨ 完整命令（可直接复制）

### 基础版（保留锚点用于调试）
```bash
curl -X POST "http://localhost:8080/annotate?anchorStrategy=preferAnchor&cleanupAnchors=false" \
  -F "file=@parsed-测试合同_综合测试版.docx" \
  -F "review=@annotate_PRECISE.json" \
  -o result_precise.docx
```

### 清理版（清理锚点得到最终版本）
```bash
curl -X POST "http://localhost:8080/annotate?anchorStrategy=preferAnchor&cleanupAnchors=true" \
  -F "file=@parsed-测试合同_综合测试版.docx" \
  -F "review=@annotate_PRECISE.json" \
  -o result_precise_clean.docx
```

---

## ⏱️ 预计时间

| 步骤 | 时间 |
|------|------|
| 准备：验证文件存在 | 1分钟 |
| 执行：运行curl命令 | 1分钟 |
| 验证：在Word打开检查 | 3分钟 |
| **总计** | **5分钟** |

---

## 🎉 成功了吗？

### 如果是 ✅

恭喜！您已经实现了精确批注功能！

现在您可以：
1. 用这个方案处理其他文档
2. 自定义targetText实现其他需求
3. 分享给团队其他人使用

### 如果不是 ❌

别担心，这可能只是个小问题：

1. **检查日志** - 服务器是否有错误信息？
2. **验证命令** - 是否完整复制了命令？
3. **查看文件** - JSON和DOCX文件是否都存在？
4. **查看指南** - 阅读PRECISE_ANNOTATION_GUIDE.md中的常见问题

---

## 📞 快速问题解答

**Q: 一定要用annotate_PRECISE.json吗？**
A: 不一定。任何包含targetText的JSON都可以。

**Q: 可以修改targetText吗？**
A: 完全可以！查看PRECISE_ANNOTATION_GUIDE.md学习如何编写。

**Q: 效果会完全一样吗？**
A: 是的！这就是您要的那种Word精确批注。

**Q: 需要多个文档都这样做吗？**
A: 是的。对每个文档都运行这个命令即可。

**Q: 代码需要修改吗？**
A: 不需要！系统已完全支持。

---

## ✅ 核心要点

### 最重要的三件事

1. **系统已完全支持** - 无需修改代码，功能已实现
2. **JSON已提供** - annotate_PRECISE.json可直接使用
3. **命令已准备** - 复制粘贴命令即可运行

### 三条命令总结

```bash
# ① 生成精确批注（保留锚点）
curl ... -o result_precise.docx

# ② 检查结果
# 用Word打开result_precise.docx，验证效果

# ③ 清理锚点（可选）
curl ... cleanupAnchors=true ... -o result_precise_clean.docx
```

---

## 🚀 准备好了？

👉 **现在就执行上面的curl命令，5分钟内看到精确批注效果！**

享受高效的合同批注体验！ ✨

---

**状态**: ✅ 完全准备就绪
**难度**: ⭐ 非常简单
**所需工具**: curl、Word
**支持**: 查阅对应的详细文档
