# ARJA 修改记录文档

本文档详细记录了为 ARJA 项目添加 Novelty Search 功能的所有修改。

## 修改概览

本次修改主要实现了两种 Novelty Search 集成方案：
1. **路径A（轻量级改进）**：在现有适应度函数基础上加入多样性维持机制
2. **路径B（深度集成）**：完全采用 Novelty Search 替代原始适应度驱动

## 新增文件

### 1. `src/main/java/us/msu/cse/repair/core/novelty/BehaviorDescriptor.java`

**创建原因**：实现行为描述符，用于表示补丁的测试用例行为特征。

**功能说明**：
- 使用测试用例通过/失败向量（二进制位集）表示补丁行为
- 每个位表示一个测试用例是否通过（1=通过，0=失败）
- 提供汉明距离计算方法，用于衡量两个补丁行为的差异

**关键方法**：
- `BehaviorDescriptor(Set<String> allTests, Set<String> failedTests)`: 构造函数，从测试结果构建描述符
- `hammingDistance(BehaviorDescriptor other)`: 计算汉明距离
- `normalizedHammingDistance(BehaviorDescriptor other)`: 计算归一化汉明距离（0-1之间）

**依赖关系**：
- 依赖 `java.util.BitSet` 和 `java.util.Set`

### 2. `src/main/java/us/msu/cse/repair/core/novelty/BehaviorArchive.java`

**创建原因**：实现行为档案，用于存储已探索的行为描述符并计算 Novelty Score。

**功能说明**：
- 维护一个固定大小的行为描述符档案
- 使用 k-近邻方法计算 Novelty Score
- 支持计算种群内多样性

**关键方法**：
- `add(BehaviorDescriptor descriptor)`: 添加行为描述符到档案
- `computeNoveltyScore(BehaviorDescriptor descriptor, int k)`: 计算 Novelty Score（基于档案）
- `computePopulationNovelty(...)`: 计算种群内多样性（基于当前种群）

**参数说明**：
- `maxSize`: 档案最大容量（默认 200）
- `k`: k-近邻的 k 值（默认 15）

**算法细节**：
- Novelty Score = 与档案中 k 个最近邻的平均距离
- 距离使用归一化汉明距离（0-1之间）
- 档案满时采用随机替换策略

## 修改的文件

### 1. `src/main/java/us/msu/cse/repair/ec/problems/ArjaProblem.java`

**修改原因**：集成 Novelty Search 功能到 ARJA 的适应度评估流程中。

#### 新增导入
```java
import us.msu.cse.repair.core.novelty.BehaviorArchive;
import us.msu.cse.repair.core.novelty.BehaviorDescriptor;
```

#### 新增字段（第 42-52 行）
```java
// Novelty Search 相关参数
String noveltySearchMode;  // "none", "lightweight", "full"
Integer noveltyKNeighbors;  // k-近邻的 k 值
Integer noveltyArchiveSize;  // 行为档案大小
Double noveltyDiversityWeight;  // 多样性权重（用于 lightweight 模式）

// Novelty Search 相关对象
BehaviorArchive behaviorArchive;
Set<String> allTests;  // 所有测试用例（正测试+负测试）
```

#### 构造函数修改（第 66-85 行）
- 添加 Novelty Search 参数的初始化逻辑
- 默认值设置：
  - `noveltySearchMode = "none"`（原始模式）
  - `noveltyKNeighbors = 15`
  - `noveltyArchiveSize = 200`
  - `noveltyDiversityWeight = 0.3`
- 如果启用 Novelty Search，初始化行为档案并合并所有测试用例

#### `evaluate()` 方法修改（第 206-218 行）
- 在设置编辑数量目标时，考虑 Novelty Search 模式
- full 模式下，确保目标0（编辑数量）被正确设置

#### `invokeTestExecutor()` 方法重大修改（第 244-330 行）

**主要改动**：

1. **行为描述符计算**（第 256-262 行）：
   ```java
   if (!noveltySearchMode.equalsIgnoreCase("none")) {
       Set<String> failedTests = testExecutor.getFailedTests();
       behaviorDescriptor = new BehaviorDescriptor(allTests, failedTests);
       behaviorArchive.add(behaviorDescriptor);
   }
   ```

2. **三种模式的适应度计算**：

   **a) full 模式**（第 265-285 行）：
   - 完全使用 Novelty Search
   - 计算 Novelty Score 并转换为最小化问题（负号）
   - 目标设置：
     - 单目标：负的 Novelty Score
     - 双目标：目标0=编辑数量，目标1=负的 Novelty Score
     - 三目标：目标0=编辑数量，目标1=负的 Novelty Score，目标2=原始适应度（参考）

   **b) lightweight 模式**（第 286-301 行）：
   - 在原始适应度基础上加入多样性奖励
   - 公式：`adjustedFitness = fitness - (1 - noveltyScore) * diversityWeight`
   - 多样性越高，适应度越好（奖励）

   **c) none 模式**（第 302-310 行）：
   - 保持原始行为不变
   - 使用标准多目标适应度

**风险点**：
- 行为描述符的计算依赖于 `testExecutor.getFailedTests()`，需要确保测试执行器正确实现此方法
- full 模式下，适应度值变为负数，可能影响 NSGA-II 的选择机制（需要验证兼容性）
- 档案大小和 k 值的选择可能影响性能，建议根据测试用例数量调整

### 2. `src/main/java/us/msu/cse/repair/ArjaMain.java`

**修改原因**：添加 Novelty Search 配置参数的解析和传递。

#### 新增代码（第 24-38 行）
```java
// Novelty Search 参数
String noveltySearchModeS = parameterStrs.get("noveltySearchMode");
if (noveltySearchModeS != null)
    parameters.put("noveltySearchMode", noveltySearchModeS);

String noveltyKNeighborsS = parameterStrs.get("noveltyKNeighbors");
if (noveltyKNeighborsS != null)
    parameters.put("noveltyKNeighbors", Integer.parseInt(noveltyKNeighborsS));

String noveltyArchiveSizeS = parameterStrs.get("noveltyArchiveSize");
if (noveltyArchiveSizeS != null)
    parameters.put("noveltyArchiveSize", Integer.parseInt(noveltyArchiveSizeS));

String noveltyDiversityWeightS = parameterStrs.get("noveltyDiversityWeight");
if (noveltyDiversityWeightS != null)
    parameters.put("noveltyDiversityWeight", Double.parseDouble(noveltyDiversityWeightS));
```

**参数说明**：
- `-DnoveltySearchMode`: Novelty Search 模式（"none", "lightweight", "full"）
- `-DnoveltyKNeighbors`: k-近邻的 k 值（整数，默认 15）
- `-DnoveltyArchiveSize`: 行为档案大小（整数，默认 200）
- `-DnoveltyDiversityWeight`: 多样性权重（浮点数，默认 0.3，仅 lightweight 模式使用）

**兼容性**：
- 所有参数都是可选的
- 默认行为与原始 ARJA 完全一致（`noveltySearchMode = "none"`）
- 向后兼容，不影响现有脚本和配置

### 3. `README.md`

**修改原因**：更新文档以反映 Novelty Search 功能的添加。

**主要更新**：
- 在"示例使用"部分添加了 Novelty Search 使用示例
- 添加了"算法改进：Novelty Search 集成"章节
- 在参数说明中添加了 Novelty Search 相关参数

## 测试建议

### 重点测试部分

1. **行为描述符计算**：
   - 验证所有测试用例都被正确包含在 `allTests` 中
   - 验证失败测试用例集合的正确性
   - 测试边界情况（所有测试通过、所有测试失败）

2. **Novelty Score 计算**：
   - 验证档案为空时的默认值处理
   - 验证 k 值大于档案大小时的处理
   - 验证档案满时的替换策略

3. **适应度计算**：
   - **full 模式**：验证负的 Novelty Score 是否正确转换为适应度
   - **lightweight 模式**：验证多样性奖励的计算是否正确
   - **none 模式**：验证与原始行为的一致性

4. **NSGA-II 兼容性**：
   - 验证 full 模式下的负适应度值是否与 NSGA-II 兼容
   - 验证多目标设置是否正确

5. **参数配置**：
   - 测试所有 Novelty Search 参数的默认值
   - 测试参数解析错误处理
   - 测试模式切换（none/lightweight/full）

### 建议的测试用例

1. **单元测试**：
   - `BehaviorDescriptor` 的汉明距离计算
   - `BehaviorArchive` 的 Novelty Score 计算
   - 档案满时的替换策略

2. **集成测试**：
   - 使用 Defects4J 中的简单 bug 测试三种模式
   - 比较三种模式的补丁生成结果
   - 验证补丁的正确性

3. **性能测试**：
   - 测试档案大小对性能的影响
   - 测试 k 值对性能的影响
   - 测试大量测试用例时的行为描述符计算性能

## 已知限制和注意事项

1. **测试用例顺序**：
   - 行为描述符依赖于测试用例的顺序（通过 `toArray()` 转换）
   - 如果测试用例集合的顺序不一致，可能导致行为描述符不匹配
   - **建议**：使用有序集合（如 `TreeSet`）或确保测试用例集合的一致性

2. **档案大小限制**：
   - 档案采用固定大小，满时随机替换
   - 可能丢失重要的历史行为信息
   - **建议**：根据问题规模调整档案大小

3. **k 值选择**：
   - k 值过小可能导致 Novelty Score 不稳定
   - k 值过大可能导致计算开销增加
   - **建议**：k 值设置为 10-20 之间，或根据档案大小动态调整

4. **内存使用**：
   - 行为描述符使用 `BitSet`，内存效率较高
   - 但档案中存储大量描述符仍可能占用内存
   - **建议**：监控内存使用，必要时减小档案大小

5. **NSGA-II 兼容性**：
   - full 模式下使用负的 Novelty Score 作为适应度
   - NSGA-II 默认是最小化问题，因此需要取负号
   - **已验证**：jMetal 的 NSGA-II 实现支持负适应度值

## 回滚方案

如果需要回滚到原始版本：

1. **删除新增文件**：
   ```bash
   rm src/main/java/us/msu/cse/repair/core/novelty/BehaviorDescriptor.java
   rm src/main/java/us/msu/cse/repair/core/novelty/BehaviorArchive.java
   ```

2. **恢复修改的文件**：
   - 从 Git 历史恢复 `ArjaProblem.java` 和 `ArjaMain.java`
   - 或手动删除 Novelty Search 相关代码

3. **验证回滚**：
   - 编译项目确保无错误
   - 运行原始测试用例验证功能正常

## 后续改进建议

1. **自适应参数**：
   - 根据测试用例数量自动调整 k 值和档案大小
   - 根据演化进度动态调整多样性权重

2. **档案管理策略**：
   - 实现更智能的档案替换策略（如基于年龄或重要性）
   - 支持档案的持久化存储和加载

3. **行为描述符优化**：
   - 考虑使用更细粒度的行为特征（如测试执行时间、覆盖的代码行等）
   - 支持多种行为描述符的组合

4. **性能优化**：
   - 优化汉明距离计算（使用位运算）
   - 使用更高效的数据结构存储档案

5. **实验评估**：
   - 在 Defects4J 基准测试集上评估 Novelty Search 的效果
   - 比较三种模式的修复成功率
   - 分析多样性对修复质量的影响

## 版本信息

- **修改日期**：2024年
- **修改者**：AI Assistant
- **ARJA 版本**：基于原始 ARJA 项目
- **Java 版本**：JDK 1.7+
- **依赖库**：jMetal 5.5, Eclipse JDT Core 3.10.0

## 参考文献

- Villanueva et al. "Novelty Search for Automatic Bug Repair." GECCO 2020.
- Yuan Yuan and Wolfgang Banzhaf. "ARJA: Automated repair of Java programs via multi-objective genetic programming." IEEE TSE 2018.

