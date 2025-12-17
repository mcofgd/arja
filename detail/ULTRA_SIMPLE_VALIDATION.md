# ARJA 超简单验证方案

## 问题：为什么卡住？

### 根本原因

即使使用 GenProg，Chart_1b 有 **2199 个测试**，每个个体评估都要运行这些测试：
- 10 个个体 × 2199 个测试 = **21990 次测试执行**
- 即使每个测试 0.1 秒，也需要 **36 分钟**
- 如果某些测试很慢，可能需要**数小时**

### 解决方案：必须启用测试过滤

**关键**：必须使用 `-DtestFiltered true -Dpercentage 0.01`（只使用 1% 的测试）

## 超简单验证命令

### 方案 1：使用测试过滤（推荐）

```bash
cd ~/arja

# 准备 Chart_1b
cd /tmp/Chart_1_buggy
SRC_DIR=$(defects4j export -p dir.src.classes)
BIN_DIR=$(defects4j export -p dir.bin.classes)
TEST_DIR=$(defects4j export -p dir.bin.tests)
CP_TEST=$(defects4j export -p cp.test)

# 运行 GenProg（关键：启用测试过滤，只使用 1% 的测试）
cd ~/arja
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -cp "lib/*:bin" us.msu.cse.repair.Main GenProg \
     -DsrcJavaDir /tmp/Chart_1_buggy/$SRC_DIR \
     -DbinJavaDir /tmp/Chart_1_buggy/$BIN_DIR \
     -DbinTestDir /tmp/Chart_1_buggy/$TEST_DIR \
     -Ddependences $(echo $CP_TEST | tr ',' ':') \
     -DpopulationSize 5 \
     -DmaxGenerations 1 \
     -DwaitTime 180000 \
     -DtestExecutorName ExternalTestExecutor \
     -DtestFiltered true \
     -Dpercentage 0.01 \
     -DpatchOutputRoot /tmp/arja_ultra_simple
```

**关键参数**：
- `-DtestFiltered true`：启用测试过滤
- `-Dpercentage 0.01`：只使用 **1% 的测试**（约 22 个测试，而不是 2199 个）
- `-DpopulationSize 5`：最小种群
- `-DmaxGenerations 1`：只运行 1 代（快速验证）

### 方案 2：使用最简单的项目（如果有）

如果可能，使用测试数量更少的项目。

## 为什么必须启用测试过滤？

### 数学计算

**不使用测试过滤**：
- Chart_1b: 2199 个测试
- 10 个个体 × 2 代 = 20 次评估
- 每次评估运行 2199 个测试
- 总测试执行：20 × 2199 = **43,980 次**
- 如果每个测试 0.1 秒 = **73 分钟**

**使用测试过滤（1%）**：
- Chart_1b: 2199 个测试 × 1% = 约 22 个测试
- 5 个个体 × 1 代 = 5 次评估
- 每次评估运行 22 个测试
- 总测试执行：5 × 22 = **110 次**
- 如果每个测试 0.1 秒 = **11 秒**

**速度提升：400 倍！**

## 验证成功的标志

运行后，你应该在 **几分钟内** 看到：
```
One fitness evaluation starts...
Compiling modified sources...
Compilation successful, starting test execution...
Invoking test executor...
Getting test executor, sample tests: 22  ← 只有 22 个测试！
Test executor created, running tests...
Tests run completed, status: false, exceptional: false
One fitness evaluation is finished...  ← 关键！
```

## 如果仍然卡住

1. **检查是否有测试进程在运行**：
   ```bash
   ps aux | grep JUnitTestRunner
   ```

2. **检查工作目录**：
   ```bash
   ls -la /tmp/working_*
   ```

3. **进一步减少测试数量**：
   ```bash
   -Dpercentage 0.005  # 只使用 0.5% 的测试（约 11 个）
   ```

4. **使用更小的种群**：
   ```bash
   -DpopulationSize 3
   -DmaxGenerations 1
   ```

## 总结

**关键点**：
1. ✅ **必须启用测试过滤**：`-DtestFiltered true -Dpercentage 0.01`
2. ✅ **使用最小参数**：`-DpopulationSize 5 -DmaxGenerations 1`
3. ✅ **目标不是找补丁**：只是验证 ARJA 能正常工作

使用测试过滤后，验证应该在 **几分钟内** 完成！

