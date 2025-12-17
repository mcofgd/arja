#!/bin/bash

# 测试Defects4J故障定位器
# 使用Lang_1b项目进行测试

echo "=========================================="
echo "测试Defects4J故障定位器"
echo "=========================================="

# 设置环境
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# 项目路径
ARJA_JAR="/home/x/arja/target/Arja-0.0.1-SNAPSHOT.jar"
BUGGY_DIR="/home/x/defects4j_test/Lang_1b"
LOG_DIR="/home/x/defects4j_test/logs"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
LOG_FILE="$LOG_DIR/defects4j_localizer_test_${TIMESTAMP}.log"

# 创建日志目录
mkdir -p "$LOG_DIR"

echo "Java版本:"
java -version 2>&1 | head -3

echo ""
echo "ARJA JAR: $ARJA_JAR"
echo "测试项目: $BUGGY_DIR"
echo "日志文件: $LOG_FILE"
echo ""

# 检查项目是否存在
if [ ! -d "$BUGGY_DIR" ]; then
    echo "错误: 项目目录不存在: $BUGGY_DIR"
    exit 1
fi

# 检查JAR是否存在
if [ ! -f "$ARJA_JAR" ]; then
    echo "错误: ARJA JAR不存在: $ARJA_JAR"
    exit 1
fi

echo "开始测试..."
echo ""

# 构建完整的classpath，包含所有lib目录的jar
CLASSPATH="$ARJA_JAR"
for jar in /home/x/arja/lib/*.jar; do
    CLASSPATH="$CLASSPATH:$jar"
done

echo "Classpath: $CLASSPATH"
echo ""

# 运行ARJA，只进行故障定位（设置很小的代数和种群）
cd "$BUGGY_DIR"

java -cp "$CLASSPATH" us.msu.cse.repair.Main Arja \
    -DsrcJavaDir "$BUGGY_DIR" \
    -DbinJavaDir "$BUGGY_DIR/target/classes" \
    -DbinTestDir "$BUGGY_DIR/target/tests" \
    -Ddependences "$BUGGY_DIR/defects4j.build.classpath" \
    -DexternalProjRoot "$BUGGY_DIR" \
    -DpositiveTests org.apache.commons.lang3.math.NumberUtilsTest \
    -DnegativeTests org.apache.commons.lang3.math.NumberUtilsTest::testCreateNumber \
    -DmaxGeneration 1 \
    -DpopulationSize 1 \
    -DtestLevel method \
    2>&1 | tee "$LOG_FILE"

echo ""
echo "=========================================="
echo "测试完成"
echo "=========================================="
echo ""
echo "检查关键输出:"
echo ""

# 检查故障定位结果
echo "1. 故障定位结果:"
grep -A 5 "Fault localization starts" "$LOG_FILE" | head -10

echo ""
echo "2. 可疑行数量:"
grep "Number of faulty lines found" "$LOG_FILE"

echo ""
echo "3. 修改点数量:"
grep "Number of modification points" "$LOG_FILE"

echo ""
echo "4. 是否有错误:"
grep -i "error\|exception" "$LOG_FILE" | head -5

echo ""
echo "完整日志: $LOG_FILE"