#!/bin/bash

# LLM URL支持测试脚本
# 用于测试各种LLM是否支持通过URL访问文件

set -e

BASE_URL="http://localhost:8080"
QWEN_API="${BASE_URL}/api/qwen"

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}========== LLM URL支持测试 ==========${NC}\n"

# 检查服务是否运行
echo -e "${YELLOW}[Step 1] 检查服务状态${NC}"
if curl -s --connect-timeout 5 "${BASE_URL}/api/qwen/health" > /dev/null; then
    echo -e "${GREEN}✓ 服务正在运行${NC}\n"
else
    echo -e "${RED}✗ 服务未运行，请先启动服务${NC}"
    echo "启动命令: mvn spring-boot:run -DskipTests"
    exit 1
fi

# 测试1: 基础URL访问测试
echo -e "${YELLOW}[Test 1] 基础URL访问测试${NC}"
echo "测试LLM是否能理解并访问URL"

URL_TEST_REQUEST='{
  "messages": [
    {
      "role": "system", 
      "content": "你是一个文件分析助手。请告诉我你是否能够访问和分析通过URL提供的文件内容。"
    },
    {
      "role": "user", 
      "content": "请访问这个URL并告诉我文件内容：https://httpbin.org/json"
    }
  ],
  "model": "qwen-max"
}'

echo "发送请求..."
RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -d "$URL_TEST_REQUEST" \
  "${QWEN_API}/chat")

echo "响应: $RESPONSE"

if [[ $RESPONSE == *"httpbin"* ]] || [[ $RESPONSE == *"json"* ]] || [[ $RESPONSE == *"访问"* ]]; then
    echo -e "${GREEN}✓ LLM可能支持URL访问${NC}\n"
else
    echo -e "${YELLOW}⚠ LLM可能不支持URL访问或无法访问外部URL${NC}\n"
fi

# 测试2: 文件URL格式测试
echo -e "${YELLOW}[Test 2] 文件URL格式测试${NC}"
echo "测试LLM对文件URL的理解"

FILE_URL_TEST_REQUEST='{
  "messages": [
    {
      "role": "system", 
      "content": "你是一个合同审查专家。用户会提供合同文件的URL，你需要分析文件内容。"
    },
    {
      "role": "user", 
      "content": "请分析这个合同文件：http://localhost:9000/contract-review/contract-123.docx"
    }
  ],
  "model": "qwen-max"
}'

echo "发送请求..."
RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -d "$FILE_URL_TEST_REQUEST" \
  "${QWEN_API}/chat")

echo "响应: $RESPONSE"

if [[ $RESPONSE == *"无法访问"* ]] || [[ $RESPONSE == *"不能访问"* ]] || [[ $RESPONSE == *"不支持"* ]]; then
    echo -e "${RED}✗ LLM明确表示不支持URL访问${NC}\n"
elif [[ $RESPONSE == *"docx"* ]] || [[ $RESPONSE == *"文件"* ]]; then
    echo -e "${GREEN}✓ LLM可能支持文件URL访问${NC}\n"
else
    echo -e "${YELLOW}⚠ 无法确定LLM对文件URL的支持情况${NC}\n"
fi

# 测试3: 实际MinIO URL测试（如果MinIO运行）
echo -e "${YELLOW}[Test 3] MinIO URL测试${NC}"
echo "测试LLM是否能访问MinIO URL"

# 检查MinIO是否运行
if curl -s --connect-timeout 5 "http://localhost:9000/minio/health/live" > /dev/null; then
    echo -e "${GREEN}✓ MinIO正在运行${NC}"
    
    MINIO_URL_TEST_REQUEST='{
      "messages": [
        {
          "role": "system", 
          "content": "你是一个文件分析助手。请尝试访问MinIO存储中的文件。"
        },
        {
          "role": "user", 
          "content": "请访问这个MinIO文件URL并分析内容：http://localhost:9000/contract-review/test-file.txt"
        }
      ],
      "model": "qwen-max"
    }'
    
    echo "发送MinIO URL测试请求..."
    RESPONSE=$(curl -s -X POST \
      -H "Content-Type: application/json" \
      -d "$MINIO_URL_TEST_REQUEST" \
      "${QWEN_API}/chat")
    
    echo "响应: $RESPONSE"
    
    if [[ $RESPONSE == *"MinIO"* ]] || [[ $RESPONSE == *"存储"* ]]; then
        echo -e "${GREEN}✓ LLM可能支持MinIO URL访问${NC}\n"
    else
        echo -e "${YELLOW}⚠ LLM对MinIO URL的响应不明确${NC}\n"
    fi
else
    echo -e "${YELLOW}⚠ MinIO未运行，跳过MinIO URL测试${NC}\n"
fi

# 测试4: 错误处理测试
echo -e "${YELLOW}[Test 4] 错误处理测试${NC}"
echo "测试LLM对无效URL的处理"

ERROR_URL_TEST_REQUEST='{
  "messages": [
    {
      "role": "system", 
      "content": "你是一个文件分析助手。"
    },
    {
      "role": "user", 
      "content": "请访问这个无效URL：http://invalid-url-that-does-not-exist.com/file.txt"
    }
  ],
  "model": "qwen-max"
}'

echo "发送无效URL测试请求..."
RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -d "$ERROR_URL_TEST_REQUEST" \
  "${QWEN_API}/chat")

echo "响应: $RESPONSE"

if [[ $RESPONSE == *"无法访问"* ]] || [[ $RESPONSE == *"错误"* ]] || [[ $RESPONSE == *"失败"* ]]; then
    echo -e "${GREEN}✓ LLM正确处理了无效URL${NC}\n"
else
    echo -e "${YELLOW}⚠ LLM对无效URL的响应不明确${NC}\n"
fi

# 总结
echo -e "${BLUE}========== 测试总结 ==========${NC}"
echo -e "${YELLOW}测试结果分析：${NC}"
echo "1. 如果LLM能够访问外部URL并返回内容，说明支持URL访问"
echo "2. 如果LLM明确表示无法访问URL，说明不支持URL访问"
echo "3. 如果LLM响应模糊，需要进一步测试"

echo -e "\n${YELLOW}建议的MinIO集成方案：${NC}"
echo "1. 如果LLM支持URL访问 → 使用方案一（最小改动方案）"
echo "2. 如果LLM不支持URL访问 → 使用方案二（混合存储方案）"
echo "3. 如果不确定 → 先实现方案二，然后逐步测试方案一"

echo -e "\n${GREEN}========== 测试完成 ==========${NC}"

