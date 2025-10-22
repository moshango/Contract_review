@echo off
REM 恢复应用到端口8080的Windows批处理脚本

echo.
echo ====================================================
echo 合同审查系统 - 恢复到8080端口
echo ====================================================
echo.

REM 步骤1: 关闭所有Java进程
echo [步骤1] 关闭所有Java进程...
taskkill /F /IM java.exe >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo ✅ Java进程已关闭
) else (
    echo ⚠️  无Java进程运行或无法关闭，继续...
)

REM 等待端口释放
echo.
echo [步骤2] 等待3秒以释放端口...
timeout /t 3 /nobreak

REM 步骤2: 验证端口
echo.
echo [步骤3] 验证端口状态...
netstat -ano | findstr "8080" >nul
if %ERRORLEVEL% EQU 0 (
    echo ⚠️  端口8080仍被占用，将强制释放...
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr "8080"') do (
        taskkill /PID %%a /F >nul 2>&1
    )
    timeout /t 2 /nobreak
) else (
    echo ✅ 端口8080已释放
)

REM 步骤3: 启动应用
echo.
echo [步骤4] 启动应用到端口8080...
echo 当前目录: %cd%
echo JAR文件位置: %cd%\target\app.jar
echo.

cd /d "%~dp0"
if exist "target\app.jar" (
    echo ✅ 找到app.jar，即将启动...
    echo.
    echo 启动命令: java -jar target\app.jar
    echo.
    java -jar target\app.jar
) else if exist "target\Contract_review-0.0.1-SNAPSHOT.jar" (
    echo ⚠️  app.jar不存在，使用Contract_review-0.0.1-SNAPSHOT.jar...
    echo.
    echo 启动命令: java -jar target\Contract_review-0.0.1-SNAPSHOT.jar
    echo.
    java -jar target\Contract_review-0.0.1-SNAPSHOT.jar
) else (
    echo ❌ 找不到JAR文件！
    echo 请先运行: mvn clean package -DskipTests
    pause
    exit /b 1
)

pause
