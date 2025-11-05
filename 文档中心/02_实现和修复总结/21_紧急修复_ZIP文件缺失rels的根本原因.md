# 紧急修复：ZIP文件中 document.xml.rels 完全缺失

## 问题发现

根据最新日志（第968行），发现了更严重的问题：

```
ZIP文件验证：hasRels=false, hasComments=true, relsSize=0字节
ERROR: ZIP文件中缺少 document.xml.rels！
```

### 关键观察

1. **OPCPackage 中**：document.xml.rels 存在（1367字节），包含 comments 关系
2. **保存前验证**：✓ 通过（第965行）
3. **opcPackage.save() 后**：document.xml.rels **完全消失**！

### 根本原因推测

`opcPackage.save()` 在生成 ZIP 文件时，**没有包含** document.xml.rels 文件。

可能的原因：
1. **OPCPackage 的内部实现问题**：document.xml.rels 可能是一个特殊的"关系"文件，需要在 OPCPackage 层面注册，而不是作为普通 Part
2. **保存顺序问题**：可能需要在保存 document.xml 之后再保存 rels
3. **路径映射问题**：OPCPackage 可能没有正确映射 rels 文件的路径

## 修复方案

### 方案1：使用 ZIP 手动修复

如果 `opcPackage.save()` 无法正确保存 rels 文件，我们可以：

1. 先调用 `opcPackage.save()` 生成基本的 ZIP
2. 然后手动读取 `document.xml.rels` 的字节数组
3. 使用 Java ZIP API 手动添加到 ZIP 文件中

### 方案2：延迟保存 OPCPackage

将 `updateDocumentRels()` 的调用移到更早的位置，避免被覆盖。

### 方案3：使用不同的 OPCPackage 实现

尝试使用 Apache POI 的 `OPCPackage` 的不同实现方式。

## 临时解决方案

考虑到问题的复杂性，建议先使用**方案1**进行紧急修复：

```java
// 1. 先保存 OPCPackage
ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
opcPackage.save(outputStream);
opcPackage.close();

// 2. 手动读取 document.xml.rels
byte[] relsBytes = ... // 从 OPCPackage 获取

// 3. 手动添加到 ZIP
try (java.util.zip.ZipInputStream zipIn = new java.util.zip.ZipInputStream(
     new java.io.ByteArrayInputStream(outputStream.toByteArray()));
     java.util.zip.ZipOutputStream zipOut = new java.util.zip.ZipOutputStream(new ByteArrayOutputStream())) {
    
    // 复制现有条目
    java.util.zip.ZipEntry entry;
    while ((entry = zipIn.getNextEntry()) != null) {
        if ("word/_rels/document.xml.rels".equals(entry.getName())) {
            // 跳过原有的
            continue;
        }
        zipOut.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
        zipOut.write(zipIn.readAllBytes());
        zipOut.closeEntry();
    }
    
    // 添加我们的 rels 文件
    java.util.zip.ZipEntry relsEntry = new java.util.zip.ZipEntry("word/_rels/document.xml.rels");
    zipOut.putNextEntry(relsEntry);
    zipOut.write(relsBytes);
    zipOut.closeEntry();
}
```

## 急需测试

请编译新代码并查看日志，特别关注：

1. **立即验证阶段**（第1376行）：能看到多少个关系？
2. **ZIP验证阶段**（第968行）：hasRels 是否仍然为 false？

这将帮助我们确定：
- 是否是 OPCPackage 的问题
- 还是我们的验证逻辑有问题

---

**状态**：紧急调试中  
**需要**：查看更详细的日志确认 OPCPackage.save() 的行为


