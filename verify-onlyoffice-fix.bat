@echo off
echo ========================================
echo OnlyOffice预览器修复验证
echo ========================================
echo.

echo 检查JavaScript文件语法...
node -c "src/main/resources/static/js/onlyoffice-previewer.js" 2>nul
if %ERRORLEVEL% EQU 0 (
    echo ✅ JavaScript语法正确
) else (
    echo ❌ JavaScript语法错误
    echo 请检查文件内容
)

echo.
echo 修复完成！请执行以下步骤：
echo.
echo 1. 刷新浏览器页面 (Ctrl+F5 强制刷新)
echo 2. 打开开发者工具查看控制台
echo 3. 点击"文件预览"选项卡
echo 4. 测试文件预览功能
echo.
echo 如果仍有问题，请查看控制台错误信息
echo.
pause
