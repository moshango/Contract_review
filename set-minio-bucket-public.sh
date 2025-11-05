#!/bin/bash

# MinIO存储桶策略设置脚本
# 将contract-review存储桶设置为PUBLIC访问

echo "=== 设置MinIO存储桶为PUBLIC访问 ==="
echo "时间: $(date)"
echo ""

# MinIO配置
MINIO_ENDPOINT="http://localhost:9000"
MINIO_ACCESS_KEY="minioadmin"
MINIO_SECRET_KEY="minioadmin"
BUCKET_NAME="contract-review"

# 创建存储桶策略JSON
cat > bucket-policy.json << 'EOF'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": "*",
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::contract-review/*"
    }
  ]
}
EOF

echo "1. 检查MinIO服务状态..."
response=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:9000/minio/health/live)
if [ "$response" = "200" ]; then
    echo "✓ MinIO服务正常运行"
else
    echo "✗ MinIO服务不可访问"
    exit 1
fi

echo ""
echo "2. 检查存储桶是否存在..."
# 使用MinIO客户端检查存储桶
if command -v mc >/dev/null 2>&1; then
    echo "使用MinIO客户端检查存储桶..."
    mc alias set myminio $MINIO_ENDPOINT $MINIO_ACCESS_KEY $MINIO_SECRET_KEY
    mc ls myminio/$BUCKET_NAME >/dev/null 2>&1
    if [ $? -eq 0 ]; then
        echo "✓ 存储桶 $BUCKET_NAME 存在"
    else
        echo "✗ 存储桶 $BUCKET_NAME 不存在，正在创建..."
        mc mb myminio/$BUCKET_NAME
        if [ $? -eq 0 ]; then
            echo "✓ 存储桶 $BUCKET_NAME 创建成功"
        else
            echo "✗ 存储桶 $BUCKET_NAME 创建失败"
            exit 1
        fi
    fi
else
    echo "⚠ MinIO客户端(mc)未安装，跳过存储桶检查"
fi

echo ""
echo "3. 设置存储桶策略为PUBLIC..."
if command -v mc >/dev/null 2>&1; then
    mc anonymous set public myminio/$BUCKET_NAME
    if [ $? -eq 0 ]; then
        echo "✓ 存储桶 $BUCKET_NAME 已设置为PUBLIC访问"
    else
        echo "✗ 设置存储桶策略失败"
        exit 1
    fi
else
    echo "⚠ MinIO客户端(mc)未安装，请手动在控制台设置"
    echo "   访问: http://localhost:9001"
    echo "   找到存储桶: $BUCKET_NAME"
    echo "   设置访问策略为: Public"
fi

echo ""
echo "4. 验证设置结果..."
if command -v mc >/dev/null 2>&1; then
    echo "当前存储桶策略:"
    mc anonymous get myminio/$BUCKET_NAME
fi

echo ""
echo "=== 设置完成 ==="
echo ""
echo "现在可以通过以下方式访问存储桶:"
echo "1. MinIO控制台: http://localhost:9001/browser/$BUCKET_NAME"
echo "2. 直接API访问: http://localhost:9000/$BUCKET_NAME/"
echo ""
echo "注意: PUBLIC访问意味着任何人都可以读取存储桶中的文件"
echo "请确保这是您想要的安全设置"

# 清理临时文件
rm -f bucket-policy.json

