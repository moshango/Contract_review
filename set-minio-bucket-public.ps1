# MinIO存储桶策略设置脚本 (PowerShell版本)
# 将contract-review存储桶设置为PUBLIC访问

Write-Host "=== 设置MinIO存储桶为PUBLIC访问 ===" -ForegroundColor Green
Write-Host "时间: $(Get-Date)" -ForegroundColor Gray
Write-Host ""

# MinIO配置
$MINIO_ENDPOINT = "http://localhost:9000"
$MINIO_ACCESS_KEY = "minioadmin"
$MINIO_SECRET_KEY = "minioadmin"
$BUCKET_NAME = "contract-review"

Write-Host "1. 检查MinIO服务状态..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:9000/minio/health/live" -UseBasicParsing -TimeoutSec 5
    if ($response.StatusCode -eq 200) {
        Write-Host "✓ MinIO服务正常运行" -ForegroundColor Green
    } else {
        Write-Host "✗ MinIO服务响应异常 (HTTP $($response.StatusCode))" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "✗ MinIO服务不可访问: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "2. 检查MinIO控制台访问..." -ForegroundColor Yellow
try {
    $consoleResponse = Invoke-WebRequest -Uri "http://localhost:9001" -UseBasicParsing -TimeoutSec 5
    if ($consoleResponse.StatusCode -eq 200) {
        Write-Host "✓ MinIO控制台可访问" -ForegroundColor Green
    } else {
        Write-Host "✗ MinIO控制台不可访问 (HTTP $($consoleResponse.StatusCode))" -ForegroundColor Red
    }
} catch {
    Write-Host "✗ MinIO控制台不可访问: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "3. 创建存储桶策略JSON..." -ForegroundColor Yellow
$bucketPolicy = @"
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": "*",
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::$BUCKET_NAME/*"
    }
  ]
}
"@

# 保存策略到临时文件
$policyFile = "bucket-policy.json"
$bucketPolicy | Out-File -FilePath $policyFile -Encoding UTF8
Write-Host "✓ 存储桶策略JSON已创建" -ForegroundColor Green

Write-Host ""
Write-Host "4. 使用curl设置存储桶策略..." -ForegroundColor Yellow
try {
    # 使用curl设置存储桶策略
    $curlCommand = "curl -X PUT `"$MINIO_ENDPOINT/$BUCKET_NAME?policy`" -H `"Content-Type: application/json`" -d `"$bucketPolicy`""
    Write-Host "执行命令: $curlCommand" -ForegroundColor Gray
    
    # 由于PowerShell的curl别名问题，我们使用Invoke-WebRequest
    $headers = @{
        "Content-Type" = "application/json"
    }
    
    $putResponse = Invoke-WebRequest -Uri "$MINIO_ENDPOINT/$BUCKET_NAME?policy" -Method PUT -Body $bucketPolicy -Headers $headers -UseBasicParsing -TimeoutSec 10
    if ($putResponse.StatusCode -eq 200 -or $putResponse.StatusCode -eq 204) {
        Write-Host "✓ 存储桶策略设置成功" -ForegroundColor Green
    } else {
        Write-Host "✗ 存储桶策略设置失败 (HTTP $($putResponse.StatusCode))" -ForegroundColor Red
    }
} catch {
    Write-Host "✗ 设置存储桶策略时出错: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "这可能是由于认证问题，请使用Web控制台手动设置" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "5. 验证设置..." -ForegroundColor Yellow
try {
    $getResponse = Invoke-WebRequest -Uri "$MINIO_ENDPOINT/$BUCKET_NAME?policy" -UseBasicParsing -TimeoutSec 5
    if ($getResponse.StatusCode -eq 200) {
        Write-Host "✓ 存储桶策略验证成功" -ForegroundColor Green
        Write-Host "策略内容:" -ForegroundColor Gray
        Write-Host $getResponse.Content -ForegroundColor White
    } else {
        Write-Host "✗ 无法验证存储桶策略 (HTTP $($getResponse.StatusCode))" -ForegroundColor Red
    }
} catch {
    Write-Host "✗ 验证存储桶策略时出错: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== 设置完成 ===" -ForegroundColor Green
Write-Host ""
Write-Host "现在可以通过以下方式访问存储桶:" -ForegroundColor Cyan
Write-Host "1. MinIO控制台: http://localhost:9001/browser/$BUCKET_NAME" -ForegroundColor White
Write-Host "2. 直接API访问: http://localhost:9000/$BUCKET_NAME/" -ForegroundColor White
Write-Host ""
Write-Host "注意: PUBLIC访问意味着任何人都可以读取存储桶中的文件" -ForegroundColor Yellow
Write-Host "请确保这是您想要的安全设置" -ForegroundColor Yellow

# 清理临时文件
if (Test-Path $policyFile) {
    Remove-Item $policyFile
    Write-Host "✓ 临时文件已清理" -ForegroundColor Gray
}

