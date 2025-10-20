# Both模式 AnchorId一致性验证指南

**创建时间**: 2025-10-20 15:15
**目的**: 帮助用户快速验证Parse的Both模式中JSON与文档的anchorId是否真的一致

---

## 🎯 快速验证（5分钟）

### 方法1：命令行快速对比

```bash
# Windows Git Bash / Linux / macOS

# 1️⃣ 从Word文档中提取所有anchorId
cd "D:\工作\合同审查系统开发\spring boot\Contract_review"

# 解压docx（本质是ZIP文件）并提取document.xml中的书签
unzip -p parsed-测试合同_综合测试版.docx word/document.xml | \
  grep -oP 'bookmarkStart[^>]*Name="\K[^"]*' | \
  sort > document_anchors.txt

# 2️⃣ 从JSON中提取所有anchorId
cat annotate.json | \
  grep -oP '"anchorId"\s*:\s*"\K[^"]*' | \
  sort > json_anchors.txt

# 3️⃣ 对比
echo "=== 文档中的anchorId ==="
cat document_anchors.txt
echo ""
echo "=== JSON中的anchorId ==="
cat json_anchors.txt
echo ""
echo "=== 对比结果 ==="
diff document_anchors.txt json_anchors.txt && echo "✅ 完全一致！" || echo "❌ 存在差异"
```

### 预期结果

**一致的情况** (✅):
```
=== 对比结果 ===
✅ 完全一致！
```

**不一致的情况** (❌):
```
=== 对比结果 ===
< anc-c11-c72c
---
> anc-c11-f58c
```

这表示文档中是 `anc-c11-c72c`，但JSON中是 `anc-c11-f58c`

---

## 🔧 详细验证（10分钟）

### 方法2：Python脚本验证

创建文件 `verify_anchors.py`:

```python
#!/usr/bin/env python3
import json
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

def extract_anchors_from_docx(docx_path):
    """从Word文档中提取所有anchorId"""
    anchors = []
    try:
        with zipfile.ZipFile(docx_path, 'r') as zip_ref:
            with zip_ref.open('word/document.xml') as xml_file:
                root = ET.fromstring(xml_file.read())

                # 命名空间
                ns = {
                    'w': 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'
                }

                # 查找所有bookmarkStart
                for bookmark in root.findall('.//w:bookmarkStart', ns):
                    name = bookmark.get('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}name')
                    if name and name.startswith('anc-'):
                        anchors.append(name)
    except Exception as e:
        print(f"❌ 提取文档anchorId失败: {e}")
        return None

    return sorted(set(anchors))  # 去重并排序

def extract_anchors_from_json(json_path):
    """从JSON中提取所有anchorId"""
    anchors = []
    try:
        with open(json_path, 'r', encoding='utf-8') as f:
            data = json.load(f)

            if 'issues' in data:
                for issue in data['issues']:
                    if 'anchorId' in issue:
                        anchors.append(issue['anchorId'])
    except Exception as e:
        print(f"❌ 提取JSON anchorId失败: {e}")
        return None

    return sorted(set(anchors))

def main():
    docx_file = Path("parsed-测试合同_综合测试版.docx")
    json_file = Path("annotate.json")

    if not docx_file.exists():
        print(f"❌ 文件不存在: {docx_file}")
        return

    if not json_file.exists():
        print(f"❌ 文件不存在: {json_file}")
        return

    print("=" * 60)
    print("Parse Both模式 AnchorId一致性验证")
    print("=" * 60)

    # 提取anchorId
    doc_anchors = extract_anchors_from_docx(str(docx_file))
    json_anchors = extract_anchors_from_json(str(json_file))

    if doc_anchors is None or json_anchors is None:
        print("❌ 提取失败，无法继续验证")
        return

    # 显示结果
    print(f"\n📄 文档中的anchorId数量: {len(doc_anchors)}")
    for anchor in doc_anchors:
        print(f"   - {anchor}")

    print(f"\n📋 JSON中的anchorId数量: {len(json_anchors)}")
    for anchor in json_anchors:
        print(f"   - {anchor}")

    # 对比
    print("\n" + "=" * 60)
    print("对比结果:")
    print("=" * 60)

    if set(doc_anchors) == set(json_anchors):
        print("✅ 完全一致！JSON与文档中的anchorId完全相同。")
        print("\n✓ 这表示:")
        print("  - Parse的Both模式工作正常")
        print("  - 可以安心使用这个JSON进行批注")
        return 0
    else:
        print("❌ 存在差异！JSON与文档中的anchorId不匹配。")

        only_in_doc = set(doc_anchors) - set(json_anchors)
        only_in_json = set(json_anchors) - set(doc_anchors)

        if only_in_doc:
            print(f"\n❌ 仅在文档中出现的anchorId ({len(only_in_doc)}个):")
            for anchor in sorted(only_in_doc):
                print(f"   - {anchor}")

        if only_in_json:
            print(f"\n❌ 仅在JSON中出现的anchorId ({len(only_in_json)}个):")
            for anchor in sorted(only_in_json):
                print(f"   - {anchor}")

        print("\n✗ 这表示:")
        print("  1. JSON可能来自不同的parse运行")
        print("  2. 或者文档被修改后没有重新parse")
        print("  3. 或者Both模式存在代码缺陷")

        print("\n💡 建议:")
        print("  1. 重新调用parse的both模式")
        print("  2. 使用新生成的JSON和文档进行批注")
        return 1

if __name__ == "__main__":
    exit(main())
```

**运行**:
```bash
python verify_anchors.py
```

---

## 🧪 深度验证（20分钟）

### 方法3：新鲜测试 - 重新运行Both模式

这是最可靠的验证方法。

**步骤1**: 准备一个新的测试文件

```bash
# 使用一个没有被修改过的原始合同文件
cp original-contract.docx test-contract.docx
```

**步骤2**: 调用parse的both模式

```bash
# 保存返回的JSON和文档
curl -X POST "http://localhost:8080/parse?anchors=generate&returnMode=both" \
  -F "file=@test-contract.docx" \
  --output-dir test_result \
  -v > test_both_response.txt 2>&1

# 注意: 由于返回是multipart格式（JSON + 文档），需要手动提取
# 更好的方法是查看服务器日志和响应头
```

**步骤3**: 从响应中提取JSON部分

```bash
# 查看服务器返回的JSON日志
tail -50 app.log | grep -E "Issue|clauseId|anchorId"
```

**步骤4**: 对比JSON和文档

```bash
# 如果能成功提取JSON和文档，使用之前的脚本验证
python verify_anchors.py
```

**预期结果**:
- ✅ 新生成的JSON与文档应该完全一致
- 如果一致，说明Both模式正确
- 如果不一致，说明存在代码缺陷

---

## 🎯 结果解读

### 场景1: 验证结果一致 ✅

```
✅ 完全一致！JSON与文档中的anchorId完全相同。
```

**含义**:
- Both模式代码正确
- parse生成的JSON与文档是配对的
- 用户之前看到的不一致是因为JSON来自不同的parse运行

**下一步**:
- 使用正确的JSON/文档配对进行批注
- 或者按照正确的工作流重新生成

### 场景2: 验证结果不一致 ❌

```
❌ 存在差异！
❌ 仅在文档中出现的anchorId (5个):
   - anc-c1-abc1
   - anc-c2-def2
   ...
❌ 仅在JSON中出现的anchorId (3个):
   - anc-c1-xyz1
   ...
```

**含义**:
- JSON来自不同的parse运行（可能是旧版本）
- 或者文档被修改后重新parse，但JSON没有更新

**下一步**:
1. 确认JSON的来源（查看修改时间）
2. 重新调用parse的both模式
3. 使用新生成的JSON/文档配对

### 场景3: 格式问题 ❌

```
❌ 提取文档anchorId失败
❌ 提取JSON anchorId失败
```

**含义**:
- 文件格式有问题
- 或者anchorId命名格式不符合预期

**下一步**:
1. 检查文件是否真的是.docx和.json格式
2. 手动检查JSON结构是否正确
3. 查看服务器日志获取详细错误

---

## 📋 调试信息收集

如果验证过程中发现问题，请收集以下信息：

```bash
# 1️⃣ 导出文档中的XML（用于分析）
unzip -p parsed-测试合同_综合测试版.docx word/document.xml > document.xml

# 2️⃣ 复制JSON
cp annotate.json annotate_debug.json

# 3️⃣ 查看服务器日志
tail -100 app.log > debug_logs.txt

# 4️⃣ 收集系统信息
echo "=== 文件信息 ===" > debug_info.txt
ls -la parsed-测试合同_综合测试版.docx >> debug_info.txt
ls -la annotate.json >> debug_info.txt
echo "" >> debug_info.txt
echo "=== 文件时间戳 ===" >> debug_info.txt
stat parsed-测试合同_综合测试版.docx >> debug_info.txt
stat annotate.json >> debug_info.txt
```

将这些文件保存，以备后续分析。

---

## ✅ 快速检查清单

- [ ] 确认文件存在且路径正确
- [ ] 运行验证脚本得到结果
- [ ] 理解结果含义（一致/不一致）
- [ ] 如果不一致，确认JSON来源
- [ ] 根据结果采取相应的修复措施
- [ ] 保留调试信息备查

---

## 💡 推荐的工作流（避免anchorId不一致）

```bash
# 1️⃣ 上传合同，使用both模式生成文档+JSON
curl -X POST "http://localhost:8080/parse?anchors=generate&returnMode=both" \
  -F "file=@contract.docx" \
  -o parse_response.zip

# 2️⃣ 解压获得文档和JSON
unzip parse_response.zip
# 获得: parsed_contract.docx 和 parse_result.json

# 3️⃣ 从parse_result.json中复制anchorId到审查JSON中
# ✅ 这样确保使用的anchorId与文档中的书签完全匹配

# 4️⃣ 调用批注API
curl -X POST "http://localhost:8080/annotate?anchorStrategy=preferAnchor" \
  -F "file=@parsed_contract.docx" \
  -F "review=@review_with_correct_anchors.json" \
  -o annotated_contract.docx

# ✅ 结果: 精确定位的批注！
```

---

**更新日期**: 2025-10-20 15:15
**版本**: 1.0
