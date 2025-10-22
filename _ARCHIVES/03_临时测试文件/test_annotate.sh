#!/bin/bash

# 设置变量
PARSERESULTID="0f74589e-de44-44cc-8992-48708d1f9a82"
OUTPUTFILE="test_output.docx"

# 创建测试审查JSON文件
cat > review_test.json << 'EOFJSON'
{
  "issues": [
    {
      "clauseId": "c1",
      "anchorId": "anc-c1-304286e3",
      "severity": "HIGH",
      "category": "合同目的",
      "finding": "合同目的表述不够具体，缺少量化指标",
      "suggestion": "建议补充具体的合作目标、时间表和关键成果指标（KPI）",
      "targetText": "本合同旨在明确双方在软件开发、技术交付、知识产权、数据安全、保密义务及后期维护等方面的权利与义务，以确保项目顺利进行。",
      "matchPattern": "EXACT"
    },
    {
      "clauseId": "c3",
      "anchorId": "anc-c3-ea3ec6c4",
      "severity": "MEDIUM",
      "category": "交付物定义",
      "finding": "交付物验收标准不明确，缺少具体的质量要求和测试标准",
      "suggestion": "应补充详细的验收标准，包括功能完整性、性能指标、文档要求等",
      "targetText": "（1）合同解析模块（Word/PDF支持）",
      "matchPattern": "CONTAINS"
    },
    {
      "clauseId": "c7",
      "anchorId": "anc-c7-b36a73c2",
      "severity": "HIGH",
      "category": "付款条款",
      "finding": "付款条件与交付阶段的对应关系不明确，建议明确验收标准",
      "suggestion": "建议补充详细的验收流程、验收标准和延期支付的触发条件",
      "targetText": "- 首付款：合同签订后7个工作日内支付30%；",
      "matchPattern": "EXACT"
    },
    {
      "clauseId": "c16",
      "anchorId": "anc-c16-4d20610c",
      "severity": "HIGH",
      "category": "违约责任",
      "finding": "违约赔偿责任范围过宽，缺少赔偿上限或责任豁免条款",
      "suggestion": "应明确赔偿责任的上限（如年度费用的2倍），并增加不可抗力等豁免条款",
      "targetText": "任一方违反合同约定，须赔偿对方因此造成的全部经济损失。",
      "matchPattern": "EXACT"
    },
    {
      "clauseId": "c20",
      "anchorId": "anc-c20-499e999d",
      "severity": "MEDIUM",
      "category": "争议解决",
      "finding": "仲裁管辖地选择可能不利，建议评估是否需要调整",
      "suggestion": "建议添加协议管辖权条款，或考虑诉讼与仲裁相结合的方式",
      "targetText": "合同履行中如发生争议，双方应友好协商解决；协商不成的，提交广州仲裁委员会仲裁。",
      "matchPattern": "EXACT"
    }
  ]
}
EOFJSON

echo "✅ 审查JSON已创建: review_test.json"

# 执行Annotate请求
echo ""
echo "📤 执行 Annotate 请求..."
echo "参数: parseResultId=$PARSERESULTID"
echo ""

curl -v -X POST "http://localhost:8888/import-result-xml" \
  -H "Content-Type: application/json" \
  -d "{\"parseResultId\":\"$PARSERESULTID\",\"reviewJson\":$(cat review_test.json)}" \
  --output "$OUTPUTFILE" \
  2>&1 | grep -E "HTTP|<|>"

# 检查输出文件
if [ -f "$OUTPUTFILE" ]; then
    SIZE=$(stat -f%z "$OUTPUTFILE" 2>/dev/null || stat -c%s "$OUTPUTFILE" 2>/dev/null || echo "unknown")
    echo ""
    echo "✅ 输出文件已生成: $OUTPUTFILE (大小: $SIZE 字节)"
else
    echo "❌ 输出文件未生成"
fi

