@echo off
REM ============================================================
REM 合同审查系统 - 快速修复脚本（解决文件锁问题）
REM ============================================================

echo.
echo ============================================================
echo 合同审查系统 - 快速修复和启动
echo ============================================================
echo.

setlocal enabledelayedexpansion

REM ============================================================
REM 第1步: 直接通过 Windows API 强制关闭所有 Java 进程
REM ============================================================

echo [步骤1] 强制关闭所有 Java 进程...

REM 尝试所有可能的方法
taskkill /F /IM java.exe /T 2>nul
timeout /t 1 /nobreak
taskkill /F /IM java.exe 2>nul
timeout /t 1 /nobreak
wmic process where name="java.exe" delete /nointeractive 2>nul
timeout /t 1 /nobreak

echo ✅ 进程清理完成
echo.

REM ============================================================
REM 第2步: 删除 target 目录中的所有 JAR 文件
REM 这将强制重新生成
REM ============================================================

echo [步骤2] 清理 target 目录中的 JAR 文件...

cd /d "%~dp0"

if exist "target\Contract_review-0.0.1-SNAPSHOT.jar.original" (
    del /F /Q "target\Contract_review-0.0.1-SNAPSHOT.jar.original" 2>nul
)

if exist "target\Contract_review-0.0.1-SNAPSHOT.jar" (
    del /F /Q "target\Contract_review-0.0.1-SNAPSHOT.jar" 2>nul
    if !ERRORLEVEL! EQU 0 (
        echo ✅ 旧 JAR 文件已删除
    ) else (
        echo ⚠️  无法删除旧 JAR，将在构建时覆盖
    )
) else (
    echo ✅ target 目录已清空
)

echo.

REM ============================================================
REM 第3步: 执行完整的 Maven 构建
REM ============================================================

echo [步骤3] 执行 Maven 完整构建...
echo 命令: mvn clean package -DskipTests
echo.

call mvn clean package -DskipTests

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ✅ Maven 构建成功！
    echo.
) else (
    echo.
    echo ❌ Maven 构建失败
    echo.
    echo 尝试使用 PowerShell 进行更强力的进程杀死...
    powershell -Command "Stop-Process -Name java -Force -ErrorAction SilentlyContinue" 2>nul
    timeout /t 2 /nobreak

    echo.
    echo 重新尝试构建...
    call mvn package -DskipTests

    if !ERRORLEVEL! NEQ 0 (
        echo ❌ 构建仍失败，请：
        echo 1. 打开 Windows 任务管理器 (Ctrl+Shift+Esc)
        echo 2. 找到 java.exe 进程并右键选择"结束任务"
        echo 3. 重新运行此脚本
        pause
        exit /b 1
    )
)

echo.

REM ============================================================
REM 第4步: 验证 JAR 文件
REM ============================================================

echo [步骤4] 验证 JAR 文件...

if not exist "target\Contract_review-0.0.1-SNAPSHOT.jar" (
    echo ❌ JAR 文件不存在！
    pause
    exit /b 1
)

echo ✅ JAR 文件已生成
echo.

REM ============================================================
REM 第5步: 启动应用
REM ============================================================

echo [步骤5] 启动应用到端口 8080...
echo.
echo 启动命令: java -jar target\Contract_review-0.0.1-SNAPSHOT.jar
echo 访问地址: http://localhost:8080
echo.

java -jar target\Contract_review-0.0.1-SNAPSHOT.jar

pause
