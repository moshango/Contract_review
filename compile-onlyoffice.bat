@echo off
echo 正在编译OnlyOffice预览器功能...
cd /d "%~dp0"
echo 当前目录: %CD%
echo 检查pom.xml文件...
if exist pom.xml (
    echo pom.xml文件存在
    echo 开始编译...
    mvn clean compile
    if %ERRORLEVEL% EQU 0 (
        echo 编译成功！
    ) else (
        echo 编译失败，错误代码: %ERRORLEVEL%
    )
) else (
    echo 错误: pom.xml文件不存在
)
pause
