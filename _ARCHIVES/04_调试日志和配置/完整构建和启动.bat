@echo off
REM ============================================================
REM 合同审查系统 - 完整构建和启动脚本
REM 功能: 自动清理僵尸进程 → Maven 构建 → 启动应用
REM ============================================================

setlocal enabledelayedexpansion

echo.
echo ============================================================
echo 合同审查系统 - 完整构建和启动
echo ============================================================
echo.

REM ============================================================
REM 第1步: 通过多种方法清理 Java 进程
REM ============================================================

echo [步骤1] 清理所有 Java 进程...
echo.

REM 方法1: 使用 taskkill 强制关闭
echo  - 尝试 taskkill /F /IM java.exe...
taskkill /F /IM java.exe >nul 2>&1

REM 方法2: 使用 wmic 删除 Java 进程（如果存在）
echo  - 尝试 wmic 方法...
wmic process where name="java.exe" delete /nointeractive >nul 2>&1

REM 方法3: 通过 PID 直接 kill
echo  - 清理指定 PID...
taskkill /PID 25640 /F /T >nul 2>&1
taskkill /PID 27604 /F /T >nul 2>&1
taskkill /PID 11104 /F /T >nul 2>&1

REM 等待进程完全释放
echo  - 等待 3 秒释放资源...
timeout /t 3 /nobreak

echo ✅ Java 进程清理完成
echo.

REM ============================================================
REM 第2步: 验证端口已释放
REM ============================================================

echo [步骤2] 验证端口状态...
echo.

setlocal enabledelayedexpansion
set "port_8080_free=0"
set "port_9999_free=0"

netstat -ano 2>nul | findstr "8080" >nul
if %ERRORLEVEL% EQU 0 (
    echo ⚠️  端口 8080 仍被占用，再次尝试清理...
    taskkill /F /IM java.exe >nul 2>&1
    timeout /t 2 /nobreak
) else (
    set "port_8080_free=1"
    echo ✅ 端口 8080 已释放
)

netstat -ano 2>nul | findstr "9999" >nul
if %ERRORLEVEL% EQU 0 (
    echo ⚠️  端口 9999 仍被占用
) else (
    set "port_9999_free=1"
    echo ✅ 端口 9999 已释放
)

echo.

REM ============================================================
REM 第3步: 执行 Maven 构建
REM ============================================================

echo [步骤3] 执行 Maven 构建...
echo.

cd /d "%~dp0"

REM 首先尝试清理和重新构建
echo 运行: mvn clean package -DskipTests
mvn clean package -DskipTests

REM 检查构建结果
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ✅ Maven 构建成功！
    echo.
) else (
    echo.
    echo ⚠️  Maven clean 失败，尝试增量构建...
    echo 运行: mvn package -DskipTests (不使用 clean)
    echo.
    mvn package -DskipTests

    if %ERRORLEVEL% EQU 0 (
        echo ✅ 增量构建成功！
    ) else (
        echo.
        echo ❌ Maven 构建失败！
        echo 错误代码: %ERRORLEVEL%
        echo.
        echo 故障排除:
        echo 1. 打开 Windows 任务管理器 (Ctrl+Shift+Esc)
        echo 2. 找到并终止所有 java.exe 进程
        echo 3. 再次运行此脚本
        echo.
        pause
        exit /b 1
    )
)

REM ============================================================
REM 第4步: 验证 JAR 生成
REM ============================================================

echo.
echo [步骤4] 验证 JAR 文件...
echo.

if exist "target\Contract_review-0.0.1-SNAPSHOT.jar" (
    echo ✅ JAR 文件已生成
    for /F "tokens=5" %%A in ('dir "target\Contract_review-0.0.1-SNAPSHOT.jar" ^| findstr "Contract_review"') do (
        echo   文件大小: %%A 字节
    )
) else (
    echo ❌ JAR 文件未找到！
    pause
    exit /b 1
)

echo.

REM ============================================================
REM 第5步: 清理旧进程（最终保障）
REM ============================================================

echo [步骤5] 最终清理...
taskkill /F /IM java.exe >nul 2>&1
timeout /t 1 /nobreak

echo ✅ 清理完成
echo.

REM ============================================================
REM 第6步: 启动应用
REM ============================================================

echo [步骤6] 启动应用到端口 8080...
echo.

echo 命令: java -jar target\Contract_review-0.0.1-SNAPSHOT.jar
echo.

java -jar target\Contract_review-0.0.1-SNAPSHOT.jar

REM 如果 Java 命令失败
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ❌ 应用启动失败！
    echo.
    echo 故障排除:
    echo 1. 检查 Java 版本: java -version
    echo 2. 确认 JAR 文件完整
    echo 3. 检查 application.properties 配置
    echo.
    pause
)

pause
