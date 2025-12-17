# ARJA (Java 11 升级版)

## 项目简介

ARJA（Adaptive Randomized Joint Exploration）是一个基于遗传编程（Genetic Programming, GP）的 Java 程序自动修复工具。该项目通过多目标搜索、测试过滤、类型匹配以及多种搜索空间缩减策略，实现了高效的缺陷定位与补丁生成。

**本版本已升级到支持 Java 11 和 Defects4J v3.0.1。**

### 核心思想

ARJA 采用多目标遗传算法（NSGA-II）来演化补丁，主要特点包括：

- **多目标优化**：同时优化补丁的简洁性（编辑数量）和测试通过率
- **自适应搜索**：基于缺陷定位结果（suspiciousness values）指导搜索方向
- **类型匹配**：确保生成的补丁在语法和类型上正确
- **测试过滤**：通过覆盖率分析减少测试用例数量，提高评估效率

### 解决的问题

ARJA 旨在自动修复 Java 程序中的 bug，给定：
- 有缺陷的源代码
- 测试套件（包含通过和失败的测试用例）

输出：
- 能够通过所有测试用例的补丁（test-suite adequate patches）

## 系统要求

- **Java**: JDK 11 或更高版本（**重要**：本版本已从 Java 7 升级到 Java 11）
- **操作系统**: Linux, macOS, Windows
- **依赖**: Defects4J v3.0.1（推荐）
- **构建工具**: Maven 3.x（可选，也可使用 javac 直接编译）

## 与原始版本的区别

本版本（Java 11 升级版）相比原始版本的主要改进：

1. **兼容 Java 11 环境**：完全支持 Java 11 及更高版本
2. **支持 Defects4J v3.0.1**：适配最新的 Defects4J 版本的所有特性
3. **解决了模块系统访问限制**：添加了必要的 JVM 参数和代码修改
4. **更新了过时的 API 调用**：替换了已弃用的 `Thread.stop()` 等方法
5. **时区处理**：自动设置时区以匹配 Defects4J v3.0.1 要求
6. **路径适配**：支持 Defects4J v3.0.1 的新目录结构

**详细修改记录请参考 [UPGRADE_NOTES.md](UPGRADE_NOTES.md)**

## 快速开始

### 1. 安装 Defects4J v3.0.1

确保已安装 Defects4J v3.0.1 并配置好环境变量：

```bash
export DEFECTS4J_HOME=/path/to/defects4j
export PATH=$DEFECTS4J_HOME/framework/bin:$PATH
```

### 2. 编译 ARJA

#### 方式一：使用 Maven 编译（推荐）

```bash
# 进入项目根目录
cd arja

# 编译项目（需要 Java 11）
mvn clean compile

# 打包（包含依赖）
mvn package
```

编译后的类文件位于 `target/classes/`，打包后的 jar 文件位于 `target/`。

#### 方式二：使用 javac 直接编译

```bash
# 进入项目根目录
cd arja

# 清理旧的编译文件
rm -rf bin
mkdir bin

# 编译源代码（需要 Java 11）
javac --release 11 -cp "lib/*:" -d bin $(find src/main/java -name '*.java')

# 编译外部项目（如果存在）
cd external
rm -rf bin
mkdir bin
javac --release 11 -cp "../lib/*:../bin" -d bin $(find src -name '*.java')
cd ..
```

### 3. 运行修复示例

#### 基本用法

```bash
# 使用编译后的类文件运行
# 注意：需要添加 Java 11 模块系统访问参数
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -cp "lib/*:bin" us.msu.cse.repair.Main Arja \
  -DsrcJavaDir /path/to/source \
  -DbinJavaDir /path/to/compiled/classes \
  -DbinTestDir /path/to/compiled/test/classes \
  -Ddependences /path/to/dependency1.jar:/path/to/dependency2.jar
```

#### 修复 Defects4J 中的 bug

假设要修复 Defects4J 中 Chart 项目的 bug #1：

```bash
# 1. 准备 Defects4J 项目（假设已 checkout）
export DEFECTS4J_HOME=/path/to/defects4j
cd /tmp
$DEFECTS4J_HOME/framework/bin/defects4j checkout -p Chart -v 1b -w /tmp/Chart_1_buggy

# 2. 编译项目
cd /tmp/Chart_1_buggy
$DEFECTS4J_HOME/framework/bin/defects4j compile

# 3. 运行 ARJA（使用 Java 11）
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -cp "/path/to/arja/lib/*:/path/to/arja/bin" us.msu.cse.repair.Main Arja \
  -DsrcJavaDir $(pwd)/source \
  -DbinJavaDir $(pwd)/build \
  -DbinTestDir $(pwd)/build/tests \
  -Ddependences $(defects4j export -p cp.test | tr ',' ':') \
  -DpopulationSize 40 \
  -DmaxGenerations 50 \
  -DpatchOutputRoot /tmp/arja_patches_chart_1
```

**注意**：Defects4J v3.0.1 使用 `build/` 目录而不是 `target/` 目录。

## 参数说明

### 必需参数

- `-DsrcJavaDir`: 源代码根目录（绝对路径）
- `-DbinJavaDir`: 编译后的类文件根目录（绝对路径）
- `-DbinTestDir`: 编译后的测试类文件根目录（绝对路径）
- `-Ddependences`: 依赖的 jar 文件路径，多个路径用 `:` 分隔

### 可选参数

- `-DpopulationSize`: 种群大小（默认：40）
- `-DmaxGenerations`: 最大代数（默认：50）
- `-DmaxEvaluations`: 最大评估次数（默认：populationSize * maxGenerations）
- `-DgzoltarDataDir`: GZoltar 输出目录（如果已预先运行 GZoltar）
- `-Dthr`: 缺陷定位阈值（默认：0.1）
- `-Dweight`: 正测试权重（默认：0.5）
- `-DnumberOfObjectives`: 目标数量（1、2 或 3，默认：2）
- `-DmaxNumberOfEdits`: 最大编辑数量
- `-DpatchOutputRoot`: 补丁输出目录（默认：patches_$id$）
- `-DtestExecutorName`: 测试执行器名称（"ExternalTestExecutor" 或 "InternalTestExecutor"）
- `-DingredientScreenerName`: 种子语句筛选器名称
- `-DnoveltySearchMode`: Novelty Search 模式（"none", "lightweight", "full"，默认："none"）

### 查看所有参数

```bash
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -cp "lib/*:bin" us.msu.cse.repair.Main -listParameters
```

## 使用脚本运行

项目提供了一个便捷的运行脚本 `run_arja_novelty_fixed.sh`：

```bash
# 编辑脚本配置变量
vim run_arja_novelty_fixed.sh

# 运行脚本
bash run_arja_novelty_fixed.sh
```

脚本会自动：
1. 检查 Java 11 环境
2. 编译 ARJA 项目
3. 准备 Defects4J 项目
4. 运行 ARJA 修复
5. 检查结果

## 注意事项

### Java 11 相关

1. **运行时需添加 JVM 参数**：
   ```bash
   --add-opens java.base/java.lang=ALL-UNNAMED
   --add-opens java.base/java.util=ALL-UNNAMED
   ```
   这些参数用于解决 Java 11 模块系统的访问限制。

2. **时区设置**：
   - 程序会自动设置时区为 `America/Los_Angeles` 以匹配 Defects4J v3.0.1 要求
   - 无需手动设置环境变量

### Defects4J v3.0.1 相关

1. **路径结构变化**：
   - Defects4J v3.0.1 使用 `build/classes` 和 `build/tests` 目录
   - 旧版本使用 `target/classes` 和 `target/test-classes`
   - ARJA 已自动适配两种路径结构

2. **类路径获取**：
   ```bash
   # 获取测试类路径
   defects4j export -p cp.test
   
   # 获取编译类路径
   defects4j export -p cp.compile
   ```

3. **弃用的 bug**：
   - Defects4J v3.0.1 已从 `active-bugs.csv` 移除某些 bug
   - 确保使用有效的 bug ID

### 其他注意事项

1. **确保使用正确的类路径配置**
2. **详细修改记录见 [UPGRADE_NOTES.md](UPGRADE_NOTES.md)**
3. **如遇问题，请检查 Java 版本是否为 11 或更高**

## 输出说明

运行成功后，补丁将保存在 `patches_$id$/` 目录下，每个补丁包含：
- 修改的操作列表（操作类型、位置、种子语句）
- 评估次数和时间信息

## 算法改进：Novelty Search 集成

ARJA 现已支持 Novelty Search（NS）模式，以缓解中性适应度景观和种群多样性丧失问题。

### 支持的 NS 模式

1. **none**（默认）：使用原始多目标适应度
2. **lightweight**：在适应度基础上加入多样性惩罚项
3. **full**：完全采用 Novelty Search，使用测试用例行为描述符

### 使用示例

```bash
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -cp "lib/*:bin" us.msu.cse.repair.Main Arja \
  -DsrcJavaDir /path/to/source \
  -DbinJavaDir /path/to/compiled/classes \
  -DbinTestDir /path/to/compiled/test/classes \
  -Ddependences /path/to/dependencies.jar \
  -DnoveltySearchMode lightweight \
  -DnoveltyKNeighbors 15 \
  -DnoveltyArchiveSize 200
```

## 故障排除

### 问题 1：编译错误

**错误信息**：`javac: invalid target release: 11`

**解决方案**：
- 确保使用 Java 11 或更高版本
- 检查 `JAVA_HOME` 环境变量

### 问题 2：运行时模块访问错误

**错误信息**：`java.lang.IllegalAccessException`

**解决方案**：
- 确保运行时添加了 `--add-opens` JVM 参数
- 参考运行示例中的参数设置

### 问题 3：Defects4J 路径错误

**错误信息**：找不到类文件或测试文件

**解决方案**：
- 检查 Defects4J 项目是否正确编译
- 使用 `defects4j export` 命令获取正确的路径
- 确保使用 Defects4J v3.0.1 的路径结构（`build/` 目录）

### 问题 4：时区相关问题

**错误信息**：测试结果不一致

**解决方案**：
- 程序已自动设置时区，通常无需手动配置
- 如果仍有问题，检查系统时区设置

## 评估与引用

ARJA 已在 Defects4J 基准测试集的 224 个 bug 上进行评估。生成的补丁可在以下地址获取：
- https://github.com/yyxhdy/defects4j-patches

### 学术引用

如果使用 ARJA 进行学术研究，请引用：

```
Yuan Yuan and Wolfgang Banzhaf. 2018. ARJA: Automated repair of Java programs 
via multi-objective genetic programming. IEEE Transactions on Software Engineering (2018). 
https://doi.org/10.1109/TSE.2018.2874648
```

## 许可证

请查看 [LICENSE](LICENSE) 文件。

## 联系方式

如有问题或反馈，请联系：yyxhdy@gmail.com

## 更新日志

### 2024-12-19：Java 11 升级版

- ✅ 升级到 Java 11 兼容
- ✅ 支持 Defects4J v3.0.1
- ✅ 修复已弃用的 API
- ✅ 添加时区处理
- ✅ 更新文档

详细修改记录请参考 [UPGRADE_NOTES.md](UPGRADE_NOTES.md)

