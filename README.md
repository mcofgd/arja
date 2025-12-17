# ARJA: Adaptive Randomized Joint Exploration for Automated Program Repair

## 项目简介

ARJA（Adaptive Randomized Joint Exploration）是一个基于遗传编程（Genetic Programming, GP）的 Java 程序自动修复工具。该项目通过多目标搜索、测试过滤、类型匹配以及多种搜索空间缩减策略，实现了高效的缺陷定位与补丁生成。

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

## 整体架构与流程

### 修复流程概览

```
输入: Buggy Program + Test Suite
  ↓
[1] 缺陷定位 (Fault Localization)
  ↓ 使用 GZoltar 进行频谱缺陷定位
[2] AST 解析与修改点识别 (Modification Points)
  ↓ 解析源代码，识别可疑语句位置
[3] 种子语句提取 (Seed Statement Generation)
  ↓ 从代码库中提取可用的修复语句
[4] 类型匹配与过滤 (Type Matching & Filtering)
  ↓ 确保类型兼容性和语法正确性
[5] 遗传算法演化 (Genetic Algorithm Evolution)
  ↓ NSGA-II 多目标优化
  ├─ 初始化种群
  ├─ 评估适应度（测试执行）
  ├─ 选择、交叉、变异
  └─ 迭代演化
[6] 补丁验证与保存 (Patch Validation & Saving)
  ↓
输出: Valid Patches
```

### 详细流程说明

#### 阶段 1: 缺陷定位（Fault Localization）

- **输入**：源代码、测试套件
- **工具**：GZoltar 1.6.2
- **输出**：可疑代码行及其可疑度分数（suspiciousness values）
- **关键类**：`GZoltarFaultLocalizer`, `GZoltarFaultLocalizer2`

#### 阶段 2: AST 解析与修改点识别

- **输入**：源代码目录、可疑代码行
- **处理**：
  - 使用 Eclipse JDT 解析 Java 源代码为 AST
  - 识别每个可疑行对应的语句节点
  - 构建修改点（ModificationPoint）列表
- **关键类**：`FileASTRequestorImpl`, `ModificationPoint`

#### 阶段 3: 种子语句提取

- **目的**：从代码库中提取可用作修复材料的语句
- **方法**：基于代码覆盖率分析，识别与缺陷相关的可执行语句
- **关键类**：`SeedLineGeneratorProcess`, `SeedStatement`

#### 阶段 4: 类型匹配与过滤

- **变量检测**：识别局部变量、字段变量
- **方法检测**：识别可调用的方法
- **类型匹配**：确保种子语句的类型与修改点兼容
- **过滤规则**：
  - `ManipulationFilterRule`: 过滤不合适的操作类型
  - `IngredientFilterRule`: 过滤不合适的种子语句
  - `MIFilterRule`: 操作-种子组合过滤
- **关键类**：`LocalVarDetector`, `FieldVarDetector`, `MethodDetector`, `IngredientScreenerFactory`

#### 阶段 5: 遗传算法演化

**编码方式**：
- `ArrayInt`: 存储每个修改点的操作类型索引和种子语句索引
- `Binary`: 存储每个修改点是否被激活（二进制位）

**演化过程**：
1. **初始化**：基于可疑度分数概率性初始化种群
2. **评估**：
   - 将编码转换为补丁
   - 编译修改后的代码
   - 执行测试套件
   - 计算适应度（测试失败率、编辑数量）
3. **选择**：NSGA-II 的二元锦标赛选择
4. **交叉**：HUX 单点交叉
5. **变异**：位翻转均匀变异
6. **环境选择**：NSGA-II 的非支配排序与拥挤距离

**关键类**：
- `ArjaProblem`: 问题定义与适应度评估
- `Arja`: ARJA 算法封装
- `NSGAII`: 多目标优化算法（来自 jMetal 库）

#### 阶段 6: 补丁验证与保存

- **验证**：确保补丁通过所有测试用例
- **去重**：避免保存重复的补丁
- **保存**：将补丁保存到指定目录
- **关键类**：`Patch`, `IO`

## 目录结构与关键文件

### 主要源码目录

```
arja/
├── src/main/java/us/msu/cse/repair/
│   ├── algorithms/              # 修复算法实现
│   │   ├── arja/                # ARJA 算法
│   │   ├── genprog/             # GenProg 算法
│   │   ├── kali/                # Kali 算法
│   │   └── rsrepair/            # RSRepair 算法
│   ├── core/                    # 核心功能模块
│   │   ├── compiler/            # Java 编译器封装
│   │   ├── coverage/            # 代码覆盖率分析
│   │   ├── faultlocalizer/      # 缺陷定位
│   │   ├── filterrules/         # 过滤规则
│   │   ├── manipulation/        # 代码操作（删除、替换、插入等）
│   │   ├── parser/              # AST 解析与信息提取
│   │   ├── testexecutors/       # 测试执行器
│   │   └── util/                # 工具类
│   ├── ec/                      # 演化计算相关
│   │   ├── algorithms/          # 遗传算法实现
│   │   ├── operators/           # 遗传算子（交叉、变异、选择）
│   │   ├── problems/            # 问题定义
│   │   ├── representation/      # 编码表示
│   │   └── variable/            # 变量类型
│   └── [Main classes]           # 主入口类
├── external/                    # 外部依赖项目
├── lib/                         # 第三方库（.jar 文件）
└── pom.xml                      # Maven 构建配置
```

### 核心 Java 文件功能说明

#### 主入口类

- **`Main.java`**: 程序主入口，根据参数选择不同的修复算法
- **`ArjaMain.java`**: ARJA 算法的启动类，配置参数并执行修复

#### 算法核心类

- **`Arja.java`** (`algorithms/arja/Arja.java`)
  - **作用**：ARJA 算法的封装类
  - **关键方法**：
    - `Arja(ArjaProblem problem)`: 构造函数，初始化 NSGA-II 算法

- **`ArjaProblem.java`** (`ec/problems/ArjaProblem.java`)
  - **作用**：定义 ARJA 的优化问题，实现适应度评估
  - **关键方法**：
    - `evaluate(Solution solution)`: 评估个体的适应度
      - 解码补丁（从编码转换为代码修改）
      - 编译修改后的代码
      - 执行测试套件
      - 计算多目标适应度值
    - `invokeTestExecutor()`: 调用测试执行器并计算适应度
    - `manipulateOneModificationPoint()`: 对单个修改点执行操作

#### 核心功能类

- **`AbstractRepairProblem.java`** (`core/AbstractRepairProblem.java`)
  - **作用**：修复问题的抽象基类，包含通用功能
  - **关键方法**：
    - `invokeFaultLocalizer()`: 调用缺陷定位器
    - `invokeASTRequestor()`: 解析 AST 并识别修改点
    - `invokeIngredientScreener()`: 筛选种子语句
    - `getTestExecutor()`: 获取测试执行器

- **`Patch.java`** (`core/util/Patch.java`)
  - **作用**：表示一个补丁，包含多个修改项
  - **关键方法**：
    - `Patch(List<Integer> opList, ...)`: 从操作列表构建补丁
    - `equals()`, `hashCode()`: 补丁去重

- **`ModificationPoint.java`** (`core/parser/ModificationPoint.java`)
  - **作用**：表示一个可修改的代码位置
  - **关键属性**：
    - `suspValue`: 可疑度分数
    - `statement`: 待修改的语句
    - `ingredients`: 可用的种子语句列表

#### 操作类

- **`AbstractManipulation.java`** (`core/manipulation/AbstractManipulation.java`)
  - **作用**：代码操作的抽象基类
  - **子类**：
    - `DeleteManipulation`: 删除语句
    - `ReplaceManipulation`: 替换语句
    - `InsertBeforeManipulation`: 在语句前插入
    - `InsertAfterManipulation`: 在语句后插入
    - `InsertReturnManipulation`: 插入返回语句
    - `RedirectBranchManipulation`: 重定向分支

#### 测试执行类

- **`ITestExecutor.java`** (`core/testexecutors/ITestExecutor.java`)
  - **作用**：测试执行器接口
  - **关键方法**：
    - `runTests()`: 执行测试套件
    - `getFailureCountInPositive()`: 获取正测试失败数
    - `getFailureCountInNegative()`: 获取负测试失败数
    - `getFailedTests()`: 获取失败的测试用例集合

- **`ExternalTestExecutor.java`**: 外部进程方式执行测试
- **`InternalTestExecutor.java`**: 内部类加载器方式执行测试

#### 演化计算类

- **`GA.java`** (`ec/algorithms/GA.java`)
  - **作用**：标准遗传算法实现
  - **关键方法**：
    - `execute()`: 执行遗传算法主循环

- **`ArrayIntAndBinarySolutionType.java`** (`ec/representation/ArrayIntAndBinarySolutionType.java`)
  - **作用**：定义 ARJA 的编码方式（ArrayInt + Binary）

#### 遗传算子

- **`HUXSinglePointCrossover.java`**: HUX 单点交叉算子
- **`BitFilpUniformMutation.java`**: 位翻转均匀变异算子
- **`ExtendedCrossoverFactory.java`**: 交叉算子工厂
- **`ExtendedMutationFactory.java`**: 变异算子工厂

## 依赖与运行环境

### 系统要求

- **操作系统**：Linux 或 macOS（Windows 需要额外配置）
- **JDK 版本**：JDK 1.7 或更高版本
- **构建工具**：Maven 3.x（可选，也可使用 javac 直接编译）

### 主要依赖

项目依赖的第三方库（位于 `lib/` 目录）：

- **jMetal 5.5**: 多目标优化算法框架（NSGA-II 实现）
- **Eclipse JDT Core 3.10.0**: Java 代码解析与操作
- **GZoltar 0.1.1**: 缺陷定位工具
- **JUnit 4.11**: 测试框架
- **ASM 5.2**: 字节码操作
- **JaCoCo 0.7.9**: 代码覆盖率分析
- **Commons IO 2.5**: 文件操作工具

### 编译与运行

#### 方式一：使用 Maven 编译

```bash
# 进入项目根目录
cd arja

# 编译项目
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
rm -r bin

# 创建输出目录
mkdir bin

# 编译源代码（需要指定所有依赖的 jar 文件）
javac -cp "lib/*:" -d bin $(find src/main/java -name '*.java')

# 编译外部项目
cd external
rm -r bin
mkdir bin
javac -cp "../lib/*:" -d bin $(find src -name '*.java')
cd ..
```

### 运行 ARJA

#### 基本用法

```bash
# 使用编译后的类文件运行
java -cp "lib/*:bin" us.msu.cse.repair.Main Arja \
  -DsrcJavaDir /path/to/source \
  -DbinJavaDir /path/to/compiled/classes \
  -DbinTestDir /path/to/compiled/test/classes \
  -Ddependences /path/to/dependency1.jar:/path/to/dependency2.jar
```

#### 参数说明

**必需参数**：
- `-DsrcJavaDir`: 源代码根目录（绝对路径）
- `-DbinJavaDir`: 编译后的类文件根目录（绝对路径）
- `-DbinTestDir`: 编译后的测试类文件根目录（绝对路径）
- `-Ddependences`: 依赖的 jar 文件路径，多个路径用 `:` 分隔

**可选参数**：
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

#### 查看所有参数

```bash
java -cp "lib/*:bin" us.msu.cse.repair.Main -listParameters
```

## 示例使用

### 示例 1: 修复 Defects4J 中的 bug

假设要修复 Defects4J 中 Chart 项目的 bug #1：

```bash
# 1. 准备 Defects4J 项目（假设已 checkout）
export DEFECTS4J_HOME=/path/to/defects4j
cd /tmp
$DEFECTS4J_HOME/framework/bin/defects4j checkout -p Chart -v 1b -w /tmp/Chart_1_buggy

# 2. 编译项目
cd /tmp/Chart_1_buggy
$DEFECTS4J_HOME/framework/bin/defects4j compile

# 3. 运行 ARJA
java -cp "/path/to/arja/lib/*:/path/to/arja/bin" us.msu.cse.repair.Main Arja \
  -DsrcJavaDir $(pwd)/source \
  -DbinJavaDir $(pwd)/build \
  -DbinTestDir $(pwd)/build-tests \
  -Ddependences $(defects4j export -p dir.classpath.bin | tr ',' ':') \
  -DpopulationSize 40 \
  -DmaxGenerations 50 \
  -DpatchOutputRoot /tmp/arja_patches_chart_1
```

### 示例 2: 使用 Novelty Search 模式

```bash
java -cp "lib/*:bin" us.msu.cse.repair.Main Arja \
  -DsrcJavaDir /path/to/source \
  -DbinJavaDir /path/to/compiled/classes \
  -DbinTestDir /path/to/compiled/test/classes \
  -Ddependences /path/to/dependencies.jar \
  -DnoveltySearchMode lightweight \
  -DnoveltyKNeighbors 15 \
  -DnoveltyArchiveSize 200
```

### 输出说明

运行成功后，补丁将保存在 `patches_$id$/` 目录下，每个补丁包含：
- 修改的操作列表（操作类型、位置、种子语句）
- 评估次数和时间信息

## 算法改进：Novelty Search 集成

ARJA 现已支持 Novelty Search（NS）模式，以缓解中性适应度景观和种群多样性丧失问题。详见 [MODIFICATION_LOG.md](MODIFICATION_LOG.md)。

### 支持的 NS 模式

1. **none**（默认）：使用原始多目标适应度
2. **lightweight**：在适应度基础上加入多样性惩罚项
3. **full**：完全采用 Novelty Search，使用测试用例行为描述符

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
