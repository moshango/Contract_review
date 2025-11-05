#!/bin/bash

# 快速LLM URL支持测试
# 简化版本，快速判断LLM是否支持URL访问

BASE_URL="http://localhost:8080"
QWEN_API="${BASE_URL}/api/qwen"

echo "🔍 快速测试LLM URL支持..."

# 检查服务状态
if ! curl -s --connect-timeout 5 "${QWEN_API}/health" > /dev/null; then
    echo "❌ 服务未运行，请先启动服务"
    echo "启动命令: mvn spring-boot:run -DskipTests"
    exit 1
fi

echo "✅ 服务正在运行"

# 发送测试请求
echo "📤 发送URL访问测试请求..."

RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {
        "role": "user", 
        "content": "请访问这个URL并告诉我内容：https://httpbin.org/json"
      }
    ],
    "model": "qwen-max"
  }' \
  "${QWEN_API}/chat")

echo "📥 收到响应："
echo "$RESPONSE" | head -c 200
echo "..."

# 分析响应
if [[ $RESPONSE == *"httpbin"* ]] || [[ $RESPONSE == *"json"* ]] || [[ $RESPONSE == *"访问"* ]]; then
    echo ""
    echo "✅ LLM可能支持URL访问"
    echo "💡 推荐方案：方案一（最小改动方案）"
elif [[ $RESPONSE == *"无法访问"* ]] || [[ $RESPONSE == *"不能访问"* ]] || [[ $RESPONSE == *"不支持"* ]]; then
    echo ""
    echo "❌ LLM不支持URL访问"
    echo "💡 推荐方案：方案二（混合存储方案）"
else
    echo ""
    echo "⚠️  无法确定LLM的URL支持情况"
    echo "💡 推荐方案：方案二（混合存储方案）"
fi

echo ""
echo "🔧 下一步：根据推荐方案开始MinIO集成"

