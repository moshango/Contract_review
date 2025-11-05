@echo off
chcp 65001 >nul
echo ================================================
echo Aspose Words 集成测试脚本
echo ================================================
echo.

echo [1/3] 检查Maven依赖...
echo.
call mvn dependency:tree -Dincludes=com.aspose:aspose-words | findstr "aspose-words"
if %errorlevel% equ 0 (
    echo ✓ Aspose Words 依赖已正确配置
) else (
    echo ✗ 未找到Aspose Words依赖
    goto :error
)
echo.

echo [2/3] 编译项目...
echo.
call mvn clean compile -DskipTests
if %errorlevel% equ 0 (
    echo ✓ 编译成功
) else (
    echo ✗ 编译失败
    goto :error
)
echo.

echo [3/3] 启动应用（请在启动日志中查看Aspose授权信息）...
echo.
echo 预期日志输出：
echo ====================================
echo Aspose Words 授权已注册！
echo 版本: 24.12 (JDK17)
echo ====================================
echo.
echo 按任意键启动应用，或按Ctrl+C取消...
pause >nul

call mvn spring-boot:run

goto :end

:error
echo.
echo ================================================
echo 测试失败！请检查错误信息。
echo ================================================
pause
exit /b 1

:end
echo.
echo ================================================
echo 测试完成！
echo ================================================
pause

