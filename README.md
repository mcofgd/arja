# ARJA (Java 11 升级版) - 自动化程序修复系统

## 📖 项目概述

ARJA (Adaptive Randomized Joint Exploration) 是一个基于多目标遗传编程（Genetic Programming, GP）的 Java 自动化程序修复（APR）工具。

本项目是原始 ARJA 的**现代化升级版本**。原始版本基于 Java 7 开发，已无法在现代开发环境（Java 11+）和新版数据集（Defects4J v2/v3）上运行。本项目通过一系列深度重构，使其完全兼容 **Java 11** 和 **Defects4J v3.0.1**，并修复了原版中的多个逻辑缺陷。

### 核心设计理念

ARJA 的设计基于以下核心思想：

1.  **多目标优化 (Multi-Objective Optimization)**：
    *   传统的 APR 工具通常只关注修复率。ARJA 使用 **NSGA-II** 算法同时优化两个目标：
        1.  **测试通过率**（最大化）：让程序通过尽可能多的测试。
        2.  **补丁简洁度**（最小化）：减少代码变更量（编辑距离），以降低破坏原有功能的风险。
2.  **冗余假设 (Plastic Surgery Hypothesis)**：
    *   ARJA 假设修复 Bug 所需的代码片段已经存在于当前项目的其他位置。它通过从源码中提取“种子语句”（Ingredient），并将其移植到缺陷位置来生成补丁。
3.  **搜索空间缩减**：
    *   利用 GZoltar 进行缺陷定位，计算代码的可疑度（Suspiciousness），只在可疑位置进行变异操作。
    *   通过类型匹配和变量作用域检查，过滤掉语法无效的补丁。

---

## ⚙️ 工作流程

ARJA 的修复过程是一个自动化的演化循环：

1.  **初始化 (Initialization)**：
    *   利用 GZoltar 运行测试，计算每行代码的可疑度。
    *   识别可能的修改点（Modification Points）和种子语句（Ingredients）。
2.  **种群生成 (Population Generation)**：
    *   生成初始的一组补丁（个体）。每个补丁由一系列编辑操作（Delete, Insert, Replace）组成。
3.  **演化循环 (Evolution Loop)**：
    *   **变异 (Mutation)**：随机修改补丁中的操作。
    *   **交叉 (Crossover)**：交换两个补丁的部分操作。
    *   **评估 (Evaluation)**：编译并运行测试用例。
        *   *优化策略*：使用测试过滤（Test Filtering），只运行相关的测试用例以加速评估。
    *   **选择 (Selection)**：基于 NSGA-II 算法，保留表现最好的补丁进入下一代。
4.  **输出 (Output)**：
    *   当找到能通过所有测试的补丁，或达到最大代数时，输出结果。

---

## 🚀 升级与修复日志 (Upgrade & Fix History)

本项目经历了一次完整的现代化改造。以下是详细的修改记录、原因及结果：

### 1. 基础设施升级 (Java 11 & Defects4J v3)
*   **原因**：原版依赖 `Thread.stop()`（Java 11 中已移除）和旧版 `tools.jar`，且无法识别 Defects4J v3 的 `build/` 目录结构。
*   **修改内容**：
    *   **移除 `Thread.stop()`**：重构为使用 `ExecutorService` 和 `Future` 进行测试执行的超时控制。
    *   **模块化适配**：添加 JVM 参数 `--add-opens` 以解决 Java 9+ 模块系统的反射限制。
    *   **路径适配**：自动识别 Defects4J v3 的 `build/classes` 和 `build/tests` 目录结构。
    *   **GZoltar 升级**：升级依赖库以支持 Java 11 字节码插桩。
*   **结果**：工具现在可以在 Ubuntu 20.04+/Java 11 环境下流畅运行。
*   *参考文档*：`detail/JAVA11_ARJA_FIX_SUMMARY.md`, `detail/GZOLTAR_UPGRADE_STATUS.md`

### 2. 类加载器隔离修复 (ClassLoader Fix)
*   **原因**：在同一个 JVM 中反复运行测试时，静态变量和单例模式的状态会污染后续测试，导致“假阴性”或“假阳性”结果。
*   **修改内容**：实现了自定义的 `SeparatedClassLoader`，确保每次测试执行都在干净的类加载环境中进行。
*   **结果**：测试结果的确定性大幅提高，消除了因状态污染导致的随机失败。
*   *参考文档*：`detail/CLASSLOADER_FIX_SUMMARY.md`

### 3. 测试采样与过滤逻辑修复 (Test Sampling Fix)
*   **原因**：原版的测试过滤逻辑过于激进，有时会错误地过滤掉关键的触发测试（Triggering Tests），导致生成的补丁虽然通过了筛选后的测试集，但在全量测试中失败。
*   **修改内容**：
    *   修正了 `TestFilter` 类中的依赖分析逻辑。
    *   强制包含所有先前失败的测试用例（Failing Tests）。
*   **结果**：生成的补丁质量更高，通过全量回归测试的概率显著增加。
*   *参考文档*：`detail/测试采样问题修复.md`

### 4. 参数解析修复
*   **原因**：部分命令行参数（如 `miFilterRule`, `percentage`）在解析时存在逻辑错误，导致用户配置不生效。
*   **修改内容**：修复了 `ArjaExternal` 和 `Main` 类中的参数映射代码。
*   **结果**：所有配置参数现在均能正确控制算法行为。
*   *参考文档*：`detail/miFilterRule参数缺失问题修复.md`

---

## 🛠️ 快速开始 (Quick Start)

### 环境要求
*   **OS**: Linux / macOS
*   **Java**: JDK 11 (必须)
*   **Defects4J**: v3.0.1+

### 1. 编译项目
```bash
mvn clean package -DskipTests
```

### 2. 运行修复 (使用脚本)
我们提供了封装好的脚本，自动处理环境变量和类路径：

```bash
# 编辑脚本配置你的路径
vim test_arja_ns.sh

# 运行
./test_arja_ns.sh
```

### 3. 验证补丁
ARJA 生成补丁后，可以使用批量验证脚本进行正确性评估：

```bash
# 自动验证生成的补丁是否真的修复了 Bug
python3 batch_verify_patches.py
```

---

## ⚠️ 注意事项

1.  **JVM 参数**：直接使用 `java` 命令运行 jar 包时，**必须**添加以下参数，否则会报错：
    ```bash
    --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED
    ```
2.  **补丁应用**：生成的补丁文件通常包含绝对路径。在应用补丁时（使用 `patch` 命令），可能需要根据你的目录结构调整 `-p` 参数（如 `-p5`）。推荐使用 `batch_verify_patches.py`，它会自动处理路径剥离问题。
3.  **超时设置**：对于大型项目（如 Math, Closure），建议增加 `-DwaitTime` 参数（默认 10 分钟可能不够），以免在找到解之前被强制终止。

## 📂 目录结构说明

*   `src/`: ARJA 源代码
*   `bin/`: 编译后的 class 文件
*   `lib/`: 项目依赖库
*   `external/`: 外部依赖源码（如 GZoltar 修改版）
*   `detail/`: **[重要]** 详细的升级文档和修复日志
*   `test_arja_ns.sh`: 主运行脚本
*   `batch_verify_patches.py`: 补丁验证工具
