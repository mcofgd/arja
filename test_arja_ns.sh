#!/bin/bash

# ARJA + Defects4J v3.0.1 验证脚本（Java 11 升级版）
# 用于验证升级后的 ARJA 项目是否能正常运行
# 修改时间：2024-12-19
# 主要改动：适配 Defects4J v3.0.1 和 Java 11

set -e

# ================== 配置区 ==================
DEFECTS4J_HOME="$HOME/defects4j"
JAVA11_HOME="/usr/lib/jvm/java-11-openjdk-amd64"
WORK_DIR="$HOME/defects4j_test"
PROJECT_NAME="Math_2"       # 推荐使用简单的 bug 进行验证（Lang_1b, Math_1b 等）
ARJA_HOME="$HOME/arja"

# 搜索参数（可适当加大）
POPULATION_SIZE=50        # 减少初始种群大小，便于调试
MAX_GENERATIONS=80        # 减少代数，便于快速验证
WAIT_TIME=1800000         # 1800秒，增加超时时间避免测试卡住
TEST_EXECUTOR="ExternalTestExecutor"  # 使用外部测试执行器，更稳定

# 日志与输出
LOG_DIR="$ARJA_HOME"
PATCH_OUTPUT_ROOT="$WORK_DIR/arja_patches_${PROJECT_NAME}"
ARJA_LOG="$LOG_DIR/arja_${PROJECT_NAME}_debug.log"

# 颜色
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# ================== 工具函数 ==================
use_java11() {
    export JAVA_HOME="$JAVA11_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
    log_info "使用 Java 11: $($JAVA_HOME/bin/java -version 2>&1 | head -1)"
}

diagnose_defects4j_paths() {
    local project_dir="$1"
    log_info "诊断 Defects4J 项目路径结构..."
    
    cd "$project_dir" || return 1
    
    # 检查 Defects4J 命令是否可用
    if ! command -v defects4j &> /dev/null; then
        log_error "defects4j 命令不可用，请检查 PATH"
        return 1
    fi
    
    # 尝试使用 defects4j export
    log_info "尝试使用 defects4j export 获取路径..."
    if defects4j export -p dir.bin.classes &> /dev/null; then
        BIN_EXPORT=$(defects4j export -p dir.bin.classes)
        TEST_EXPORT=$(defects4j export -p dir.bin.tests)
        log_success "defects4j export 成功:"
        log_info "  BIN_DIR: $BIN_EXPORT"
        log_info "  TEST_DIR: $TEST_EXPORT"
        
        # 验证路径是否存在
        if [ -d "$BIN_EXPORT" ] && [ -d "$TEST_EXPORT" ]; then
            return 0
        else
            log_warning "export 返回的路径不存在，可能项目未编译"
            return 1
        fi
    else
        log_warning "defects4j export 失败，检查常见路径..."
        
        # 检查常见路径
        if [ -d "build/classes" ] && [ -d "build/tests" ]; then
            log_success "找到 build/ 目录结构（Defects4J v3.0.1）"
            return 0
        elif [ -d "target/classes" ] && [ -d "target/test-classes" ]; then
            log_success "找到 target/ 目录结构（旧版 Defects4J）"
            return 0
        else
            log_error "未找到标准路径结构"
            log_info "当前目录: $(pwd)"
            log_info "目录内容:"
            ls -la | head -20
            return 1
        fi
    fi
}

compile_arja() {
    log_info "编译 ARJA（使用 Maven + Java 11）..."
    cd "$ARJA_HOME"
    
    # 使用 Maven 编译，确保所有依赖正确
    mvn clean package -DskipTests > /dev/null 2>&1
    
    # 验证编译结果
    if [ -f "target/Arja-0.0.1-SNAPSHOT.jar" ]; then
        log_success "ARJA 编译完成（Java 11 兼容，使用 Maven）"
    else
        log_error "编译失败：未找到 JAR 文件"
        exit 1
    fi
}

prepare_defects4j_project() {
    log_info "准备 Defects4J v3.0.1 项目: $PROJECT_NAME"
    mkdir -p "$WORK_DIR" "$LOG_DIR"
    cd "$WORK_DIR"

    PROJECT_ID=$(echo "$PROJECT_NAME" | cut -d'_' -f1)
    BUG_ID=$(echo "$PROJECT_NAME" | cut -d'_' -f2 | sed 's/b$//')

    if [ ! -d "$PROJECT_NAME" ]; then
        log_info "检出项目: $PROJECT_ID bug ${BUG_ID}b（Defects4J v3.0.1）"
        "$DEFECTS4J_HOME/framework/bin/defects4j" checkout -p "$PROJECT_ID" -v "${BUG_ID}b" -w "$PROJECT_NAME"
    else
        log_info "项目已存在，跳过检出"
    fi

    cd "$PROJECT_NAME"
    log_info "编译项目（Defects4J v3.0.1 使用 Java 11）..."
    COMPILE_OUTPUT=$("$DEFECTS4J_HOME/framework/bin/defects4j" compile 2>&1)
    COMPILE_EXIT_CODE=$?
    
    if [ $COMPILE_EXIT_CODE -ne 0 ]; then
        log_error "Defects4J 编译失败！"
        echo "$COMPILE_OUTPUT" | tail -20
        log_info "提示：检查 Java 版本和 Defects4J 配置"
        exit 1
    else
        log_success "Defects4J 项目编译成功"
    fi

    # 使用诊断函数检查路径
    if diagnose_defects4j_paths "$(pwd)"; then
        log_success "路径结构验证通过"
    else
        log_warning "路径结构验证失败，但将继续执行（ARJA 会尝试自动适配）"
    fi

    log_info "运行原始测试（验证环境）..."
    TEST_RESULT=$("$DEFECTS4J_HOME/framework/bin/defects4j" test 2>&1) || true
    echo "$TEST_RESULT" > "$LOG_DIR/defects4j_test_original.log"
    if echo "$TEST_RESULT" | grep -q "Failing tests: [1-9]"; then
        log_success "项目检出成功，存在失败测试（符合预期）"
    else
        log_warning "未检测到失败测试，可能项目状态异常！"
    fi
}

run_arja_with_full_logging() {
    log_info "启动 ARJA 修复流程（Java 11 升级版 + Defects4J v3.0.1）..."
    use_java11
    cd "$WORK_DIR/$PROJECT_NAME"

    # 使用 defects4j export 获取路径（这是最可靠的方式）
    log_info "获取项目路径信息..."
    SRC_DIR=$(defects4j export -p dir.src.classes 2>/dev/null || echo "")
    BIN_DIR=$(defects4j export -p dir.bin.classes 2>/dev/null || echo "")
    TEST_DIR=$(defects4j export -p dir.bin.tests 2>/dev/null || echo "")
    CP_TEST=$(defects4j export -p cp.test 2>/dev/null || echo "")

    # 如果 export 失败，尝试使用相对路径
    if [ -z "$BIN_DIR" ] || [ -z "$TEST_DIR" ]; then
        log_warning "defects4j export 获取路径失败，尝试使用相对路径..."
        CURRENT_DIR=$(pwd)
        
        # 尝试检测实际路径
        if [ -d "build/classes" ]; then
            BIN_DIR="build/classes"
            log_info "检测到 build/classes 目录"
        elif [ -d "target/classes" ]; then
            BIN_DIR="target/classes"
            log_info "检测到 target/classes 目录"
        fi
        
        if [ -d "build/tests" ]; then
            TEST_DIR="build/tests"
            log_info "检测到 build/tests 目录"
        elif [ -d "target/test-classes" ]; then
            TEST_DIR="target/test-classes"
            log_info "检测到 target/test-classes 目录"
        fi
        
        if [ -z "$SRC_DIR" ]; then
            if [ -d "src/main/java" ]; then
                SRC_DIR="src/main/java"
            elif [ -d "source" ]; then
                SRC_DIR="source"
            fi
        fi
    fi

    log_info "项目路径信息:"
    log_info "  源码: ${SRC_DIR:-未找到}"
    log_info "  类文件: ${BIN_DIR:-未找到}"
    log_info "  测试类: ${TEST_DIR:-未找到}"
    log_info "  Classpath: ${CP_TEST:0:60}..."

    # 验证关键路径存在
    MISSING_DIRS=()
    for d in "$SRC_DIR" "$BIN_DIR" "$TEST_DIR"; do
        if [ -z "$d" ] || [ ! -d "$d" ]; then
            MISSING_DIRS+=("$d")
        fi
    done
    
    if [ ${#MISSING_DIRS[@]} -gt 0 ]; then
        log_error "以下关键目录不存在或无法确定:"
        for d in "${MISSING_DIRS[@]}"; do
            log_error "  - $d"
        done
        log_info "当前工作目录: $(pwd)"
        log_info "目录结构："
        ls -la | head -15
        log_info "提示："
        log_info "  1. 确保 Defects4J 项目已正确编译: defects4j compile"
        log_info "  2. 检查 Defects4J 版本: defects4j version"
        log_info "  3. 检查项目是否正确检出"
        exit 1
    fi

    # 清理旧结果
    rm -rf "$PATCH_OUTPUT_ROOT"

    cd "$ARJA_HOME"

    # 构建完整的 classpath（包含所有 lib 目录的 jar）
    CLASSPATH="$ARJA_HOME/target/Arja-0.0.1-SNAPSHOT.jar"
    for jar in "$ARJA_HOME"/lib/*.jar; do
        CLASSPATH="$CLASSPATH:$jar"
    done
    
    log_info "Classpath: ${CLASSPATH:0:100}..."
    
    # 构建命令（Java 11 兼容 + Defects4JFaultLocalizer）
    # 关键修复：
    # 1. 使用完整 classpath（包含所有依赖）
    # 2. 添加 externalProjRoot 参数（Defects4JFaultLocalizer 需要）
    # 3. 禁用所有过滤规则避免修改点被过滤
    # 4. 使用 ExternalTestExecutor 更稳定
    # 关键修复：添加 -Dpercentage 0.1 参数，只运行 10% 的测试
    # 这样可以大幅减少测试时间，避免超时
    CMD="java --add-opens java.base/java.lang=ALL-UNNAMED \
         --add-opens java.base/java.util=ALL-UNNAMED \
         -cp \"$CLASSPATH\" us.msu.cse.repair.Main Arja \
    -DsrcJavaDir "$WORK_DIR/$PROJECT_NAME/src/main/java" \
    -DbinJavaDir "$WORK_DIR/$PROJECT_NAME/$BIN_DIR" \
    -DbinTestDir "$WORK_DIR/$PROJECT_NAME/$TEST_DIR" \
    -Ddependences "$CP_TEST" \
    -DexternalProjRoot "$ARJA_HOME/external" \
    -DpopulationSize $POPULATION_SIZE \
    -DmaxGenerations $MAX_GENERATIONS \
    -DwaitTime $WAIT_TIME \
    -DpatchOutputRoot "$PATCH_OUTPUT_ROOT" \
    -Dpercentage 1 \
    -Dthr 0.1 \
    -DtestFiltered false \
    -DtestExecutorName "$TEST_EXECUTOR" \
    -DnoveltySearchMode none \
    -DmiFilterRule false \
    -DmanipulationFilterRule false \
    -DingredientFilterRule false \
    -DdiffFormat true \
    -DseedLineGenerated false"

    log_info "▶ 执行命令（日志: $ARJA_LOG）"
    log_info "CMD: $CMD"

    # 启动并重定向日志
    eval "$CMD" > "$ARJA_LOG" 2>&1 &
    ARJA_PID=$!

    log_info "开始实时监控 ARJA 运行状态..."

    # 监控循环
    while kill -0 $ARJA_PID 2>/dev/null; do
        # 检查补丁
        if [ -d "$PATCH_OUTPUT_ROOT" ]; then
            # 查找 Patch_*.txt 或 diff 文件
            PATCH_COUNT=$(find "$PATCH_OUTPUT_ROOT" \( -name "Patch_*.txt" -o -name "diff" \) -type f 2>/dev/null | wc -l)
            if [ "$PATCH_COUNT" -gt 0 ]; then
                log_success "检测到 $PATCH_COUNT 个补丁！"
                FIRST_PATCH=$(find "$PATCH_OUTPUT_ROOT" \( -name "Patch_*.txt" -o -name "diff" \) -type f | head -1)
                echo "=== 第一个补丁 ==="
                cat "$FIRST_PATCH"
                echo "=================="
                wait $ARJA_PID 2>/dev/null || true
                return 0
            fi
        fi

        # 检查日志关键词
        if grep -q "All tests passed" "$ARJA_LOG" 2>/dev/null; then
            log_success "日志中发现成功信号！"
        fi

        ERR_LINES=$(grep -i "exception\|error\|fail.*load\|timeout\|crash" "$ARJA_LOG" 2>/dev/null | tail -n 1)
        if [ -n "$ERR_LINES" ]; then
            log_warning "潜在错误: $ERR_LINES"
        fi

        sleep 10
    done

    wait $ARJA_PID
    log_info "ARJA 进程已结束"
}

analyze_results() {
    log_info "开始结果分析..."

    # 1. 补丁是否存在
    if [ -d "$PATCH_OUTPUT_ROOT" ]; then
        # 查找 .txt 补丁描述文件或 diff 文件
        PATCHES=$(find "$PATCH_OUTPUT_ROOT" -name "Patch_*.txt" -o -name "diff" -type f 2>/dev/null)
        if [ -n "$PATCHES" ]; then
            log_success "成功生成补丁！"
            echo "$PATCHES"
        else
            log_warning "补丁目录存在但无补丁文件（可能无有效修复）"
        fi
    else
        log_warning "未找到补丁目录（ARJA 可能未进入修复阶段）"
    fi

    # 2. 分析日志
    log_info "ARJA 日志摘要（最后 20 行）:"
    tail -n 20 "$ARJA_LOG"

    # 3. 判断可能原因（针对 Java 11 和 Defects4J v3.0.1）
    if grep -qi "UnsupportedClassVersionError\|class file has wrong version" "$ARJA_LOG"; then
        log_error "字节码版本错误：可能是编译时未使用 --release 11"
    elif grep -qi "IllegalAccessException\|module.*does not.*open" "$ARJA_LOG"; then
        log_error "模块系统访问错误：需要添加 --add-opens JVM 参数"
    elif grep -qi "The build directory.*is not specified" "$ARJA_LOG"; then
        log_error "路径配置错误：检查 Defects4J v3.0.1 路径结构（build/ 目录）"
    elif grep -qi "No failing tests detected" "$ARJA_LOG"; then
        log_error "ARJA 未检测到失败测试（Defects4J 路径或 classpath 配置错误）"
    elif grep -qi "timeout" "$ARJA_LOG"; then
        log_warning "大量测试超时，建议增大 -DwaitTime"
    elif grep -q "Generation.*completed" "$ARJA_LOG" && ! grep -q "All tests passed" "$ARJA_LOG"; then
        log_info "搜索完成但未找到有效补丁（可能需增大种群或换更简单 bug）"
    elif grep -q "One fitness evaluation starts" "$ARJA_LOG" && ! grep -q "One fitness evaluation is finished" "$ARJA_LOG"; then
        log_error "关键问题：评估开始但未完成！"
        log_error "可能原因："
        log_error "  1. 测试执行超时（当前 waitTime: ${WAIT_TIME}ms）"
        log_error "  2. 测试执行器卡住"
        log_error "  3. 编译或类加载问题"
        log_info "建议解决方案："
        log_info "  1. 增加 waitTime: -DwaitTime 180000"
        log_info "  2. 使用 ExternalTestExecutor: -DtestExecutorName ExternalTestExecutor"
        log_info "  3. 启用测试过滤: -DtestFiltered true -Dpercentage 0.1"
        log_info "  4. 减少测试数量或使用更简单的 bug"
        log_info "详细排查指南请参考: TROUBLESHOOTING.md"
    fi
}

main() {
    echo "==============================================="
    echo "   ARJA 验证脚本（Java 11 升级版 + Defects4J v3.0.1）"
    echo "   项目: $PROJECT_NAME"
    echo "   日志: $ARJA_LOG"
    echo "==============================================="

    # 环境检查
    if [ ! -d "$DEFECTS4J_HOME" ]; then 
        log_error "Defects4J 未找到: $DEFECTS4J_HOME"
        log_info "请设置 DEFECTS4J_HOME 环境变量或修改脚本中的路径"
        exit 1
    fi
    
    # 检查 Defects4J 版本
    if [ -f "$DEFECTS4J_HOME/framework/bin/defects4j" ]; then
        DEFECTS4J_VERSION=$("$DEFECTS4J_HOME/framework/bin/defects4j" version 2>&1 | head -1 || echo "unknown")
        log_info "Defects4J 版本: $DEFECTS4J_VERSION"
    fi
    
    if [ ! -d "$JAVA11_HOME" ]; then 
        log_error "Java 11 未找到: $JAVA11_HOME"
        log_info "请安装 Java 11 或修改脚本中的 JAVA11_HOME 路径"
        exit 1
    fi
    
    if [ ! -d "$ARJA_HOME" ]; then 
        log_error "ARJA 未找到: $ARJA_HOME"
        exit 1
    fi
    
    # 验证 Java 版本
    use_java11
    JAVA_VERSION=$($JAVA_HOME/bin/java -version 2>&1 | head -1)
    log_info "使用 Java: $JAVA_VERSION"

    START_TIME=$(date +%s)
    use_java11

    log_info "编译 ARJA..."
    compile_arja

    log_info "准备 Defects4J 项目..."
    prepare_defects4j_project

    log_info "启动 ARJA 修复..."
    run_arja_with_full_logging

    log_info "分析结果..."
    analyze_results

    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))
    log_success "脚本执行完毕（耗时: ${DURATION} 秒）"
    log_info "详细日志: $ARJA_LOG"
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
