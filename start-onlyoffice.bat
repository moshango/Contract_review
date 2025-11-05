@echo off
echo ========================================
echo OnlyOffice Document Server 快速部署
echo ========================================
echo.

echo 检查Docker是否运行...
docker --version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo 错误: Docker未安装或未运行
    echo 请先安装Docker Desktop
    pause
    exit /b 1
)

echo Docker已就绪
echo.

echo 启动OnlyOffice Document Server...
docker-compose -f docker-compose-onlyoffice.yml up -d

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ✅ OnlyOffice Document Server启动成功！
    echo.
    echo 服务地址: http://localhost:8080
    echo 健康检查: http://localhost:8080/healthcheck
    echo.
    echo 等待服务完全启动（约30秒）...
    timeout /t 30 /nobreak >nul
    
    echo.
    echo 检查服务状态...
    curl -s http://localhost:8080/healthcheck >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        echo ✅ 服务运行正常！
    ) else (
        echo ⚠️ 服务可能还在启动中，请稍等片刻
    )
) else (
    echo ❌ 启动失败
)

echo.
echo 查看服务日志: docker-compose -f docker-compose-onlyoffice.yml logs -f
echo 停止服务: docker-compose -f docker-compose-onlyoffice.yml down
echo.
pause
