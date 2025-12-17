#!/bin/bash

# ARJA 快速测试脚本 - 使用 Defects4JFaultLocalizer
# 用于快速验证 Java 11 升级后的 ARJA 是否能正常工作

set -e

# 配置
JAVA_HOME="/usr/lib/jvm/java-11-openjdk-amd64"
ARJA_HOME="/home/x/arja"
TEST_PROJECT="/home/x/defects4j_test/Lang_1b"
LOG_FILE="/tmp/arja_quick_test_$(date +%Y%m%d_%H%M%S).log"

# 颜色
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo "=========================================="
echo "ARJA 快速测试（Java 11 + Defects4JFaultLocalizer）"
echo "=========================================="

# 设置 Java 11
export PATH="$JAVA_HOME/bin:$PATH"
echo "Java 版本: $(java -version 2>&1 | head -1)"

# 检查项目
if [ ! -d "$TEST_PROJECT" ]; then
    echo -e "${RED}错误: 测试项目不存在: $TEST_PROJECT${NC}"
    exit 1
fi

# 编译 ARJA
echo "编译 ARJA..."
cd "$ARJA_HOME"
mvn clean package -DskipTests > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ 编译成功${NC}"
else
    echo -e "${RED}✗ 编译失败${NC}"
    exit 1
fi

# 构建 classpath
CLASSPATH="$ARJA_HOME/target/Arja-0.0.1-SNAPSHOT.jar"
for jar in "$ARJA_HOME"/lib/*.jar; do
    CLASSPATH="$CLASSPATH:$jar"
done

echo "Classpath: ${CLASSPATH:0:80}..."

# 获取项目路径
cd "$TEST_PROJECT"
BIN_DIR=$(defects4j export -p dir.bin.classes 2>/dev/null || echo "target/classes")
TEST_DIR=$(defects4j export -p dir.bin.tests 2>/dev/null || echo "target/tests")
CP_TEST=$(defects4j export -p cp.test 2>/dev/null || cat defects4j.build.classpath)

echo "项目路径:"
echo "  BIN_DIR: $BIN_DIR"
echo "  TEST_DIR: $TEST_DIR"
echo "  CP_TEST: ${CP_TEST:0:60}..."

# 运行 ARJA（小规模测试）
echo ""
echo "启动 ARJA（小规模测试：1代，种群5）..."
echo "日志文件: $LOG_FILE"
echo ""

cd "$ARJA_HOME"

java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -cp "$CLASSPATH" us.msu.cse.repair.Main Arja \
    -DsrcJavaDir "$TEST_PROJECT" \
    -DbinJavaDir "$TEST_PROJECT/$BIN_DIR" \
    -DbinTestDir "$TEST_PROJECT/$TEST_DIR" \
    -Ddependences "$CP_TEST" \
    -DexternalProjRoot "$TEST_PROJECT" \
    -DpopulationSize 5 \
    -DmaxGenerations 1 \
    -DwaitTime 180000 \
    -DtestFiltered false \
    -DtestExecutorName ExternalTestExecutor \
    -DingredientScreeningMode 0 \
    -DmiFilterRule false \
    -DmanipulationFilterRule false \
    -DingredientFilterRule false \
    -DseedLineGenerated false \
    > "$LOG_FILE" 2>&1 &

ARJA_PID=$!
echo "ARJA PID: $ARJA_PID"

# 监控进程
echo "监控运行状态（按 Ctrl+C 停止）..."
COUNTER=0
while kill -0 $ARJA_PID 2>/dev/null; do
    COUNTER=$((COUNTER + 1))
    
    # 每10秒显示一次状态
    if [ $((COUNTER % 2)) -eq 0 ]; then
        echo -n "."
    fi
    
    # 检查关键进度
    if grep -q "Fault localization is finished" "$LOG_FILE" 2>/dev/null; then
        if [ -z "$FL_DONE" ]; then
            echo ""
            echo -e "${GREEN}✓ 故障定位完成${NC}"
            FL_DONE=1
            
            # 显示故障定位结果
            echo "故障定位结果:"
            grep "Number of faulty lines found" "$LOG_FILE" || true
            grep "Number of modification points" "$LOG_FILE" || true
        fi
    fi
    
    if grep -q "One fitness evaluation starts" "$LOG_FILE" 2>/dev/null; then
        if [ -z "$EVAL_STARTED" ]; then
            echo ""
            echo -e "${GREEN}✓ 适应度评估已启动${NC}"
            EVAL_STARTED=1
        fi
    fi
    
    if grep -q "One fitness evaluation is finished" "$LOG_FILE" 2>/dev/null; then
        if [ -z "$EVAL_DONE" ]; then
            echo ""
            echo -e "${GREEN}✓ 适应度评估完成${NC}"
            EVAL_DONE=1
        fi
    fi
    
    # 检查错误
    if grep -qi "exception\|error.*fault" "$LOG_FILE" 2>/dev/null; then
        if [ -z "$ERROR_SHOWN" ]; then
            echo ""
            echo -e "${RED}⚠ 检测到错误${NC}"
            grep -i "exception\|error" "$LOG_FILE" | tail -3
            ERROR_SHOWN=1
        fi
    fi
    
    sleep 5
    
    # 超时检查（5分钟）
    if [ $COUNTER -gt 60 ]; then
        echo ""
        echo -e "${YELLOW}超时，终止进程${NC}"
        kill $ARJA_PID 2>/dev/null || true
        break
    fi
done

wait $ARJA_PID 2>/dev/null || true

echo ""
echo "=========================================="
echo "测试完成"
echo "=========================================="

# 分析结果
echo ""
echo "关键指标:"
echo "--------"

# 1. 故障定位
FL_LINES=$(grep "Number of faulty lines found" "$LOG_FILE" | tail -1 || echo "未找到")
echo "故障定位: $FL_LINES"

# 2. 修改点
MP_COUNT=$(grep "Total modification points after trimming" "$LOG_FILE" | tail -1 || echo "未找到")
echo "修改点: $MP_COUNT"

# 3. 适应度评估
EVAL_COUNT=$(grep -c "One fitness evaluation starts" "$LOG_FILE" 2>/dev/null || echo "0")
echo "适应度评估次数: $EVAL_COUNT"

# 4. 错误
ERROR_COUNT=$(grep -ci "exception\|error.*fault" "$LOG_FILE" 2>/dev/null || echo "0")
if [ "$ERROR_COUNT" -gt 0 ]; then
    echo -e "${RED}错误数量: $ERROR_COUNT${NC}"
else
    echo -e "${GREEN}错误数量: 0${NC}"
fi

echo ""
echo "详细日志: $LOG_FILE"
echo ""

# 显示最后20行
echo "日志最后20行:"
echo "--------"
tail -20 "$LOG_FILE"

echo ""
echo "=========================================="

# 判断成功与否
if grep -q "Number of faulty lines found: [1-9]" "$LOG_FILE" && \
   grep -q "Total modification points after trimming: [1-9]" "$LOG_FILE"; then
    echo -e "${GREEN}✓ 测试成功！ARJA 可以正常工作${NC}"
    echo "  - 故障定位成功"
    echo "  - 修改点生成成功"
    exit 0
else
    echo -e "${YELLOW}⚠ 测试部分成功${NC}"
    echo "请检查日志文件了解详情"
    exit 1
fi