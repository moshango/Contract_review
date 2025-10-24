# 集成指南 - 文档导航

本目录提供代码集成和二次开发的指南。

## 📋 文档列表

本目录计划包含以下文档:

- 📄 前端集成指南
- 📄 后端集成指南
- 📄 自定义规则开发
- 📄 扩展功能指南
- 📄 性能优化指南

*(文档正在编写中)*

## 🎯 集成方向

### 前端集成

**集成点**: `qwen-review.js`

**主要功能**:
- 一键审查按钮点击事件
- API调用和数据处理
- 进度显示和结果填充

**扩展空间**:
- 自定义UI样式
- 实现额外的校验
- 添加更多的交互

### 后端集成

**集成点**: `QwenRuleReviewService`、`QwenRuleReviewController`

**主要功能**:
- Prompt处理
- Qwen API调用
- JSON结果解析

**扩展空间**:
- 自定义审查逻辑
- 缓存机制实现
- 异步处理优化

## 💻 常见集成场景

### 场景1: 自定义UI

**需求**: 修改一键按钮的样式和位置

**文件**: `index.html`

**修改点**:
```html
<!-- 修改按钮样式 -->
<button class="btn" id="qwen-review-btn"
        style="background: your-color;">
  自定义文本
</button>
```

### 场景2: 自定义校验

**需求**: 审查前进行额外的数据校验

**文件**: `qwen-review.js`

**修改点**:
```javascript
async function startQwenReview() {
    // 添加自定义校验
    if (!validateCustomRules(prompt)) {
        showToast('校验失败', 'error');
        return;
    }

    // 继续原有流程
    ...
}
```

### 场景3: 审查结果缓存

**需求**: 缓存相同Prompt的审查结果

**文件**: `QwenRuleReviewService.java`

**思路**:
```java
// 使用Map缓存结果
private Map<String, String> resultCache = new ConcurrentHashMap<>();

public String reviewContractWithQwen(String prompt) {
    // 检查缓存
    if (resultCache.containsKey(prompt)) {
        return resultCache.get(prompt);
    }

    // 调用Qwen
    String result = ...

    // 保存到缓存
    resultCache.put(prompt, result);
    return result;
}
```

### 场景4: 审查历史记录

**需求**: 保存所有审查记录

**文件**: 新增 `ReviewHistory.java`

**思路**:
```java
// 创建审查历史表
// 记录: 文件名、时间、问题数、审查人员等
```

## 📚 开发指南

### 快速开始集成开发

1. **阅读源代码**
   - QwenRuleReviewService.java
   - QwenRuleReviewController.java
   - qwen-review.js

2. **理解数据流**
   ```
   前端UI → API请求 → 后端处理 → Qwen调用
        ↓                         ↓
   结果填充 ← 返回结果 ← JSON解析 ← AI响应
   ```

3. **修改和测试**
   - 修改源代码
   - 运行 `mvn compile` 验证
   - 启动应用并测试

4. **提交变更**
   - Git add/commit/push
   - 请求代码审查

### 文件组织

```
src/main/java/.../contract_review/
├── service/
│   ├── QwenRuleReviewService.java      ← 核心逻辑
│   └── ... (其他服务)
├── controller/
│   ├── QwenRuleReviewController.java   ← API端点
│   └── ... (其他控制器)
└── qwen/
    ├── client/QwenClient.java          ← API客户端
    └── dto/ChatResponse.java           ← 数据模型

src/main/resources/static/
└── js/
    ├── qwen-review.js                  ← 前端逻辑
    └── ... (其他JS)
```

## 🔌 扩展点

### 业务逻辑扩展

1. **自定义审查规则**
   - 修改 `ReviewRulesService`
   - 实现自己的规则匹配

2. **审查结果处理**
   - 修改 `parseReviewResults`
   - 实现自定义的结果解析

3. **错误处理**
   - 修改 `buildErrorResponse`
   - 实现自定义的错误处理

### 功能扩展

1. **批量审查**
   - 新增批量接口
   - 实现并发处理

2. **审查历史**
   - 添加数据库存储
   - 实现历史查询

3. **审查报告**
   - 生成PDF报告
   - 导出审查统计

## 🧪 测试指南

### 单元测试

```java
@SpringBootTest
public class QwenRuleReviewServiceTest {

    @Autowired
    private QwenRuleReviewService service;

    @Test
    public void testReviewContract() {
        String result = service.reviewContractWithQwen(prompt);
        assertNotNull(result);
        assertTrue(result.contains("issues"));
    }
}
```

### 集成测试

```bash
# 测试完整流程
curl -X POST http://localhost:8080/api/qwen/rule-review/review \
  -H "Content-Type: application/json" \
  -d '{"prompt": "..."}'
```

## 📊 代码质量

### 编码规范

- 遵循Java命名规范
- 使用有意义的变量名
- 添加详细的注释
- 实现完善的异常处理

### 文档要求

- 添加Javadoc注释
- 更新README文档
- 说明API变更

### 提交规范

- 清晰的commit message
- 相关的PR描述
- 完整的测试覆盖

## 🚀 最佳实践

### 1. 保持兼容性
- 不破坏现有API
- 做好版本管理
- 提供迁移指南

### 2. 安全考虑
- 输入验证
- 隐藏敏感信息
- 完善的权限检查

### 3. 性能优化
- 缓存常用数据
- 异步处理长任务
- 监控性能指标

### 4. 可观测性
- 详细的日志记录
- 监控关键指标
- 完善的错误报告

## 📚 相关文档

| 文档 | 用途 |
|------|------|
| [快速开始](../快速开始) | 快速上手 |
| [功能说明](../功能说明) | 功能详解 |
| [API接口](../API接口) | 接口参考 |
| [实现总结](../实现总结) | 技术细节 |
| [故障排除](../故障排除) | 问题解决 |

## 🎯 常见集成任务

| 任务 | 难度 | 耗时 |
|------|------|------|
| 修改按钮样式 | ⭐ | 10分钟 |
| 添加自定义校验 | ⭐⭐ | 30分钟 |
| 实现结果缓存 | ⭐⭐ | 1小时 |
| 实现审查历史 | ⭐⭐⭐ | 2-3小时 |
| 支持批量审查 | ⭐⭐⭐ | 4-6小时 |

## 💡 开发建议

1. **先读代码，再改代码**
   - 理解现有逻辑
   - 了解依赖关系

2. **小步快走**
   - 逐个小功能实现
   - 每步都进行测试

3. **保存版本**
   - 使用Git管理
   - 及时提交变更

4. **寻求帮助**
   - 查看文档
   - 参考示例代码

## 📖 学习资源

- Spring Boot官方文档
- Qwen API文档
- Java开发最佳实践
- 项目源代码示例

## 🔗 相关链接

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Qwen API Reference](https://dashscope.console.aliyun.com/)
- [Java Coding Standards](https://google.github.io/styleguide/javaguide.html)

---

**版本**: 1.0
**最后更新**: 2025-10-24
**状态**: ✅ 生产就绪

注: 本目录的详细集成指南正在编写中，敬请期待!
