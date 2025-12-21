# ARJA percentage 参数缺失问题修复

## 🎯 问题描述

在 Java 11 + Defects4J v3.0.1 环境下，即使在启动脚本中设置了 `-Dpercentage 0.1`，ARJA 仍然运行全部 382 个测试，导致超时。

### 日志证据

```
Line 99-102:
DEBUG: getSamplePositiveTests() called
  percentage = null  ← 应该是 0.1
  positiveTests.size() = 382
  Returning all 382 tests (no sampling)
```

## 🔍 根本原因分析

### 问题链条

```
test_arja_ns.sh 启动命令
  -Dpercentage 0.1  ← 参数已设置
  ↓
Interpreter.getParameterStrings(args)
  parameters.put("percentage", "0.1")  ← 解析成功
  ↓
Interpreter.getBasicParameterSetting(parameterStrs)
  ❌ 没有处理 "percentage" 参数！
  ↓
返回的 parameters Map 中没有 "percentage"
  ↓
AbstractRepairProblem 构造函数
  percentage = (Double) parameters.get("percentage")
  percentage = null  ← 因为 Map 中没有这个键
  ↓
getSamplePositiveTests() 方法
  if (percentage == null || percentage == 1)
      return positiveTests;  ← 返回全部 382 个测试
```

### 关键发现

**`Interpreter.getBasicParameterSetting()` 方法缺少 `percentage` 参数的处理逻辑！**

在 `Interpreter.java` 第22-135行中，处理了很多参数：
- ✅ `binJavaDir`
- ✅ `testFiltered`
- ✅ `waitTime`
- ✅ `thr`
- ✅ `externalProjRoot`
- ❌ **`percentage`** ← 缺失！

## ✅ 修复方案

### 修复：添加 percentage 参数处理（Interpreter.java）

**位置**：`Interpreter.java` 第104-108行之后

**修改内容**：
```java
String testFilteredS = parameterStrs.get("testFiltered");
if (testFilteredS != null) {
    boolean testFiltered = Boolean.parseBoolean(testFilteredS);
    parameters.put("testFiltered", testFiltered);
}

// ✅ 关键修复：添加 percentage 参数处理
String percentageS = parameterStrs.get("percentage");
if (percentageS != null) {
    double percentage = Double.parseDouble(percentageS);
    parameters.put("percentage", percentage);
}

String seed_str = parameterStrs.get("seed");
```

**作用**：
1. 从 `parameterStrs` 中获取 `"percentage"` 字符串
2. 解析为 `double` 类型
3. 放入 `parameters` Map 中
4. 传递给 `AbstractRepairProblem` 构造函数

## 📊 预期效果

### 修复前
```
启动命令: -Dpercentage 0.1
  ↓
Interpreter.getParameterStrings(): {"percentage": "0.1"}
  ↓
Interpreter.getBasicParameterSetting(): {}  ← percentage 丢失
  ↓
AbstractRepairProblem: percentage = null
  ↓
getSamplePositiveTests(): 返回全部 382 个测试
```

### 修复后
```
启动命令: -Dpercentage 0.1
  ↓
Interpreter.getParameterStrings(): {"percentage": "0.1"}
  ↓
Interpreter.getBasicParameterSetting(): {"percentage": 0.1}  ← 正确传递
  ↓
AbstractRepairProblem: percentage = 0.1
  ↓
getSamplePositiveTests(): 返回 38 个测试（10%）
```

## 🚀 验证步骤

### 1. 重新编译

```bash
cd ~/arja
mvn clean package -DskipTests
```

### 2. 运行测试

```bash
./test_arja_ns.sh
```

### 3. 检查日志

查找以下关键信息：

```bash
grep "percentage = " logs/arja_*.log
```

应该看到：
```
DEBUG: getSamplePositiveTests() called
  percentage = 0.1  ← 不再是 null
  positiveTests.size() = 382
  Calculated sample size: 38 (10.0%)
✅ Sampled 38 tests from 382
Getting test executor, sample tests: 38
```

### 4. 验证成功标志

- ✅ `percentage = 0.1`（不是 `null`）
- ✅ `Calculated sample size: 38`
- ✅ `Getting test executor, sample tests: 38`
- ✅ 测试执行不再超时
- ✅ 种群开始正常进化

## 🔍 其他可能缺失的参数

通过对比 `ArjaProblem` 构造函数和 `Interpreter.getBasicParameterSetting()`，我发现以下参数也可能缺失：

### 已处理的参数
- ✅ `binJavaDir`
- ✅ `binTestDir`
- ✅ `srcJavaDir`
- ✅ `dependences`
- ✅ `tests`
- ✅ `thr`
- ✅ `externalProjRoot`
- ✅ `maxNumberOfModificationPoints`
- ✅ `jvmPath`
- ✅ `testExecutorName`
- ✅ `waitTime`
- ✅ `patchOutputRoot`
- ✅ `gzoltarDataDir`
- ✅ `ingredientMode`
- ✅ `diffFormat`
- ✅ `testFiltered`
- ✅ `seed`

### 可能缺失的参数（需要检查）
- ❓ `weight`（ArjaProblem 使用）
- ❓ `mu`（ArjaProblem 使用）
- ❓ `numberOfObjectives`（ArjaProblem 使用）
- ❓ `initializationStrategy`（ArjaProblem 使用）
- ❓ `miFilterRule`（ArjaProblem 使用）
- ❓ `maxNumberOfEdits`（ArjaProblem 使用）
- ❓ `manipulationFilterRule`（AbstractRepairProblem 使用）
- ❓ `ingredientFilterRule`（AbstractRepairProblem 使用）
- ❓ `seedLineGenerated`（AbstractRepairProblem 使用）

**建议**：检查这些参数是否也需要在 `Interpreter.getBasicParameterSetting()` 中添加处理逻辑。

## 📝 经验教训

### 问题根源
1. **参数传递链条断裂**：参数在中间环节丢失
2. **缺少参数验证**：没有检查关键参数是否成功传递
3. **调试日志不足**：早期没有输出 `percentage` 的值

### 改进建议
1. **添加参数验证**：在 `AbstractRepairProblem` 构造函数中检查关键参数
2. **完善调试日志**：输出所有关键参数的值
3. **文档化参数列表**：明确列出所有支持的参数及其默认值

## 🎯 总结

### 根本原因
**`Interpreter.getBasicParameterSetting()` 方法缺少 `percentage` 参数的处理逻辑**，导致参数在传递链条中丢失。

### 核心修复
在 `Interpreter.java` 第108行后添加：
```java
String percentageS = parameterStrs.get("percentage");
if (percentageS != null) {
    double percentage = Double.parseDouble(percentageS);
    parameters.put("percentage", percentage);
}
```

### 预期结果
- ✅ `percentage` 参数正确传递
- ✅ 测试采样生效（382 → 38）
- ✅ 测试时间大幅减少（>1800s → ~180s）
- ✅ 种群正常进化，生成补丁

---

**修复时间**：2025-12-18  
**修复文件**：`src/main/java/us/msu/cse/repair/Interpreter.java`  
**修复类型**：参数处理逻辑补全