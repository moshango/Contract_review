#!/bin/bash

# 清除所有代理环境变量后启动应用
echo "========================================"
echo "清除代理环境变量..."
echo "========================================"

unset HTTP_PROXY
unset HTTPS_PROXY
unset http_proxy
unset https_proxy
unset ALL_PROXY
unset all_proxy
unset NO_PROXY
unset no_proxy

echo "代理已清除，启动应用..."
echo "========================================"

mvn spring-boot:run
