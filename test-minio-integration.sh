#!/bin/bash

# MinIO集成测试脚本
# 用于测试MinIO服务是否正常工作

echo "=== MinIO集成测试脚本 ==="
echo "时间: $(date)"
echo ""

# 检查MinIO服务状态
echo "1. 检查MinIO服务状态..."
curl -s http://localhost:8080/api/minio/status | jq '.' 2>/dev/null || echo "MinIO状态检查失败"

echo ""
echo "2. 检查MinIO配置..."
curl -s http://localhost:8080/api/minio/config | jq '.' 2>/dev/null || echo "MinIO配置检查失败"

echo ""
echo "3. 测试一键审查功能（MinIO集成）..."
echo "请手动上传一个合同文件进行测试，或使用以下curl命令："
echo ""
echo "curl -X POST http://localhost:8080/api/qwen/rule-review/one-click-review \\"
echo "  -F 'file=@测试合同_综合测试版.docx' \\"
echo "  -F 'stance=neutral' \\"
echo "  -v"
echo ""
echo "注意：检查响应头中的 X-Minio-Url 字段"

echo ""
echo "=== 测试完成 ==="

