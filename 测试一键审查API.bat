@echo off
chcp 65001 >nul
echo ================================================
echo 一键审查API测试脚本
echo ================================================
echo.

set API_BASE=http://localhost:8080

echo [1/4] 检查后端服务...
curl -s %API_BASE%/api/qwen/rule-review/status | findstr "success" >nul
if %errorlevel% equ 0 (
    echo ✓ 后端服务正常
) else (
    echo ✗ 后端服务未启动或不可用
    echo.
    echo 请先启动后端服务：
    echo   cd Contract_review
    echo   mvn spring-boot:run
    goto :end
)
echo.

echo [2/4] 准备测试文件...
set TEST_FILE=测试合同_综合测试版.docx
if exist "%TEST_FILE%" (
    echo ✓ 找到测试文件: %TEST_FILE%
) else (
    echo ✗ 未找到测试文件: %TEST_FILE%
    echo.
    echo 请将测试文件放在当前目录，或修改脚本中的TEST_FILE变量
    goto :end
)
echo.

echo [3/4] 测试解析API...
echo 请求: POST /api/parse
curl -X POST "%API_BASE%/api/parse" ^
  -F "file=@%TEST_FILE%" ^
  -F "anchors=generate" ^
  -F "returnMode=json" ^
  -o parse_result.json ^
  -w "HTTP状态码: %%{http_code}\n耗时: %%{time_total}秒\n"

if %errorlevel% equ 0 (
    echo ✓ 解析API调用成功
    echo.
    echo 解析结果预览（前200字符）:
    type parse_result.json | findstr /C:"partyA" /C:"partyB" /C:"filename"
) else (
    echo ✗ 解析API调用失败
)
echo.

echo [4/4] 测试一键审查API...
echo 请求: POST /api/qwen/rule-review/one-click-review
echo 立场: 甲方（A方）
echo.
echo ⏱️  预计耗时 5-10秒，请耐心等待...
echo.

curl -X POST "%API_BASE%/api/qwen/rule-review/one-click-review" ^
  -F "file=@%TEST_FILE%" ^
  -F "stance=A方" ^
  -o review_result.json ^
  -w "HTTP状态码: %%{http_code}\n总耗时: %%{time_total}秒\n"

if %errorlevel% equ 0 (
    echo.
    echo ✓ 一键审查API调用成功
    echo.
    echo 审查结果:
    type review_result.json
    echo.
    echo.
    echo 审查结果已保存到: review_result.json
) else (
    echo ✗ 一键审查API调用失败
)
echo.

:end
echo ================================================
echo 测试完成！
echo ================================================
echo.
echo 生成的文件:
echo   - parse_result.json    (解析结果)
echo   - review_result.json   (审查结果)
echo.
pause

