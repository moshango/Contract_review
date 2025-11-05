#!/bin/bash

# MinIO服务状态验证脚本
echo "=== MinIO服务状态验证 ==="
echo "时间: $(date)"
echo ""

# 检查MinIO容器状态
echo "1. 检查MinIO容器状态..."
docker ps | grep minio
if [ $? -eq 0 ]; then
    echo "✓ MinIO容器正在运行"
else
    echo "✗ MinIO容器未运行"
    exit 1
fi

echo ""
echo "2. 检查MinIO服务健康状态..."
response=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:9000/minio/health/live)
if [ "$response" = "200" ]; then
    echo "✓ MinIO服务健康检查通过 (HTTP $response)"
else
    echo "✗ MinIO服务健康检查失败 (HTTP $response)"
fi

echo ""
echo "3. 检查MinIO控制台..."
response=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:9001)
if [ "$response" = "200" ]; then
    echo "✓ MinIO控制台可访问 (HTTP $response)"
    echo "  控制台地址: http://localhost:9001"
    echo "  用户名: minioadmin"
    echo "  密码: minioadmin"
else
    echo "✗ MinIO控制台不可访问 (HTTP $response)"
fi

echo ""
echo "4. 测试应用MinIO状态接口..."
if command -v curl >/dev/null 2>&1; then
    response=$(curl -s http://localhost:8080/api/minio/status 2>/dev/null)
    if [ $? -eq 0 ]; then
        echo "✓ 应用MinIO状态接口可访问"
        echo "  响应: $response"
    else
        echo "✗ 应用MinIO状态接口不可访问"
        echo "  请确保Spring Boot应用正在运行"
    fi
else
    echo "⚠ curl命令不可用，跳过应用接口测试"
fi

echo ""
echo "=== 验证完成 ==="
echo ""
echo "如果所有检查都通过，MinIO服务已准备就绪！"
echo "现在可以："
echo "1. 重启Spring Boot应用以加载MinIO配置"
echo "2. 测试文件上传到MinIO功能"
echo "3. 访问MinIO控制台管理文件"

