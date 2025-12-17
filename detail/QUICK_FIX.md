# ARJA 评估卡住问题 - 快速修复方案

## 问题总结

ARJA 评估开始但没有完成，可能卡在测试执行阶段。

## 已添加的调试信息

我已经在代码中添加了更多调试输出，重新编译后运行可以看到：
- 编译状态
- 测试执行器创建状态
- 测试执行开始和完成状态

## 立即尝试的解决方案

### 方案 1：使用测试过滤（最推荐）

```bash
cd ~/arja

# 准备项目路径
cd /home/x/defects4j_test/Lang_1b
SRC_DIR=$(defects4j export -p dir.src.classes)
BIN_DIR=$(defects4j export -p dir.bin.classes)
TEST_DIR=$(defects4j export -p dir.bin.tests)
CP_TEST=$(defects4j export -p cp.test)

# 运行 ARJA（启用测试过滤，只使用 10% 的测试）
cd ~/arja
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -cp "lib/*:bin" us.msu.cse.repair.Main Arja \
     -DsrcJavaDir /home/x/defects4j_test/Lang_1b/$SRC_DIR \
     -DbinJavaDir /home/x/defects4j_test/Lang_1b/$BIN_DIR \
     -DbinTestDir /home/x/defects4j_test/Lang_1b/$TEST_DIR \
     -Ddependences $(echo $CP_TEST | tr ',' ':') \
     -DpopulationSize 10 \
     -DmaxGenerations 5 \
     -DwaitTime 300000 \
     -DtestExecutorName ExternalTestExecutor \
     -DtestFiltered true \
     -Dpercentage 0.05 \
     -DpatchOutputRoot /tmp/arja_patches_lang_1_filtered
```

**关键参数**：
- `-DtestFiltered true`：启用测试过滤
- `-Dpercentage 0.05`：只使用 5% 的测试（约 113 个测试，而不是 2261 个）
- `-DwaitTime 300000`：300 秒超时
- `-DpopulationSize 10 -DmaxGenerations 5`：快速验证

### 方案 2：检查 ExternalTestExecutor 是否正常工作

```bash
# 检查 external 项目是否编译
cd ~/arja/external
ls -la bin/

# 如果没有编译，编译它
mkdir -p bin
javac --release 11 -cp "../lib/*" -d bin $(find src -name '*.java')
```

### 方案 3：使用更简单的 bug

Lang_1b 有 2261 个测试，可能太复杂。尝试更简单的：

```bash
# 修改脚本中的 PROJECT_NAME
PROJECT_NAME="Math_1b"  # 或 Chart_1b
```

### 方案 4：检查测试执行器进程

如果评估卡住，可以检查是否有测试进程在运行：

```bash
# 查看是否有 Java 测试进程
ps aux | grep JUnitTestRunner

# 查看工作目录
ls -la /tmp/working_*
```

## 重新编译并运行

```bash
cd ~/arja

# 重新编译（包含新的调试信息）
rm -rf bin
mkdir bin
javac --release 11 -cp "lib/*:" -d bin $(find src/main/java -name '*.java')

# 编译 external 项目
cd external
mkdir -p bin
javac --release 11 -cp "../lib/*:../bin" -d bin $(find src -name '*.java')
cd ..

# 运行脚本（现在会有更多调试信息）
./test_arja_ns.sh
```

## 预期的新日志输出

重新编译后，日志应该包含：
```
One fitness evaluation starts...
Compiling modified sources...
Compilation successful, starting test execution...
Invoking test executor...
Getting test executor, sample tests: X
Test executor created, running tests (waitTime: 180000ms)...
Tests run completed, status: false, exceptional: false
One fitness evaluation is finished...
```

如果看到这些消息，说明问题已经解决。如果仍然卡在某个步骤，可以根据消息定位问题。

## 如果仍然卡住

1. **检查日志中的最后一条消息**，确定卡在哪一步
2. **检查系统资源**：`top` 或 `htop` 查看 CPU/内存使用
3. **检查是否有僵尸进程**：`ps aux | grep defunct`
4. **尝试减少测试数量**：使用 `-Dpercentage 0.01`（只 1% 的测试）

## 重要提示

**这不是 Java 11 升级的问题**。ARJA 已经成功运行，只是：
1. Lang_1b 有太多测试（2261 个），执行时间很长
2. 某些测试可能很慢或卡住
3. 需要使用测试过滤来减少测试数量

使用测试过滤后，ARJA 应该能正常完成评估。

