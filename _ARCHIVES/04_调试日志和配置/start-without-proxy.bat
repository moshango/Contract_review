@echo off
REM 清除所有代理环境变量后启动应用
echo ========================================
echo 清除代理环境变量...
echo ========================================

set HTTP_PROXY=
set HTTPS_PROXY=
set http_proxy=
set https_proxy=
set ALL_PROXY=
set all_proxy=
set NO_PROXY=
set no_proxy=

echo 代理已清除，启动应用...
echo ========================================

mvn spring-boot:run
