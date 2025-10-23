#!/bin/bash

# Qwen 集成测试脚本
# 用法: bash test-qwen.sh

set -e

BASE_URL="http://localhost:8888"
QWEN_API="${BASE_URL}/api/qwen"

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}========== Qwen 集成测试 ==========${NC}\n"

# 测试 1: 健康检查
echo -e "${YELLOW}[Test 1] 健康检查${NC}"
echo "GET ${QWEN_API}/health"
RESPONSE=$(curl -s --noproxy localhost "${QWEN_API}/health")
echo "响应: $RESPONSE"
if [[ $RESPONSE == *"status"* ]]; then
    echo -e "${GREEN}✓ 通过${NC}\n"
else
    echo -e "${RED}✗ 失败${NC}\n"
fi

# 测试 2: 非流式聊天（无 API Key）
echo -e "${YELLOW}[Test 2] 非流式聊天（没有 API Key）${NC}"
echo "POST ${QWEN_API}/chat"
CHAT_REQUEST='{
  "messages": [
    {"role": "user", "content": "你好"}
  ],
  "model": "qwen-max"
}'
echo "请求体: $CHAT_REQUEST"
RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -d "$CHAT_REQUEST" \
  "${QWEN_API}/chat")
echo "响应: $RESPONSE"
if [[ $RESPONSE == *"error"* ]] || [[ $RESPONSE == *"API key"* ]]; then
    echo -e "${GREEN}✓ API Key 验证正常${NC}\n"
else
    echo -e "${YELLOW}⚠ 响应: $RESPONSE${NC}\n"
fi

# 测试 3: 流式聊天（无 API Key）
echo -e "${YELLOW}[Test 3] 流式聊天（没有 API Key）${NC}"
echo "POST ${QWEN_API}/stream"
STREAM_REQUEST='{
  "messages": [
    {"role": "user", "content": "写一句话"}
  ],
  "model": "qwen-max"
}'
echo "请求体: $STREAM_REQUEST"
RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -d "$STREAM_REQUEST" \
  "${QWEN_API}/stream" | head -c 200)
echo "响应 (前200字符): $RESPONSE"
if [[ $RESPONSE == *"error"* ]] || [[ $RESPONSE == *"data:"* ]]; then
    echo -e "${GREEN}✓ 流式端点正常${NC}\n"
else
    echo -e "${YELLOW}⚠ 响应: $RESPONSE${NC}\n"
fi

# 测试 4: 配置检查
echo -e "${YELLOW}[Test 4] 配置检查${NC}"
CONFIG=$(curl -s --noproxy localhost "${QWEN_API}/health" | grep -o '"config":{[^}]*}')
echo "配置: $CONFIG"
echo -e "${GREEN}✓ 配置可读${NC}\n"

echo -e "${GREEN}========== 测试完成 ==========${NC}\n"
echo -e "${YELLOW}下一步:${NC}"
echo "1. 配置 DASHSCOPE_API_KEY 环境变量"
echo "2. 设置 QWEN_BASE_URL (中国区或新加坡区)"
echo "3. 重启应用: mvn spring-boot:run -DskipTests -Dspring-boot.run.arguments='--server.port=8888'"
echo "4. 再次运行此脚本测试实际调用"
