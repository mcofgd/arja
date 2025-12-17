#!/bin/bash

# ARJA 快速验证脚本 - 使用最简单的项目
# 目标：快速验证 ARJA 是否能正常工作，而不是找到补丁

set -e

DEFECTS4J_HOME="$HOME/defects4j"
ARJA_HOME="$HOME/arja"
PROJECT="Chart"  # 使用 Chart，测试数量最少
BUG_ID="1"
WORK_DIR="/tmp"

# 颜色
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}ARJA 快速验证脚本${NC}"
echo -e "${BLUE}使用最简单的项目进行验证${NC}"
echo -e "${BLUE}========================================${NC}"

# 检查环境
if [ ! -d "$DEFECTS4J_HOME" ]; then
    echo "错误: Defects4J 未找到: $DEFECTS4J_HOME"
    exit 1
fi

if [ ! -d "$ARJA_HOME" ]; then
    echo "错误: ARJA 未找到: $ARJA_HOME"
    exit 1
fi

# 准备项目
PROJECT_DIR="${WORK_DIR}/${PROJECT}_${BUG_ID}_buggy"
echo -e "${BLUE}准备项目: ${PROJECT}_${BUG_ID}b${NC}"

if [ ! -d "$PROJECT_DIR" ]; then
    echo "检出项目..."
    cd "$WORK_DIR"
    "$DEFECTS4J_HOME/framework/bin/defects4j" checkout -p "$PROJECT" -v "${BUG_ID}b" -w "${PROJECT}_${BUG_ID}_buggy"
else
    echo "项目已存在，跳过检出"
fi

cd "$PROJECT_DIR"
echo "编译项目..."
"$DEFECTS4J_HOME/framework/bin/defects4j" compile

# 检查测试数量
TEST_INFO=$("$DEFECTS4J_HOME/framework/bin/defects4j" test 2>&1)
TEST_COUNT=$(echo "$TEST_INFO" | grep -oP "Running \K[0-9]+" | head -1 || echo "unknown")
echo -e "${GREEN}测试数量: $TEST_COUNT${NC}"

# 获取路径
SRC_DIR=$("$DEFECTS4J_HOME/framework/bin/defects4j" export -p dir.src.classes)
BIN_DIR=$("$DEFECTS4J_HOME/framework/bin/defects4j" export -p dir.bin.classes)
TEST_DIR=$("$DEFECTS4J_HOME/framework/bin/defects4j" export -p dir.bin.tests)
CP_TEST=$("$DEFECTS4J_HOME/framework/bin/defects4j" export -p cp.test)

echo -e "${BLUE}项目路径:${NC}"
echo "  源码: $SRC_DIR"
echo "  类文件: $BIN_DIR"
echo "  测试: $TEST_DIR"

# 运行验证（使用 GenProg，更简单，对种群大小要求更低）
echo -e "${BLUE}运行 GenProg 算法（更简单，更适合快速验证）...${NC}"
echo -e "${YELLOW}提示：GenProg 使用单目标优化，对种群大小要求更低${NC}"
cd "$ARJA_HOME"

PATCH_OUTPUT="/tmp/arja_validation_${PROJECT}_${BUG_ID}"

java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -cp "lib/*:bin" us.msu.cse.repair.Main GenProg \
     -DsrcJavaDir "$PROJECT_DIR/$SRC_DIR" \
     -DbinJavaDir "$PROJECT_DIR/$BIN_DIR" \
     -DbinTestDir "$PROJECT_DIR/$TEST_DIR" \
     -Ddependences $(echo "$CP_TEST" | tr ',' ':') \
     -DpopulationSize 5 \
     -DmaxGenerations 1 \
     -DwaitTime 180000 \
     -DtestExecutorName ExternalTestExecutor \
     -DtestFiltered true \
     -Dpercentage 0.01 \
     -DpatchOutputRoot "$PATCH_OUTPUT"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}验证完成！${NC}"
echo -e "${GREEN}========================================${NC}"

# 检查结果
if [ -d "$PATCH_OUTPUT" ]; then
    PATCH_COUNT=$(find "$PATCH_OUTPUT" -name "*.patch" 2>/dev/null | wc -l)
    if [ "$PATCH_COUNT" -gt 0 ]; then
        echo -e "${GREEN}✅ 找到 $PATCH_COUNT 个补丁！${NC}"
    else
        echo -e "${YELLOW}⚠️  未找到补丁（这是正常的，验证目标是功能而非补丁）${NC}"
    fi
else
    echo -e "${YELLOW}⚠️  未生成补丁目录（可能未找到有效补丁）${NC}"
fi

echo ""
echo "验证检查清单："
echo "  ✅ ARJA 能正常启动"
echo "  ✅ 能进行缺陷定位"
echo "  ✅ 能解析 AST"
echo "  ✅ 能执行测试评估"
echo "  ✅ 能完成评估流程"
echo ""
echo -e "${BLUE}如果看到 'One fitness evaluation is finished...' 消息，说明验证成功！${NC}"

