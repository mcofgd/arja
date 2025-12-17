#!/bin/bash

# ARJA Java 8 快速启动脚本
# 用于解决 GZoltar 0.1.1 与 Java 11 不兼容的问题

set -e

echo "=============================================="
echo "   ARJA Java 8 快速启动"
echo "=============================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 检查 Java 8 是否安装
JAVA8_HOME="/usr/lib/jvm/java-8-openjdk-amd64"

if [ ! -d "$JAVA8_HOME" ]; then
    echo -e "${RED}[ERROR]${NC} Java 8 未安装"
    echo -e "${BLUE}[INFO]${NC} 正在安装 Java 8..."
    sudo apt-get update
    sudo apt-get install -y openjdk-8-jdk
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}[ERROR]${NC} Java 8 安装失败"
        exit 1
    fi
    echo -e "${GREEN}[SUCCESS]${NC} Java 8 安装成功"
fi

# 切换到 Java 8
export JAVA_HOME="$JAVA8_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

echo -e "${BLUE}[INFO]${NC} 当前 Java 版本:"
java -version

# 验证是否是 Java 8
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
if [[ ! "$JAVA_VERSION" =~ ^1\.8\. ]]; then
    echo -e "${RED}[ERROR]${NC} Java 版本不是 1.8.x: $JAVA_VERSION"
    exit 1
fi

echo -e "${GREEN}[SUCCESS]${NC} Java 8 环境已设置"

# 编译 ARJA
echo -e "${BLUE}[INFO]${NC} 编译 ARJA..."
cd "$(dirname "$0")"
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo -e "${RED}[ERROR]${NC} ARJA 编译失败"
    exit 1
fi

echo -e "${GREEN}[SUCCESS]${NC} ARJA 编译成功"

# 运行测试（如果提供了参数）
if [ "$1" == "test" ]; then
    echo -e "${BLUE}[INFO]${NC} 运行测试..."
    cd /home/x/defects4j_test
    bash /home/x/arja/test_arja_ns.sh
fi

echo ""
echo "=============================================="
echo -e "${GREEN}[SUCCESS]${NC} ARJA 已准备就绪（Java 8）"
echo "=============================================="
echo ""
echo "使用方法:"
echo "  1. 直接运行 ARJA:"
echo "     cd /home/x/defects4j_test"
echo "     bash /home/x/arja/test_arja_ns.sh"
echo ""
echo "  2. 或使用此脚本运行测试:"
echo "     bash quick_start_java8.sh test"
echo ""
echo "注意: 此脚本会将 Java 版本切换到 1.8.x"
echo "      如需切换回 Java 11，请运行:"
echo "      export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64"
echo "      export PATH=\$JAVA_HOME/bin:\$PATH"
echo ""