#!/bin/bash

# 简单的编译测试脚本
echo "=== MinIO集成编译测试 ==="
echo "时间: $(date)"
echo ""

# 检查Java文件是否存在
echo "1. 检查MinIO相关文件..."
if [ -f "src/main/java/com/example/Contract_review/config/MinioConfig.java" ]; then
    echo "✓ MinioConfig.java 存在"
else
    echo "✗ MinioConfig.java 不存在"
fi

if [ -f "src/main/java/com/example/Contract_review/service/MinioFileService.java" ]; then
    echo "✓ MinioFileService.java 存在"
else
    echo "✗ MinioFileService.java 不存在"
fi

if [ -f "src/main/java/com/example/Contract_review/controller/MinioStatusController.java" ]; then
    echo "✓ MinioStatusController.java 存在"
else
    echo "✗ MinioStatusController.java 不存在"
fi

echo ""
echo "2. 检查pom.xml中的MinIO依赖..."
if grep -q "minio" pom.xml; then
    echo "✓ MinIO依赖已添加"
else
    echo "✗ MinIO依赖未找到"
fi

echo ""
echo "3. 检查application.properties中的MinIO配置..."
if grep -q "minio.enabled" src/main/resources/application.properties; then
    echo "✓ MinIO配置已添加"
else
    echo "✗ MinIO配置未找到"
fi

echo ""
echo "=== 编译测试完成 ==="
echo "如果所有检查都通过，说明MinIO集成文件已正确创建"
echo "请运行 'mvn clean compile' 进行实际编译测试"

