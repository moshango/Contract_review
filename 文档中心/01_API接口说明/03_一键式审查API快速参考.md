# 一键式审查API快速参考

**最后更新**: 2025-10-27

---

## API 端点

### POST /api/qwen/rule-review/one-click-review

一键完成合同审查工作流，返回带有审查批注的Word文档。

---

## 请求

### 请求方法
```
POST /api/qwen/rule-review/one-click-review
```

### 请求头
```
Content-Type: multipart/form-data
```

### 请求参数

| 参数 | 位置 | 类型 | 必需 | 说明 |
|-----|------|------|------|------|
| `file` | form | file | ✅ | 合同文件（.docx 或 .doc） |
| `stance` | form | string | ❌ | 审查立场（默认: neutral） |

### 立场参数值

| 值 | 说明 |
|---|-----|
| `neutral` | 中立角度（默认值） |
| `A方` | A方（甲方）视角 |
| `甲方` | A方（甲方）视角 |
| `B方` | B方（乙方）视角 |
| `乙方` | B方（乙方）视角 |

---

## 响应

### 成功响应 (200 OK)

**Content-Type**: `application/vnd.openxmlformats-officedocument.wordprocessingml.document`

**响应内容**: 二进制文件流（Word文档）

**同时**：文件自动保存到 `文档中心/已生成的审查报告/{合同名称}_一键审查_{立场}.docx`

### 错误响应

#### 400 Bad Request

```json
{
  "success": false,
  "error": "文件不能为空"
}
```

或

```json
{
  "success": false,
  "error": "仅支持 .docx 和 .doc 格式"
}
```

#### 500 Internal Server Error

```json
{
  "success": false,
  "error": "一键审查失败: 错误描述",
  "timestamp": 1635157223000
}
```

---

## 示例

### 使用 cURL

```bash
# 中立立场（默认）
curl -X POST "http://localhost:8080/api/qwen/rule-review/one-click-review" \
  -F "file=@/path/to/contract.docx" \
  -o annotated.docx

# 指定 A方 视角
curl -X POST "http://localhost:8080/api/qwen/rule-review/one-click-review" \
  -F "file=@/path/to/contract.docx" \
  -F "stance=A方" \
  -o annotated.docx

# 指定 B方 视角
curl -X POST "http://localhost:8080/api/qwen/rule-review/one-click-review" \
  -F "file=@/path/to/contract.docx" \
  -F "stance=B方" \
  -o annotated.docx
```

### 使用 JavaScript (Fetch API)

```javascript
const fileInput = document.getElementById('fileInput');
const stanceSelect = document.getElementById('stanceSelect');

const formData = new FormData();
formData.append('file', fileInput.files[0]);
formData.append('stance', stanceSelect.value || 'neutral');

fetch('/api/qwen/rule-review/one-click-review', {
    method: 'POST',
    body: formData
})
.then(response => response.blob())
.then(blob => {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileInput.files[0].name.replace(/\.docx?$/, '') + '_一键审查.docx';
    a.click();
    window.URL.revokeObjectURL(url);
})
.catch(error => console.error('审查失败:', error));
```

### 使用 Python

```python
import requests

# 文件路径
file_path = '/path/to/contract.docx'
url = 'http://localhost:8080/api/qwen/rule-review/one-click-review'

# 中立立场
with open(file_path, 'rb') as f:
    files = {'file': f}
    data = {'stance': 'neutral'}
    response = requests.post(url, files=files, data=data)

# 保存结果
with open('annotated.docx', 'wb') as f:
    f.write(response.content)

# A方 视角
with open(file_path, 'rb') as f:
    files = {'file': f}
    data = {'stance': 'A方'}
    response = requests.post(url, files=files, data=data)

with open('annotated_A.docx', 'wb') as f:
    f.write(response.content)
```

### 使用 Postman

1. **设置请求类型**: `POST`
2. **URL**: `http://localhost:8080/api/qwen/rule-review/one-click-review`
3. **Body** 标签页，选择 `form-data`：
   - Key: `file`, Value: 选择文件
   - Key: `stance`, Value: `neutral` (或 `A方`, `B方`)
4. **Send** 按钮，选择 `Send and Download`

---

## 工作流说明

API 执行以下6个步骤：

```
┌─ 1. 文件验证
├─ 2. 合同解析（提取条款、生成锚点）
├─ 3. 生成审查Prompt（根据立场定制）
├─ 4. Qwen审查（获取JSON格式的审查意见）
├─ 5. 文档批注（将审查意见插入Word）
└─ 6. 保存返回（保存到文档中心并下载）
```

---

## 性能指标

**典型处理时间**（12个条款，~5000字的合同）：

- 解析合同：~800ms
- 生成Prompt：~300ms
- Qwen审查：~3500ms
- 文档批注：~1000ms
- 保存文件：~200ms
- **总计**：**~6.2秒**

---

## 文件保存位置

生成的文档自动保存到：

```
{项目根目录}/文档中心/已生成的审查报告/{合同名称}_一键审查_{立场}.docx
```

例如：
- `文档中心/已生成的审查报告/技术服务协议_一键审查_neutral.docx`
- `文档中心/已生成的审查报告/采购合同_一键审查_A方.docx`
- `文档中心/已生成的审查报告/合作协议_一键审查_B方.docx`

---

## 相关接口

### 其他 Qwen 规则审查接口

| 接口 | 方法 | 说明 |
|-----|------|------|
| `/api/qwen/rule-review/review` | POST | 仅进行审查（不批注）|
| `/api/qwen/rule-review/status` | GET | 检查Qwen服务状态 |
| `/api/qwen/rule-review/config` | GET | 获取Qwen配置信息 |

### 合同解析接口

| 接口 | 方法 | 说明 |
|-----|------|------|
| `/parse` | POST | 仅解析合同 |
| `/annotate` | POST | 仅批注合同 |

---

## 常见问题

### Q: 审查需要多长时间？
**A**: 典型合同需要 5-10 秒，主要取决于网络延迟和Qwen API响应时间。

### Q: 生成的文档保存在哪里？
**A**: 自动保存到 `文档中心/已生成的审查报告/` 目录，同时返回供下载。

### Q: 可以指定不同的审查立场吗？
**A**: 可以。支持 `neutral`（中立）、`A方`/`甲方`、`B方`/`乙方` 三种立场。

### Q: 支持什么文件格式？
**A**: 仅支持 `.docx` 和 `.doc` 格式的Word文档。

### Q: 如果Qwen未配置会怎样？
**A**: API会返回 400 错误，提示 "Qwen服务未配置或不可用"。

---

**相关文档**: [`09_一键式审查功能实现.md`](../02_实现和修复总结/09_一键式审查功能实现.md)
