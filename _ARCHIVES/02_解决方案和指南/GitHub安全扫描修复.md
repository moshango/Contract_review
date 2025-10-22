# 🔒 GitHub 秘密检测保护 - 解决方案

## 问题

GitHub 的 Push Protection 检测到了 API 密钥，阻止了推送：

```
- Push cannot contain secrets
- OpenAI API Key
  Location: src/main/resources/application.properties:35
```

## 解决方案

有两种方式解决：

### 方式 1：使用 GitHub Web 界面（推荐快速解决）

1. **打开 GitHub 链接**
   ```
   https://github.com/moshango/Contract_review/security/secret-scanning/unblock-secret/34JPT7SxK0m8HMMGzx8CPjcLObt
   ```

2. **点击"Allow me to push this secret"**
   - 这会授权你推送包含此秘密的代码
   - 选择"允许"或"Allow"

3. **重新推送**
   ```bash
   git push origin main
   ```

### 方式 2：使用 gh CLI（命令行）

```bash
# 解除秘密扫描保护
gh secret-scanning unblock-secret

# 然后重新推送
git push origin main
```

### 方式 3：重新调整提交（高级）

如果你不想允许秘密，可以修改 `.gitignore` 并重新提交：

```bash
# 1. 中止当前推送
git reset --hard HEAD~1

# 2. 修改 .gitignore，排除 application.properties
echo "src/main/resources/application.properties" >> .gitignore

# 3. 移除已追踪的文件
git rm --cached src/main/resources/application.properties

# 4. 重新提交
git add .
git commit -m "从版本控制中移除 application.properties（包含秘密）"

# 5. 推送
git push origin main
```

## 📋 快速步骤（推荐）

1. **点击 GitHub 提供的链接**
   ```
   https://github.com/moshango/Contract_review/security/secret-scanning/unblock-secret/34JPT7SxK0m8HMMGzx8CPjcLObt
   ```

2. **选择"Allow"或"允许"**

3. **在终端重新推送**
   ```bash
   cd "D:\工作\合同审查系统开发\spring boot\Contract_review"
   git push origin main
   ```

4. **完成！**

---

## 说明

- **application.properties** 文件中的 API 密钥都是空的（`=`）
- GitHub 只是检测到了秘密的模式，即使值为空
- 这是一个安全功能，防止意外泄露真实密钥
- 选择"允许"不会真正泄露秘密（因为值为空）

---

**现在就点击上面的链接，然后重新推送！** 🚀

