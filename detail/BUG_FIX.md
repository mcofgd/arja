# ARJA 快速验证 - Bug 修复说明

## 问题：ArrayIndexOutOfBoundsException

### 错误信息
```
Exception in thread "main" java.lang.ArrayIndexOutOfBoundsException: Index 5 out of bounds for length 5
    at jmetal.operators.selection.BinaryTournament2.execute(BinaryTournament2.java:79)
```

### 原因
- 种群大小设置为 **5** 太小
- NSGA-II 的 `BinaryTournament2` 选择操作需要至少 **10** 个个体才能正常工作
- 当种群太小时，选择操作会尝试访问不存在的索引

### 解决方案
将种群大小从 5 增加到 **10**（最小安全值）

### 修复后的参数
```bash
-DpopulationSize 10      # 最小安全值（原来是 5）
-DmaxGenerations 2       # 只运行 2 代（快速验证）
```

## 为什么需要至少 10 个个体？

NSGA-II 算法的工作流程：
1. **初始化种群**：创建 N 个个体
2. **选择操作**：使用 BinaryTournament2 选择父代
3. **交叉和变异**：生成子代
4. **环境选择**：从父代+子代中选择下一代

BinaryTournament2 需要：
- 至少 2 个个体进行锦标赛选择
- NSGA-II 的非支配排序需要足够的个体
- 通常建议至少 10-20 个个体

## 快速验证的推荐参数

### 最小配置（快速验证）
```bash
-DpopulationSize 10      # 最小安全值
-DmaxGenerations 2       # 只运行 2 代
-DwaitTime 120000        # 120 秒超时
```

### 标准配置（实际使用）
```bash
-DpopulationSize 40      # 默认值，推荐
-DmaxGenerations 50      # 默认值
-DwaitTime 60000        # 60 秒超时
```

## 验证成功的标志

运行修复后的脚本，你应该看到：
```
One fitness evaluation starts...
Compiling modified sources...
Compilation successful, starting test execution...
Invoking test executor...
Getting test executor, sample tests: X
Test executor created, running tests...
Tests run completed, status: false, exceptional: false
One fitness evaluation is finished...  ← 关键！
```

如果看到 "One fitness evaluation is finished..."，说明验证成功！

## 不需要设置断点

这个问题不需要设置断点调试，因为：
1. **错误原因明确**：种群大小太小
2. **解决方案简单**：增加种群大小到 10
3. **已经修复**：脚本已更新

如果还有其他问题，可以：
1. 查看日志中的详细错误信息
2. 检查是否有其他异常
3. 使用 `-DpopulationSize 20` 进一步增加种群大小

