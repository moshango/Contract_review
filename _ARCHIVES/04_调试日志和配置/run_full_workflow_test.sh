#!/bin/bash

# 完整工作流自测试脚本
# 用于验证 8080 ChatGPT 集成模块的批注功能

set -e

echo "=========================================="
echo "开始完整工作流自测试"
echo "=========================================="

TEST_FILE="D:/工作/合同审查系统开发/spring boot/Contract_review/测试合同_综合测试版.docx"
BASE_URL="http://localhost:8080"

if [ ! -f "$TEST_FILE" ]; then
    echo "❌ 测试文件不存在: $TEST_FILE"
    exit 1
fi

echo "✅ 测试文件存在"
echo "📁 文件: $TEST_FILE"
echo ""

# ============================================================
# 步骤 1: Parse 阶段 - 生成 Prompt 并获取锚点
# ============================================================
echo "========== 步骤 1: Parse 阶段 ==========="
echo "调用 /generate-prompt 端点..."
echo ""

PARSE_RESPONSE=$(curl -s -X POST "${BASE_URL}/chatgpt/generate-prompt" \
  -F "file=@$TEST_FILE" \
  -F "contractType=通用合同" \
  -F "anchors=generate")

echo "📋 Parse 响应:"
echo "$PARSE_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$PARSE_RESPONSE"
echo ""

# 提取关键信息
PARSE_ID=$(echo "$PARSE_RESPONSE" | grep -o '"parseResultId":"[^"]*' | cut -d'"' -f4)
CLAUSE_COUNT=$(echo "$PARSE_RESPONSE" | grep -o '"clauseCount":[0-9]*' | cut -d':' -f2)

if [ -z "$PARSE_ID" ]; then
    echo "❌ 未能获取 parseResultId"
    exit 1
fi

echo "✅ Parse 成功"
echo "   - parseResultId: $PARSE_ID"
echo "   - 条款数: $CLAUSE_COUNT"
echo ""

# ============================================================
# 步骤 2: 生成测试审查结果 JSON
# ============================================================
echo "========== 步骤 2: 生成测试审查结果 ==========="

# 构建测试的审查结果 JSON
REVIEW_JSON='{
  "issues": [
    {
      "clauseId": "c1",
      "anchorId": "anc-c1-304286e3",
      "severity": "HIGH",
      "category": "合同目的",
      "finding": "需要更明确的合作范围定义",
      "suggestion": "建议在合同目的中明确列出所有合作项目的具体范围和目标",
      "targetText": "本合同旨在明确双方在软件开发、技术交付、知识产权、数据安全、保密义务及后期维护等方面的权利与义务，以确保项目顺利进行。",
      "matchPattern": "EXACT"
    },
    {
      "clauseId": "c3",
      "anchorId": "anc-c3-ea3ec6c4",
      "severity": "MEDIUM",
      "category": "交付物定义",
      "finding": "前端展示界面缺少具体的技术规格要求",
      "suggestion": "应明确前端展示的必需功能模块、性能要求（如响应时间）和兼容性要求",
      "targetText": "（3）前端展示与标注界面（Web版）",
      "matchPattern": "CONTAINS"
    },
    {
      "clauseId": "c5",
      "severity": "HIGH",
      "category": "付款条款",
      "finding": "首付款比例偏低，风险较大",
      "suggestion": "建议提高首付款比例至 40-50%，以平衡双方风险",
      "targetText": "首付款：合同签订后7个工作日内支付30%",
      "matchPattern": "CONTAINS"
    },
    {
      "clauseId": "c10",
      "severity": "MEDIUM",
      "category": "知识产权",
      "finding": "知识产权所有权分配不够平衡",
      "suggestion": "建议明确区分通用技术和项目特定技术的所有权归属",
      "targetText": "所有项目成果的知识产权归甲方所有",
      "matchPattern": "CONTAINS"
    },
    {
      "clauseId": "c15",
      "severity": "HIGH",
      "category": "违约责任",
      "finding": "违约责任条款过于宽泛，缺少具体的量化标准",
      "suggestion": "建议明确违约赔偿的计算方式和上限金额",
      "targetText": "任一方违反合同约定，须赔偿对方因此造成的全部经济损失",
      "matchPattern": "CONTAINS"
    }
  ],
  "summary": {
    "totalIssues": 5,
    "highRisk": 3,
    "mediumRisk": 2,
    "lowRisk": 0,
    "recommendation": "该合同在整体结构上较为完整，但在合作范围定义、付款风险管理和违约责任量化等方面仍有改进空间。建议重点关注高风险条款，通过进一步谈判和修改来平衡双方权益。"
  }
}'

echo "✅ 测试审查结果生成完成"
echo "   - 问题数: 5"
echo "   - 高风险: 3"
echo "   - 中风险: 2"
echo ""

# ============================================================
# 步骤 3: Annotate 阶段 - 导入审查结果并生成批注
# ============================================================
echo "========== 步骤 3: Annotate 阶段 ==========="
echo "调用 /import-result-xml 端点..."
echo ""

ANNOTATE_RESPONSE=$(curl -s -X POST "${BASE_URL}/chatgpt/import-result-xml?parseResultId=${PARSE_ID}&anchorStrategy=preferAnchor&cleanupAnchors=false" \
  -F "chatgptResponse=$REVIEW_JSON" \
  -o annotated_output.docx)

if [ -f "annotated_output.docx" ]; then
    echo "✅ Annotate 成功"
    FILE_SIZE=$(ls -lh annotated_output.docx | awk '{print $5}')
    echo "   - 输出文件: annotated_output.docx"
    echo "   - 文件大小: $FILE_SIZE"
    echo ""
else
    echo "❌ Annotate 失败"
    exit 1
fi

# ============================================================
# 步骤 4: 验证结果
# ============================================================
echo "========== 步骤 4: 验证结果 ==========="
echo "检查输出文件..."
echo ""

if [ -f "annotated_output.docx" ]; then
    echo "✅ 批注文档已生成"
    echo "   文件位置: $(pwd)/annotated_output.docx"
    echo ""
    echo "📋 工作流完成！"
    echo ""
    echo "下一步建议:"
    echo "1. 打开 annotated_output.docx 文件检查批注"
    echo "2. 验证批注位置是否正确"
    echo "3. 查看后台日志中的关键信息"
else
    echo "❌ 输出文件生成失败"
    exit 1
fi

echo ""
echo "=========================================="
echo "✅ 完整工作流自测试结束"
echo "=========================================="
