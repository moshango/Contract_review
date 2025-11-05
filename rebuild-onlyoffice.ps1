# OnlyOffice容器重建脚本
# 使用统一JWT密钥重建OnlyOffice Document Server

Write-Host "正在停止并删除现有OnlyOffice容器..." -ForegroundColor Yellow

# 停止并删除现有容器
docker stop onlyoffice-documentserver 2>$null
docker rm onlyoffice-documentserver 2>$null

Write-Host "正在使用统一JWT密钥重建OnlyOffice容器..." -ForegroundColor Green

# 使用统一JWT密钥重建容器
docker run -d --name onlyoffice-documentserver -p 8082:80 -p 8445:443 -e JWT_ENABLED=true -e JWT_SECRET=ThisIsA_VeryStrong_OnlyOffice_JWT_Secret_Key_2025_1234567890 onlyoffice/documentserver:latest

Write-Host "等待容器启动..." -ForegroundColor Cyan
Start-Sleep -Seconds 10

Write-Host "检查容器状态..." -ForegroundColor Blue
docker ps | findstr onlyoffice

Write-Host "检查容器日志..." -ForegroundColor Magenta
docker logs onlyoffice-documentserver --tail 10

Write-Host "测试健康检查..." -ForegroundColor Green
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8082/healthcheck" -TimeoutSec 10
    Write-Host "健康检查成功: $($response.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host "健康检查失败: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "OnlyOffice容器重建完成！" -ForegroundColor Green
Write-Host "请重启Spring Boot后端服务以使JWT配置生效。" -ForegroundColor Yellow
