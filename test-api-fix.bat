@echo off
echo ========================================
echo API修复验证脚本
echo ========================================
echo.

echo 测试文件支持检查API...
echo.

set "fileName=reports/___________一键审查_A方_20251028_155832_67e25ac4.docx"
set "encodedFileName=%fileName:/=%2F%"

echo 原始文件名: %fileName%
echo 编码后文件名: %encodedFileName%
echo.

echo 测试API调用...
curl -s "http://localhost:8080/api/preview/supported?fileName=%encodedFileName%"

echo.
echo.
echo 如果看到JSON响应，说明API修复成功
echo 如果看到HTML页面，说明还有问题
echo.
pause
