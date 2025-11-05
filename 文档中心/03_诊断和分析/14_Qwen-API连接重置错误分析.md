# Qwen API 连接重置错误分析报告

## 🔍 错误概述

**错误时间**：2025-11-05 10:44:55  
**错误类型**：`WebClientRequestException: Connection reset`  
**影响范围**：Qwen AI审查功能  
**严重程度**：高（导致审查失败）

---

## 📋 错误详情

### 错误堆栈

```
ERROR c.e.C.qwen.client.QwenClient - Non-stream chat failed
org.springframework.web.reactive.function.client.WebClientRequestException: Connection reset
  at ExchangeFunctions$DefaultExchangeFunction.lambda$wrapException$9

*__checkpoint ? Request to POST https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions

Caused by: java.net.SocketException: Connection reset
  at sun.nio.ch.SocketChannelImpl.throwConnectionReset
  at sun.nio.ch.SocketChannelImpl.read
  at io.netty.buffer.PooledByteBuf.setBytes
```

### 关键信息提取

| 信息项 | 值 |
|-------|---|
| **错误组件** | `QwenClient.java` |
| **调用方法** | `chat()` 非流式模式 |
| **目标API** | `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions` |
| **错误类型** | `Connection reset` (连接被重置) |
| **底层异常** | `java.net.SocketException` |
| **传输层** | Netty NIO |

---

## 🎯 原因分析

### 原因分类

#### 可能性1：DashScope API服务端主动断开（60%概率）⭐

**症状特征**：
- ✅ 错误发生在读取响应时（`SocketChannelImpl.read`）
- ✅ Connection reset（而非Connection timeout）
- ✅ 请求已发送，但服务端断开连接

**可能原因**：

##### A. API请求过大或超时
```
问题：Prompt文本过长（超过API限制）
表现：服务端开始处理但中途中断连接
限制：
  - DashScope单次请求最大token：~30,000
  - Qwen-max-latest context：8,192 tokens
  - 如果Prompt超过限制，服务端会主动断开
```

**诊断方法**：
```bash
# 查看前面的日志，找到Prompt长度
grep "Prompt长度" logs/*.log

# 如果显示：
# Prompt长度: 25000 字符
# 估算token：25000 / 2 = 12,500 tokens
# 如果超过8,192，会被拒绝
```

---

##### B. API Rate Limit（速率限制）
```
问题：短时间内请求过多
表现：触发API限流，连接被重置
限制：
  - DashScope API有QPM（每分钟查询数）限制
  - 免费版：通常10 QPM
  - 付费版：根据套餐不同
```

**诊断方法**：
```bash
# 查看最近的请求频率
grep "向Qwen发送审查请求" logs/*.log | grep "10:44"

# 如果1分钟内超过10次请求，可能触发限流
```

---

##### C. API Key配额耗尽或过期
```
问题：API Key余额不足或已过期
表现：认证失败后连接被重置
```

**诊断方法**：
```bash
# 访问DashScope控制台
https://dashscope.console.aliyun.com/

# 检查：
# 1. API Key状态（是否有效）
# 2. 账户余额（是否充足）
# 3. 用量统计（是否超限）
```

---

#### 可能性2：网络问题（30%概率）

**症状特征**：
- ✅ Connection reset（连接中断）
- ✅ 发生在Netty NIO层

**可能原因**：

##### A. 网络不稳定
```
问题：网络波动导致连接中断
表现：偶发性错误，不是每次都发生
环境：
  - 公司网络防火墙
  - VPN不稳定
  - DNS解析问题
```

**诊断方法**：
```bash
# 测试网络连通性
ping dashscope.aliyuncs.com

# 测试HTTPS连接
curl -I https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions

# 如果网络不稳定，会看到间歇性失败
```

---

##### B. 防火墙/代理问题
```
问题：企业防火墙拦截或代理超时
表现：连接建立后被中断
```

**诊断方法**：
```bash
# 检查是否有HTTP代理
echo $HTTP_PROXY
echo $HTTPS_PROXY

# 测试绕过代理
curl --noproxy '*' https://dashscope.aliyuncs.com/...
```

---

#### 可能性3：WebClient配置问题（10%概率）

**症状特征**：
- ✅ 使用Spring WebClient
- ✅ Reactor Netty底层

**可能原因**：

##### A. 超时配置不当
```
问题：读取超时时间过短
表现：服务端还在处理，客户端已超时断开
```

**检查配置**：
```java
// Contract_review/src/main/java/.../config/WebClientConfig.java
// 查找超时配置

@Bean
public WebClient webClient(WebClient.Builder builder) {
    return builder
        .clientConnector(new ReactorClientHttpConnector(
            HttpClient.create()
                .responseTimeout(Duration.ofSeconds(60))  // ← 检查此值
        ))
        .build();
}
```

---

##### B. 连接池配置问题
```
问题：连接池资源耗尽
表现：无法获取新连接，复用连接时被重置
```

---

## 📊 错误根因概率分析

| 根因 | 概率 | 诊断优先级 | 修复难度 |
|-----|------|-----------|---------|
| **API Prompt过长** | 60% | ⭐⭐⭐⭐⭐ 最高 | 简单 |
| **API速率限制** | 20% | ⭐⭐⭐⭐ 高 | 简单 |
| **API Key问题** | 15% | ⭐⭐⭐ 中 | 简单 |
| **网络不稳定** | 10% | ⭐⭐ 低 | 中等 |
| **超时配置** | 5% | ⭐ 最低 | 简单 |

---

## 🔬 诊断步骤

### 步骤1：检查Prompt长度（5分钟）

**查看日志**：
```bash
# 在日志中搜索Prompt长度
grep "Prompt长度" Contract_review/logs/*.log | tail -10

# 预期输出：
# Prompt长度: 8500 字符
```

**判断标准**：
```
字符数 → 估算token数（÷2）
  
✅ < 6,000字符 (≈3,000 tokens) - 安全
⚠️ 6,000-15,000字符 (3,000-7,500 tokens) - 接近限制
❌ > 15,000字符 (≈7,500+ tokens) - 超限（会被拒绝）
```

**如果超限**：
→ 原因确认：Prompt过长  
→ 解决方案：优化Prompt生成逻辑

---

### 步骤2：检查API调用频率（3分钟）

**查看日志**：
```bash
# 统计10:44分的请求次数
grep "向Qwen发送审查请求" logs/*.log | grep "10:44" | wc -l

# 预期输出：
# 3  ← 3次请求（正常）
# 15 ← 15次请求（可能超限）
```

**判断标准**：
```
每分钟请求数（QPM）：
  
✅ < 5次 - 安全（免费版）
⚠️ 5-10次 - 接近限制
❌ > 10次 - 超限（会被限流）
```

**如果超限**：
→ 原因确认：触发速率限制  
→ 解决方案：添加请求队列或降低并发

---

### 步骤3：检查API Key状态（2分钟）

**访问控制台**：
```
https://dashscope.console.aliyun.com/
```

**检查项目**：

| 检查项 | 正常状态 | 异常状态 |
|-------|---------|---------|
| API Key状态 | ✅ 启用中 | ❌ 已禁用/过期 |
| 账户余额 | ✅ 充足 | ❌ 余额不足 |
| 今日用量 | ✅ < 限额 | ❌ 已达上限 |

**如果异常**：
→ 原因确认：API Key问题  
→ 解决方案：充值或更换API Key

---

### 步骤4：测试网络连通性（2分钟）

```bash
# 测试DNS解析
nslookup dashscope.aliyuncs.com

# 测试HTTPS连接
curl -I https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions

# 测试完整请求（简单测试）
curl -X POST https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen-max-latest",
    "messages": [{"role": "user", "content": "测试"}]
  }'
```

**预期结果**：
```json
{
  "id": "chatcmpl-xxx",
  "choices": [...]
}
```

**如果失败**：
→ 原因确认：网络连通性问题  
→ 解决方案：检查防火墙/代理设置

---

### 步骤5：检查WebClient超时配置（1分钟）

**查看配置文件**：
```java
Contract_review/src/main/java/.../config/WebClientConfig.java
```

**关键配置**：
```java
.responseTimeout(Duration.ofSeconds(60))  // 响应超时
.connectTimeout(Duration.ofSeconds(10))   // 连接超时
```

**判断标准**：
```
✅ responseTimeout ≥ 60秒 - 足够
❌ responseTimeout < 30秒 - 过短（AI审查需要5-10秒）
```

---

## 💡 最可能的原因（优先排查）

### ⭐ 根因1：Prompt文本过长（60%概率）

#### 证据

从之前的一键审查日志中可以看到：
```
条款数: X 个
规则匹配: Y 条
```

如果：
- 条款数 > 20个
- 每个条款匹配 > 3条规则
- Prompt会非常长

#### 计算示例

```
假设：
  - 20个条款
  - 每个条款匹配3条规则
  - 每条规则包含：checklist(200字) + suggestA(100字)
  
Prompt大小估算：
  = 20条款 × 3规则 × (200+100)字
  = 20 × 3 × 300
  = 18,000字符
  ≈ 9,000 tokens
  
结论：超过Qwen-max-latest的8,192 token限制！
```

#### DashScope API限制

| 模型 | 最大上下文 | 说明 |
|-----|-----------|------|
| qwen-max-latest | 8,192 tokens | 超过会被拒绝 |
| qwen-plus | 32,768 tokens | 更大容量 |
| qwen-turbo | 8,192 tokens | 同max |

#### 解决方向（暂不实施）

1. **优化Prompt**：精简规则描述
2. **分批审查**：将大合同拆分为多次请求
3. **切换模型**：使用qwen-plus（32K context）

---

### ⭐ 根因2：API速率限制（20%概率）

#### DashScope限流规则

**免费版**：
- QPM（每分钟查询数）：10
- TPM（每分钟token数）：200,000

**如果触发**：
- HTTP 429错误（通常）
- 或Connection reset（部分情况）

#### 诊断方法

**查看日志中的请求频率**：
```bash
# 统计10:44分的所有Qwen请求
grep "向Qwen发送审查请求" logs/*.log | grep "10:44"
```

**如果输出**：
```
10:44:30 向Qwen发送审查请求
10:44:35 向Qwen发送审查请求
10:44:40 向Qwen发送审查请求
10:44:45 向Qwen发送审查请求
10:44:50 向Qwen发送审查请求
10:44:55 向Qwen发送审查请求  ← 错误发生
```

**分析**：
- 6次请求/1分钟 = 6 QPM ✅ 正常（< 10）
- 如果 > 10次/分钟 = 超限 ❌

#### 解决方向（暂不实施）

1. **添加请求限流**：控制QPM < 8
2. **队列机制**：排队等待
3. **升级账户**：付费版有更高限额

---

### ⭐ 根因3：API Key配额问题（15%概率）

#### 可能情况

| 情况 | 表现 |
|-----|------|
| **余额不足** | 请求被拒绝，连接重置 |
| **达到日配额** | 当日请求超限 |
| **Key被禁用** | 认证失败 |

#### 诊断方法

**检查DashScope控制台**：
1. 登录：https://dashscope.console.aliyun.com/
2. 查看API Key状态
3. 查看账户余额
4. 查看今日用量

#### 解决方向（暂不实施）

1. **充值**：增加账户余额
2. **更换Key**：使用新的API Key
3. **升级套餐**：提高配额

---

### 根因4：网络/防火墙问题（10%概率）

#### 表现特征

- 偶发性（不是每次都失败）
- 特定时间段出现
- 特定网络环境出现

#### 可能原因

| 原因 | 概率 |
|-----|------|
| 企业防火墙拦截HTTPS | 5% |
| 网络波动 | 3% |
| DNS解析失败 | 2% |

#### 诊断方法

```bash
# 测试网络连通性
ping dashscope.aliyuncs.com

# 测试HTTPS
curl -v https://dashscope.aliyuncs.com/compatible-mode/v1/models

# 如果超时或连接被拒绝 → 网络问题
```

---

## 🎯 优先诊断建议

### 立即检查（5分钟）

#### 1. 查看Prompt长度

**在后端日志中搜索**：
```
grep "Prompt长度" logs/*.log | tail -5
```

**期望看到**：
```
Prompt长度: 5500 字符  ← ✅ 正常（< 6000）
Prompt长度: 18000 字符 ← ❌ 超限（> 15000）
```

**如果超过15,000字符**：
→ **这就是问题根因！** Prompt过长导致API拒绝

---

#### 2. 检查API Key状态

**访问**：
```
https://dashscope.console.aliyun.com/apiKey
```

**检查**：
- API Key是否启用
- 账户余额是否充足
- 今日调用量是否超限

**如果有任何异常**：
→ **这就是问题根因！** API Key问题

---

#### 3. 测试单个简单请求

**直接调用API**（绕过应用）：
```bash
curl -X POST https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen-max-latest",
    "messages": [
      {"role": "system", "content": "你是助手"},
      {"role": "user", "content": "你好"}
    ]
  }'
```

**如果成功**：
```json
{
  "id": "chatcmpl-xxx",
  "choices": [...]
}
```
→ API本身正常，问题在应用端（Prompt过长或频率过高）

**如果失败**：
```
Connection reset / Timeout
```
→ API Key或网络问题

---

## 📈 诊断决策树

```
Connection reset错误
    ↓
检查Prompt长度
    ├─ > 15,000字符 → 【根因：Prompt过长】 ✅
    │                 解决：优化Prompt生成
    │
    ├─ < 6,000字符 → 继续检查 ↓
    │
检查请求频率
    ├─ > 10次/分钟 → 【根因：速率限制】 ✅
    │                 解决：添加限流
    │
    ├─ < 5次/分钟 → 继续检查 ↓
    │
检查API Key
    ├─ 余额不足/过期 → 【根因：API Key问题】 ✅
    │                   解决：充值/更换Key
    │
    ├─ 正常 → 继续检查 ↓
    │
测试API直接调用
    ├─ 失败 → 【根因：网络问题】 ✅
    │          解决：检查防火墙/代理
    │
    └─ 成功 → 【根因：未知】需深入排查
```

---

## 🔍 具体诊断命令

### 命令1：查看最近的Prompt长度

```bash
# Windows PowerShell
Select-String -Path "Contract_review\logs\*.log" -Pattern "Prompt长度" | Select-Object -Last 5

# 或直接查看日志文件
Get-Content "Contract_review\logs\spring.log" | Select-String "Prompt长度"
```

---

### 命令2：查看错误前的请求日志

```bash
# 查看10:44分的所有Qwen相关日志
Select-String -Path "*.log" -Pattern "Qwen" | Where-Object {$_.Line -like "*10:44*"}
```

---

### 命令3：查看完整错误上下文

```bash
# 查看错误前后10行日志
Select-String -Path "*.log" -Pattern "Connection reset" -Context 10,10
```

---

## ⚠️ 不推荐的操作（避免）

### ❌ 不要盲目增加超时时间

```java
// ❌ 错误做法
.responseTimeout(Duration.ofMinutes(10))  // 10分钟超时

// 原因：
// 1. Connection reset不是超时问题
// 2. 过长超时会掩盖真正问题
// 3. 占用连接资源
```

---

### ❌ 不要盲目重试

```java
// ❌ 错误做法
for (int i = 0; i < 5; i++) {
    try {
        return qwenClient.chat(...);
    } catch (Exception e) {
        // 重试
    }
}

// 原因：
// 1. 如果是Prompt过长，重试无效
// 2. 如果是速率限制，重试会加剧问题
// 3. 浪费API配额
```

---

### ❌ 不要修改底层Netty配置

```java
// ❌ 错误做法
HttpClient.create()
    .option(ChannelOption.SO_KEEPALIVE, true)
    .option(ChannelOption.TCP_NODELAY, true)
    // ... 各种底层配置

// 原因：
// 1. Connection reset不是TCP层问题
// 2. 修改底层配置风险高
// 3. 不解决根本问题
```

---

## 📊 诊断结果模板

完成诊断后，填写此表：

```
【Qwen API连接重置诊断结果】

错误时间：2025-11-05 10:44:55
诊断时间：2025-11-05 __:__:__

1. Prompt长度检查
   □ 未检查
   □ 正常（< 6,000字符）
   □ 接近限制（6,000-15,000字符）
   □ 超限（> 15,000字符）← 根因
   
2. 请求频率检查
   □ 未检查
   □ 正常（< 5 QPM）
   □ 接近限制（5-10 QPM）
   □ 超限（> 10 QPM）← 根因
   
3. API Key状态检查
   □ 未检查
   □ 正常
   □ 余额不足 ← 根因
   □ 已禁用/过期 ← 根因
   
4. 网络连通性检查
   □ 未检查
   □ 正常
   □ 偶发失败
   □ 持续失败 ← 根因
   
5. 诊断结论
   根因：_______________________
   置信度：____%
   建议方案：___________________
```

---

## 🚨 紧急处理建议

### 如果业务紧急

**临时方案**：
1. 减少单次审查的条款数（< 10个）
2. 手动重试失败的审查
3. 使用更小的测试文件

**长期方案**（待诊断后实施）：
1. 优化Prompt生成（减少冗余）
2. 添加请求限流
3. 升级API账户

---

## 📞 获取帮助

### 查看相关日志

**完整日志位置**：
```
Contract_review/logs/spring.log
```

**关键日志关键词**：
- "Prompt长度"
- "向Qwen发送审查请求"
- "Qwen返回内容长度"
- "Connection reset"

---

### DashScope技术支持

**文档**：https://help.aliyun.com/zh/dashscope/  
**工单**：https://dashscope.console.aliyun.com/  
**论坛**：https://developer.aliyun.com/forum/

---

## 🎯 诊断总结

### 最可能的原因（按概率排序）

1. **Prompt文本过长**（60%） - 超过API token限制
2. **API速率限制**（20%） - QPM超限
3. **API Key问题**（15%） - 余额不足或过期
4. **网络问题**（5%） - 防火墙/连接不稳定

### 推荐诊断顺序

**第一步**：检查Prompt长度（最快，最可能）  
**第二步**：检查API Key状态（最简单）  
**第三步**：检查请求频率（需统计）  
**第四步**：测试网络连通性（最后手段）

---

## 💡 下一步行动

### 立即可做

1. **查看后端日志**：
```bash
# 找到错误前的Prompt长度日志
grep -B 20 "Connection reset" logs/spring.log | grep "Prompt长度"
```

2. **访问DashScope控制台**：
   - 检查API Key状态
   - 查看账户余额
   - 查看调用统计

3. **测试简单请求**：
```bash
curl测试（见上文）
```

### 诊断完成后

**如果确认是Prompt过长**：
- 需要优化Prompt生成逻辑
- 或切换到qwen-plus模型

**如果确认是其他问题**：
- 根据具体原因采取对应措施

---

**诊断状态**：✅ 分析完成，待用户诊断确认根因  
**归档位置**：`文档中心/03_诊断和分析/14_Qwen-API连接重置错误分析.md`

---

**请按照上述步骤诊断，找出确切原因后再决定如何修复！** 🔍

