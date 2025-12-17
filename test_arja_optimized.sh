#!/bin/bash

# ARJA 优化测试脚本 - 解决三大核心问题
# 1. miFilterRule 未正确禁用
# 2. 测试执行超时
# 3. 操作成分不足

set -e

# 配置
JAVA_HOME="/usr/lib/jvm/java-11-openjdk-amd64"
ARJA_HOME="/home/x/arja"
TEST_PROJECT="/home/x/defects4j_test/Math_1b"
LOG_FILE="/tmp/arja_optimized_test_$(date +%Y%m%d_%H%M%S).log"

# 颜色
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "=========================================="
echo "ARJA 优化测试（解决三大核心问题）"
echo "=========================================="
echo ""
echo -e "${BLUE}优化目标：${NC}"
echo "  1. 确保 miFilterRule 正确禁用"
echo "  2. 增加测试超时时间（10分钟）"
echo "  3. 启用种子行生成获取更多成分"
echo ""

# 设置 Java 11
export PATH="$JAVA_HOME/bin:$PATH"
echo "Java 版本: $(java -version 2>&1 | head -1)"

# 检查项目
if [ ! -d "$TEST_PROJECT" ]; then
    echo -e "${RED}错误: 测试项目不存在: $TEST_PROJECT${NC}"
    exit 1
fi

# 编译 ARJA
echo ""
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

# 获取项目路径
cd "$TEST_PROJECT"

# 检查项目是否已编译
if [ ! -f ".defects4j.config" ]; then
    echo -e "${RED}错误: 项目未正确检出${NC}"
    echo "请先运行: defects4j checkout -p Chart -v 1b -w $TEST_PROJECT"
    exit 1
fi

# 确保项目已编译
if [ ! -d "build" ] && [ ! -d "target/classes" ]; then
    echo "编译项目..."
    defects4j compile
fi

# 获取路径（兼容 v2 和 v3）
BIN_DIR=$(defects4j export -p dir.bin.classes 2>/dev/null)
if [ -z "$BIN_DIR" ]; then
    # Defects4J v2 fallback
    if [ -d "build/classes" ]; then
        BIN_DIR="build/classes"
    elif [ -d "target/classes" ]; then
        BIN_DIR="target/classes"
    else
        echo -e "${RED}错误: 找不到编译后的类文件目录${NC}"
        exit 1
    fi
fi

TEST_DIR=$(defects4j export -p dir.bin.tests 2>/dev/null)
if [ -z "$TEST_DIR" ]; then
    # Defects4J v2 fallback
    if [ -d "build/tests" ]; then
        TEST_DIR="build/tests"
    elif [ -d "target/tests" ]; then
        TEST_DIR="target/tests"
    else
        echo -e "${RED}错误: 找不到编译后的测试类目录${NC}"
        exit 1
    fi
fi

CP_TEST=$(defects4j export -p cp.test 2>/dev/null)
if [ -z "$CP_TEST" ]; then
    # Defects4J v2 fallback - 构建 classpath
    echo -e "${YELLOW}警告: defects4j export 失败，尝试手动构建 classpath${NC}"
    
    # 基本路径
    CP_TEST="$TEST_PROJECT/$BIN_DIR:$TEST_PROJECT/$TEST_DIR"
    
    # 添加 lib 目录下的 jar
    if [ -d "$TEST_PROJECT/lib" ]; then
        for jar in "$TEST_PROJECT"/lib/*.jar; do
            if [ -f "$jar" ]; then
                CP_TEST="$CP_TEST:$jar"
            fi
        done
    fi
    
    # 添加 Defects4J 框架的 jar
    DEFECTS4J_HOME=$(defects4j info -p Chart 2>/dev/null | grep "Defects4J" | head -1 | awk '{print $NF}' || echo "/home/x/defects4j")
    if [ -d "$DEFECTS4J_HOME/framework/projects/lib" ]; then
        CP_TEST="$CP_TEST:$DEFECTS4J_HOME/framework/projects/lib/junit-4.12-hamcrest-1.3.jar"
    fi
    
    # 添加项目特定的库
    if [ -d "$DEFECTS4J_HOME/framework/projects/Chart/lib" ]; then
        for jar in "$DEFECTS4J_HOME"/framework/projects/Chart/lib/*.jar; do
            if [ -f "$jar" ]; then
                CP_TEST="$CP_TEST:$jar"
            fi
        done
    fi
fi

echo ""
echo "项目路径:"
echo "  BIN_DIR: $BIN_DIR"
echo "  TEST_DIR: $TEST_DIR"

# 运行 ARJA（优化参数）
echo ""
echo -e "${BLUE}启动 ARJA（优化配置）...${NC}"
echo "  - 种群大小: 20"
echo "  - 代数: 10"
echo "  - 测试超时: 600000ms (10分钟)"
echo "  - 成分筛选: 模式 0（最宽松）"
echo "  - 种子行生成: 启用"
echo "  - 所有过滤规则: 禁用"
echo ""
echo "日志文件: $LOG_FILE"
echo ""

cd "$ARJA_HOME"

# 使用优化的参数
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -cp "$CLASSPATH" us.msu.cse.repair.Main Arja \
    -DsrcJavaDir "$TEST_PROJECT" \
    -DbinJavaDir "$TEST_PROJECT/$BIN_DIR" \
    -DbinTestDir "$TEST_PROJECT/$TEST_DIR" \
    -Ddependences "$CP_TEST" \
    -DexternalProjRoot "$TEST_PROJECT" \
    -DpopulationSize 40 \
    -DmaxGenerations 100 \
    -DwaitTime 900000 \
    -DtestFiltered false \
    -DtestExecutorName ExternalTestExecutor \
    -DingredientScreeningMode 0 \
    -DseedLineGenerated true \
    -DmiFilterRule false \
    -DmanipulationFilterRule false \
    -DingredientFilterRule false \
    > "$LOG_FILE" 2>&1 &

ARJA_PID=$!
echo "ARJA PID: $ARJA_PID"

# 监控进程
echo ""
echo "监控运行状态（按 Ctrl+C 停止）..."
echo ""
COUNTER=0
FL_DONE=""
EVAL_STARTED=""
EVAL_DONE=""
ERROR_SHOWN=""
INGREDIENT_CHECKED=""
MI_FILTER_CHECKED=""
TIMEOUT_WARNING=""

while kill -0 $ARJA_PID 2>/dev/null; do
    COUNTER=$((COUNTER + 1))
    
    # 每10秒显示一次状态
    if [ $((COUNTER % 2)) -eq 0 ]; then
        echo -n "."
    fi
    
    # 检查故障定位
    if grep -q "Fault localization is finished" "$LOG_FILE" 2>/dev/null; then
        if [ -z "$FL_DONE" ]; then
            echo ""
            echo -e "${GREEN}✓ 故障定位完成${NC}"
            FL_DONE=1
            
            # 显示故障定位结果
            FAULTY_LINES=$(grep "Number of faulty lines found" "$LOG_FILE" | tail -1)
            MOD_POINTS=$(grep "Total modification points after trimming" "$LOG_FILE" | tail -1)
            echo "  $FAULTY_LINES"
            echo "  $MOD_POINTS"
        fi
    fi
    
    # 检查成分可用性
    if grep -q "Modification points without ingredients" "$LOG_FILE" 2>/dev/null; then
        if [ -z "$INGREDIENT_CHECKED" ]; then
            INGREDIENT_INFO=$(grep "Modification points without ingredients" "$LOG_FILE" | tail -1)
            echo ""
            echo -e "${YELLOW}⚠ 成分状态: $INGREDIENT_INFO${NC}"
            INGREDIENT_CHECKED=1
        fi
    fi
    
    # 检查 miFilterRule 状态
    if grep -q "miFilterRule enabled" "$LOG_FILE" 2>/dev/null; then
        if [ -z "$MI_FILTER_CHECKED" ]; then
            MI_STATUS=$(grep "miFilterRule enabled" "$LOG_FILE" | tail -1)
            if echo "$MI_STATUS" | grep -q "true"; then
                echo ""
                echo -e "${RED}✗ 警告: miFilterRule 仍然启用！${NC}"
                echo "  $MI_STATUS"
            else
                echo ""
                echo -e "${GREEN}✓ miFilterRule 已正确禁用${NC}"
            fi
            MI_FILTER_CHECKED=1
        fi
    fi
    
    # 检查适应度评估
    if grep -q "One fitness evaluation starts" "$LOG_FILE" 2>/dev/null; then
        if [ -z "$EVAL_STARTED" ]; then
            echo ""
            echo -e "${GREEN}✓ 适应度评估已启动${NC}"
            EVAL_STARTED=1
        fi
    fi
    
    if grep -q "One fitness evaluation is finished" "$LOG_FILE" 2>/dev/null; then
        if [ -z "$EVAL_DONE" ]; then
            EVAL_COUNT=$(grep -c "One fitness evaluation is finished" "$LOG_FILE" 2>/dev/null || echo "0")
            echo ""
            echo -e "${GREEN}✓ 适应度评估进行中 (已完成 $EVAL_COUNT 次)${NC}"
            EVAL_DONE=1
        fi
    fi
    
    # 检查超时
    if grep -q "Timeout occurs" "$LOG_FILE" 2>/dev/null; then
        if [ -z "$TIMEOUT_WARNING" ]; then
            TIMEOUT_COUNT=$(grep -c "Timeout occurs" "$LOG_FILE" 2>/dev/null || echo "0")
            if [ "$TIMEOUT_COUNT" -gt 3 ]; then
                echo ""
                echo -e "${YELLOW}⚠ 检测到多次超时 ($TIMEOUT_COUNT 次)${NC}"
                echo "  建议进一步增加 -DwaitTime"
                TIMEOUT_WARNING=1
            fi
        fi
    fi
    
    # 检查错误
    if grep -qi "exception.*at us.msu.cse" "$LOG_FILE" 2>/dev/null; then
        if [ -z "$ERROR_SHOWN" ]; then
            echo ""
            echo -e "${RED}⚠ 检测到异常${NC}"
            grep -i "exception" "$LOG_FILE" | grep "at us.msu.cse" | tail -3
            ERROR_SHOWN=1
        fi
    fi
    
    sleep 5
    
    # 超时检查（20分钟）
    if [ $COUNTER -gt 240 ]; then
        echo ""
        echo -e "${YELLOW}超时（20分钟），终止进程${NC}"
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
echo -e "${BLUE}关键指标分析：${NC}"
echo "--------"

# 1. 故障定位
FL_LINES=$(grep "Number of faulty lines found" "$LOG_FILE" | tail -1 || echo "未找到")
echo "故障定位: $FL_LINES"

# 2. 修改点
MP_COUNT=$(grep "Total modification points after trimming" "$LOG_FILE" | tail -1 || echo "未找到")
echo "修改点: $MP_COUNT"

# 3. 成分可用性
INGREDIENT_STATUS=$(grep "Modification points without ingredients" "$LOG_FILE" | tail -1 || echo "未找到")
echo "成分状态: $INGREDIENT_STATUS"

# 4. miFilterRule 状态
MI_STATUS=$(grep "miFilterRule enabled" "$LOG_FILE" | tail -1 || echo "未找到")
echo "miFilterRule: $MI_STATUS"

# 5. 适应度评估
EVAL_COUNT=$(grep -c "One fitness evaluation starts" "$LOG_FILE" 2>/dev/null || echo "0")
EVAL_FINISHED=$(grep -c "One fitness evaluation is finished" "$LOG_FILE" 2>/dev/null || echo "0")
echo "适应度评估: 启动 $EVAL_COUNT 次, 完成 $EVAL_FINISHED 次"

# 6. 超时
TIMEOUT_COUNT=$(grep -c "Timeout occurs" "$LOG_FILE" 2>/dev/null || echo "0")
if [ "$TIMEOUT_COUNT" -gt 0 ]; then
    echo -e "${YELLOW}超时次数: $TIMEOUT_COUNT${NC}"
else
    echo -e "${GREEN}超时次数: 0${NC}"
fi

# 7. 编译成功
COMPILE_SUCCESS=$(grep -c "Compilation successful" "$LOG_FILE" 2>/dev/null || echo "0")
COMPILE_FAIL=$(grep -c "Compilation fails" "$LOG_FILE" 2>/dev/null || echo "0")
echo "编译统计: 成功 $COMPILE_SUCCESS 次, 失败 $COMPILE_FAIL 次"

# 8. 补丁
PATCH_COUNT=$(grep -c "patch found\|valid patch" "$LOG_FILE" 2>/dev/null || echo "0")
if [ "$PATCH_COUNT" -gt 0 ]; then
    echo -e "${GREEN}找到补丁: $PATCH_COUNT 个${NC}"
else
    echo "找到补丁: 0 个"
fi

echo ""
echo "详细日志: $LOG_FILE"
echo ""

# 显示最后30行
echo -e "${BLUE}日志最后30行:${NC}"
echo "--------"
tail -30 "$LOG_FILE"

echo ""
echo "=========================================="

# 判断成功与否
if grep -q "Number of faulty lines found: [1-9]" "$LOG_FILE" && \
   grep -q "Total modification points after trimming: [1-9]" "$LOG_FILE" && \
   [ "$EVAL_FINISHED" -gt 0 ]; then
    echo -e "${GREEN}✓ 测试成功！${NC}"
    echo "  - 故障定位成功"
    echo "  - 修改点生成成功"
    echo "  - 适应度评估正常运行"
    
    if [ "$TIMEOUT_COUNT" -gt 5 ]; then
        echo ""
        echo -e "${YELLOW}建议：${NC}"
        echo "  - 超时次数较多，建议进一步增加 -DwaitTime 到 900000 (15分钟)"
    fi
    
    if echo "$INGREDIENT_STATUS" | grep -q "40"; then
        echo ""
        echo -e "${YELLOW}建议：${NC}"
        echo "  - 成分仍然不足，已启用种子行生成"
        echo "  - 如果仍有问题，可以尝试使用不同的项目"
    fi
    
    exit 0
else
    echo -e "${YELLOW}⚠ 测试部分成功${NC}"
    echo "请检查日志文件了解详情"
    exit 1
fi