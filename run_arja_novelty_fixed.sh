#!/bin/bash

# ARJA + Novelty Search 测试脚本（修复版）

# 项目路径: ~/arja
# 支持 Novelty Search 模式 (lightweight/full)

set -e

# 配置变量
DEFECTS4J_HOME="$HOME/defects4j"
JAVA11_HOME="/usr/lib/jvm/java-11-openjdk-amd64"
WORK_DIR="$HOME/defects4j_test"
PROJECT_NAME="Lang_1b"  # 可修改为其他 Defects4J 项目
ARJA_HOME="$HOME/arja"

# Novelty Search 配置
NOVELTY_MODE="lightweight"  # 可选: none, lightweight, full
NOVELTY_K=15
NOVELTY_ARCHIVE_SIZE=200

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# 使用Java 11
use_java11() {
    export JAVA_HOME="$JAVA11_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
    log_info "使用Java 11: $($JAVA_HOME/bin/java -version 2>&1 | head -1)"
}

# 检查ARJA在Java 11上的兼容性
check_arja_java11() {
    log_info "检查ARJA在Java 11上的兼容性..."
    
    if [ ! -d "$ARJA_HOME" ]; then
        log_error "ARJA项目目录不存在: $ARJA_HOME"
        return 1
    fi
    
    cd "$ARJA_HOME"
    
    # 检查编译目录是否存在
    if [ ! -d "bin" ]; then
        log_warning "bin目录不存在，尝试编译ARJA..."
        compile_arja
    fi
    
    # 使用正确的参数检查ARJA
    if java -cp "lib/*:bin" us.msu.cse.repair.Main -listParameters 2>&1 | grep -q "Parameters available"; then
        log_success "ARJA可以在Java 11上运行"
        return 0
    else
        log_error "ARJA在Java 11上运行失败"
        return 1
    fi
}

# 编译ARJA项目
compile_arja() {
    log_info "编译ARJA项目..."
    cd "$ARJA_HOME"
    
    # 清理旧的编译文件
    rm -rf bin
    mkdir -p bin
    
    # 编译源代码
    javac -cp "lib/*:" -d bin $(find src/main/java -name '*.java')
    
    # 编译外部项目（如果存在）
    if [ -d "external/src" ]; then
        mkdir -p external/bin
        javac -cp "lib/*:bin" -d external/bin $(find external/src -name '*.java')
    fi
    
    log_success "ARJA编译完成"
}

# 准备Defects4J项目
prepare_defects4j_project() {
    log_info "准备Defects4J项目: $PROJECT_NAME"
    
    # 创建工作目录
    mkdir -p "$WORK_DIR"
    cd "$WORK_DIR"
    
    # 解析项目名称 (格式: Project_IDb)
    PROJECT_ID=$(echo "$PROJECT_NAME" | cut -d'_' -f1)
    BUG_ID=$(echo "$PROJECT_NAME" | cut -d'_' -f2 | sed 's/b$//')
    
    # 检查项目是否已存在
    if [ ! -d "$PROJECT_NAME" ]; then
        log_info "检出Defects4J项目: $PROJECT_ID bug $BUG_ID"
        "$DEFECTS4J_HOME/framework/bin/defects4j" checkout -p "$PROJECT_ID" -v "${BUG_ID}b" -w "$PROJECT_NAME"
    else
        log_info "项目已存在，跳过检出"
    fi
    
    # 编译项目
    cd "$PROJECT_NAME"
    log_info "编译Defects4J项目..."
    "$DEFECTS4J_HOME/framework/bin/defects4j" compile
    
    log_success "Defects4J项目准备完成"
}

# 运行ARJA (支持Novelty Search)
run_arja() {
    log_info "运行ARJA修复 (Novelty Search模式: $NOVELTY_MODE)..."
    
    use_java11
    
    cd "$WORK_DIR/$PROJECT_NAME"
    
    # 获取项目信息
    SRC_DIR=$(defects4j export -p dir.src.classes)
    BIN_DIR=$(defects4j export -p dir.bin.classes)
    TEST_DIR=$(defects4j export -p dir.bin.tests)
    CP_TEST=$(defects4j export -p cp.test)
    
    log_info "项目信息:"
    log_info "  源码目录: $SRC_DIR"
    log_info "  编译目录: $BIN_DIR"
    log_info "  测试目录: $TEST_DIR"
    
    # 构建补丁输出目录
    PATCH_OUTPUT_ROOT="$WORK_DIR/arja_patches_${PROJECT_NAME}_${NOVELTY_MODE}"
    
    # 使用Java 11运行ARJA
    cd "$ARJA_HOME"
    
    log_info "开始ARJA修复过程 (使用Java 11)..."
    
    # 基础命令 - 修复：使用 -DtestFiltered false 而不是 -DtestFilterStrategy none
    # Java 11 兼容性：添加模块系统访问权限
    CMD="java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED -cp \"lib/*:bin\" us.msu.cse.repair.Main Arja \
    -DsrcJavaDir \"$WORK_DIR/$PROJECT_NAME/$SRC_DIR\" \
    -DbinJavaDir \"$WORK_DIR/$PROJECT_NAME/$BIN_DIR\" \
    -DbinTestDir \"$WORK_DIR/$PROJECT_NAME/$TEST_DIR\" \
    -Ddependences \"$CP_TEST\" \
    -DpopulationSize 40 \
    -DmaxGenerations 10 \
    -DwaitTime 30000 \
    -DpatchOutputRoot \"$PATCH_OUTPUT_ROOT\" \
    -DtestFiltered false \
    -DnoveltySearchMode \"$NOVELTY_MODE\""
    
    # 根据Novelty模式添加额外参数
    if [ "$NOVELTY_MODE" != "none" ]; then
        CMD="$CMD -DnoveltyKNeighbors $NOVELTY_K -DnoveltyArchiveSize $NOVELTY_ARCHIVE_SIZE"
    fi
    
    # 执行命令
    log_info "执行命令: $CMD"
    eval $CMD
}

# 检查结果
check_results() {
    log_info "检查ARJA运行结果..."
    
    PATCH_OUTPUT_ROOT="$WORK_DIR/arja_patches_${PROJECT_NAME}_${NOVELTY_MODE}"
    
    if [ -d "$PATCH_OUTPUT_ROOT" ]; then
        PATCH_FILES=$(find "$PATCH_OUTPUT_ROOT" -name "*.patch" -type f 2>/dev/null)
        if [ -n "$PATCH_FILES" ]; then
            log_success "找到补丁文件:"
            echo "$PATCH_FILES"
            FIRST_PATCH=$(echo "$PATCH_FILES" | head -1)
            echo "=== 补丁内容 ==="
            cat "$FIRST_PATCH"
            echo "================"
        else
            log_warning "补丁目录存在但未找到补丁文件"
            ls -la "$PATCH_OUTPUT_ROOT"
        fi
    else
        log_warning "未找到补丁目录: $PATCH_OUTPUT_ROOT"
        # 检查ARJA根目录下的可能输出
        find "$ARJA_HOME" -name "patches*" -type d 2>/dev/null | while read dir; do
            echo "检查目录: $dir"
            ls -la "$dir" 2>/dev/null || echo "  空目录或无法访问"
        done
    fi
}

main() {
    echo "=================================================="
    echo "   ARJA + Novelty Search 测试脚本（修复版）"
    echo "   项目路径: $ARJA_HOME"
    echo "   Novelty模式: $NOVELTY_MODE"
    echo "=================================================="
    
    # 检查依赖
    if [ ! -d "$DEFECTS4J_HOME" ]; then
        log_error "Defects4J未安装或路径错误: $DEFECTS4J_HOME"
        exit 1
    fi
    
    if [ ! -d "$JAVA11_HOME" ]; then
        log_error "Java 11未安装或路径错误: $JAVA11_HOME"
        exit 1
    fi
    
    use_java11
    
    # 检查ARJA兼容性
    if check_arja_java11; then
        log_success "ARJA兼容性检查通过"
        
        # 强制重新编译以确保包含最新修改
        log_info "重新编译ARJA以包含最新修改..."
        compile_arja
        
        # 准备Defects4J项目
        prepare_defects4j_project
        
        # 运行ARJA
        run_arja
        
        # 检查结果
        check_results
        
    else
        log_error "ARJA兼容性检查失败"
        exit 1
    fi
    
    log_success "脚本执行完成"
}

# 如果作为脚本直接运行
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi

