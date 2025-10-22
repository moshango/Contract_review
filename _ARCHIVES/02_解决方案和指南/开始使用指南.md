# 🚀 快速启动指南

## 端口问题已解决!

为避免与其他项目的端口冲突,系统已更改为使用 **8080** 端口。

## 启动步骤

### 方法一: 使用 Maven 直接运行

```bash
mvn spring-boot:run
```

### 方法二: 打包后运行

```bash
# 1. 打包
mvn clean package

# 2. 运行
java -jar target/Contract_review-0.0.1-SNAPSHOT.jar
```

## 访问地址

启动成功后,在浏览器中访问:

```
http://localhost:8080
```

您将看到 AI 合同审查助手的 Web 界面!

## 验证启动成功

### 1. 查看控制台输出

应该看到类似以下信息:
```
Tomcat started on port(s): 8080 (http)
Started ContractReviewApplication in X.XXX seconds
```

### 2. 测试健康检查

在新的终端执行:
```bash
curl http://localhost:8080/health
```

预期返回:
```json
{
  "status": "UP",
  "service": "AI Contract Review Assistant",
  "version": "1.0.0"
}
```

### 3. 访问 Web 界面

打开浏览器访问: http://localhost:8080

应该看到:
- 渐变紫色背景
- "🤖 AI 合同审查助手" 标题
- 两个选项卡: 📄 合同解析 和 ✍️ 合同批注

## 故障排查

### 问题 1: 端口仍然被占用

如果 8080 端口也被占用,修改配置文件:

```bash
# 编辑 src/main/resources/application.properties
# 将 server.port=8080 改为其他端口,如 8091
```

然后重新编译运行。

### 问题 2: 访问显示其他项目页面

**原因:** 可能访问了错误的端口(如旧的 8080)

**解决:**
1. 确认使用 **8080** 端口
2. 检查控制台确认实际启动端口
3. 清除浏览器缓存后重试

### 问题 3: 404 Not Found

**解决方案:**
1. 确认静态资源已正确编译
2. 检查 target/classes/static 目录下是否有 index.html
3. 重新编译: `mvn clean compile`

### 问题 4: API 调用失败

**解决方案:**
1. 打开浏览器开发者工具 (F12)
2. 查看 Network 标签检查请求
3. 确认请求的 URL 是否正确 (应该是 localhost:8080)
4. 检查后端日志是否有错误信息

## 完整测试流程

### 测试 1: 访问主页

```bash
# 启动项目
mvn spring-boot:run

# 在浏览器打开
http://localhost:8080
```

✅ 应该看到 Web 界面

### 测试 2: 测试文件上传

1. 准备一个测试 .docx 文件(包含"第一条"、"第二条"等条款)
2. 在 Web 界面点击 "📄 合同解析"
3. 上传文件
4. 选择 "生成锚点" + "JSON结果"
5. 点击 "🚀 开始解析"

✅ 应该返回 JSON 格式的解析结果

### 测试 3: 测试批注功能

1. 切换到 "✍️ 合同批注" 选项卡
2. 上传同一个文件
3. 粘贴测试 JSON:
```json
{
  "issues": [
    {
      "clauseId": "c1",
      "severity": "HIGH",
      "category": "测试",
      "finding": "这是测试批注",
      "suggestion": "这是测试建议"
    }
  ]
}
```
4. 点击 "✨ 开始批注"

✅ 应该自动下载带批注的文档

## 端口配置文件位置

```
src/main/resources/application.properties
```

当前配置:
```properties
server.port=8080
```

## 已修复的问题

- ✅ 修改端口从 8080 改为 8080
- ✅ 创建专用的 HomeController 确保正确路由
- ✅ 更新所有文档中的端口引用
- ✅ 编译测试通过

## 下一步

启动项目后:

1. 访问 http://localhost:8080 查看 Web 界面
2. 尝试上传测试文件
3. 查看 README.md 了解完整功能
4. 查看 WEB_GUIDE.md 了解详细使用方法

祝使用愉快! 🎉
