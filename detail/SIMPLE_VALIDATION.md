# ARJA 简单验证方案

## 问题分析

### 为什么测试过滤仍然卡住？

从代码分析发现，即使使用了 `-Dpercentage 0.05`，ARJA 的逻辑是：
1. **第一步**：使用 5% 的测试（sample tests）进行快速评估
2. **第二步**：如果第一步通过，**还会运行全部测试**（2261 个）！

这就是为什么即使设置了 `percentage`，仍然会卡住的原因。

## 解决方案：使用更简单的数据集

ARJA 主要支持 Defects4J，但我们可以：

### 方案 1：使用 Defects4J 中最简单的 bug（推荐）

**推荐项目（测试数量少）**：
- **Chart_1b**：测试数量较少，通常 < 100 个
- **Math_1b**：相对简单
- **Time_1b**：测试数量中等

```bash
# 使用 Chart_1b（测试数量少）
export DEFECTS4J_HOME=/path/to/defects4j
cd /tmp
$DEFECTS4J_HOME/framework/bin/defects4j checkout -p Chart -v 1b -w Chart_1_buggy
cd Chart_1_buggy
$DEFECTS4J_HOME/framework/bin/defects4j compile

# 检查测试数量
defects4j test | grep "Failing tests"

# 运行 ARJA（不需要测试过滤，因为测试本身就少）
cd ~/arja
SRC_DIR=$(defects4j export -p dir.src.classes)
BIN_DIR=$(defects4j export -p dir.bin.classes)
TEST_DIR=$(defects4j export -p dir.bin.tests)
CP_TEST=$(defects4j export -p cp.test)

java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -cp "lib/*:bin" us.msu.cse.repair.Main Arja \
     -DsrcJavaDir /tmp/Chart_1_buggy/$SRC_DIR \
     -DbinJavaDir /tmp/Chart_1_buggy/$BIN_DIR \
     -DbinTestDir /tmp/Chart_1_buggy/$TEST_DIR \
     -Ddependences $(echo $CP_TEST | tr ',' ':') \
     -DpopulationSize 10 \
     -DmaxGenerations 3 \
     -DwaitTime 120000 \
     -DtestExecutorName ExternalTestExecutor \
     -DtestFiltered false \
     -DpatchOutputRoot /tmp/arja_patches_chart_1
```

### 方案 2：修改代码禁用第二步测试（快速验证）

如果你想快速验证 ARJA 是否能工作，可以临时修改代码，禁用第二步测试：

```java
// 在 ArjaProblem.java 的 invokeTestExecutor 方法中
// 注释掉这部分：
/*
if (status && percentage != null && percentage < 1) {
    testExecutor = getTestExecutor(compiledClasses, positiveTests);
    status = testExecutor.runTests();
}
*/
```

### 方案 3：创建一个最小测试用例（最推荐用于验证）

创建一个简单的 Java 项目来验证 ARJA：

```bash
# 创建测试项目
mkdir -p /tmp/simple_test/{src,test,build}
cd /tmp/simple_test

# 创建有 bug 的源代码
cat > src/Buggy.java << 'EOF'
public class Buggy {
    public int add(int a, int b) {
        return a - b;  // Bug: 应该是 a + b
    }
}
EOF

# 创建测试
cat > test/BuggyTest.java << 'EOF'
import org.junit.Test;
import static org.junit.Assert.*;

public class BuggyTest {
    @Test
    public void testAdd() {
        Buggy b = new Buggy();
        assertEquals(5, b.add(2, 3));  // 这个测试会失败
    }
}
EOF

# 编译
javac -d build src/Buggy.java
javac -cp "build:lib/junit-4.13.2.jar" -d build test/BuggyTest.java

# 运行 ARJA
cd ~/arja
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -cp "lib/*:bin" us.msu.cse.repair.Main Arja \
     -DsrcJavaDir /tmp/simple_test/src \
     -DbinJavaDir /tmp/simple_test/build \
     -DbinTestDir /tmp/simple_test/build \
     -Ddependences /tmp/simple_test/lib/junit-4.13.2.jar \
     -DpopulationSize 5 \
     -DmaxGenerations 2 \
     -DwaitTime 60000 \
     -DtestExecutorName ExternalTestExecutor \
     -DpatchOutputRoot /tmp/arja_patches_simple
```

## 最简单的验证方法（推荐）

### 使用 Chart_1b（测试数量最少）

```bash
#!/bin/bash
# 快速验证脚本

DEFECTS4J_HOME="$HOME/defects4j"
ARJA_HOME="$HOME/arja"
PROJECT="Chart_1b"

echo "准备最简单的 Defects4J 项目: $PROJECT"

# 准备项目
cd /tmp
$DEFECTS4J_HOME/framework/bin/defects4j checkout -p Chart -v 1b -w Chart_1_buggy
cd Chart_1_buggy
$DEFECTS4J_HOME/framework/bin/defects4j compile

# 检查测试数量
TEST_COUNT=$(defects4j test 2>&1 | grep -oP "Running \K[0-9]+" | head -1)
echo "测试数量: $TEST_COUNT"

# 获取路径
SRC_DIR=$(defects4j export -p dir.src.classes)
BIN_DIR=$(defects4j export -p dir.bin.classes)
TEST_DIR=$(defects4j export -p dir.bin.tests)
CP_TEST=$(defects4j export -p cp.test)

echo "运行 ARJA（最小配置）..."
cd $ARJA_HOME
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -cp "lib/*:bin" us.msu.cse.repair.Main Arja \
     -DsrcJavaDir /tmp/Chart_1_buggy/$SRC_DIR \
     -DbinJavaDir /tmp/Chart_1_buggy/$BIN_DIR \
     -DbinTestDir /tmp/Chart_1_buggy/$TEST_DIR \
     -Ddependences $(echo $CP_TEST | tr ',' ':') \
     -DpopulationSize 5 \
     -DmaxGenerations 2 \
     -DwaitTime 120000 \
     -DtestExecutorName ExternalTestExecutor \
     -DtestFiltered false \
     -DpatchOutputRoot /tmp/arja_patches_chart_1_simple
```

## 为什么测试过滤仍然卡住？

### 根本原因

ARJA 的两阶段测试策略：
1. **阶段 1**：使用 `percentage` 比例的测试（如 5% = 113 个测试）
2. **阶段 2**：如果阶段 1 通过，运行**全部测试**（2261 个）

即使设置了 `-Dpercentage 0.05`，如果生成的补丁能通过 5% 的测试，ARJA 会继续运行全部 2261 个测试来验证，这就是为什么会卡住。

### 解决方案

1. **使用测试数量少的项目**（Chart_1b 等）
2. **修改代码禁用第二阶段**（仅用于验证）
3. **设置 percentage = 1.0**（但这样就没有过滤效果了）

## 推荐的验证流程

### 步骤 1：选择简单的项目

```bash
# 查看各项目的测试数量（选择测试最少的）
for proj in Chart Math Time Lang; do
    defects4j checkout -p $proj -v 1b -w /tmp/${proj}_1b
    cd /tmp/${proj}_1b
    defects4j compile
    TEST_COUNT=$(defects4j test 2>&1 | grep -oP "Running \K[0-9]+" | head -1)
    echo "$proj: $TEST_COUNT tests"
    cd ..
done
```

### 步骤 2：使用最小配置运行

```bash
# 使用测试最少的项目，最小参数配置
-DpopulationSize 5      # 最小种群
-DmaxGenerations 2      # 只运行 2 代
-DwaitTime 120000       # 120 秒超时
-DtestFiltered false    # 不启用过滤（因为测试本身就少）
```

### 步骤 3：验证 ARJA 功能

目标不是找到补丁，而是验证：
- ✅ ARJA 能正常启动
- ✅ 能进行缺陷定位
- ✅ 能解析 AST
- ✅ 能执行测试评估
- ✅ 能完成至少一次评估

## 总结

**最快验证方法**：
1. 使用 **Chart_1b**（测试数量最少）
2. 使用最小参数（populationSize=5, maxGenerations=2）
3. 不启用测试过滤（因为测试本身就少）
4. 目标：验证 ARJA 能完成评估，而不是找到补丁

这样可以在几分钟内验证 ARJA 是否正常工作！

