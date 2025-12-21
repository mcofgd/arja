# ARJA Java 11 升级与修复全纪录

## 1. 项目背景与目标
本项目旨在将自动程序修复工具 **ARJA** 升级以支持 **Java 11** 环境，并适配 **Defects4J v3.0.1**。原版 ARJA 主要针对 Java 7/8 设计，在现代环境中面临依赖缺失、字节码不兼容以及故障定位失效等问题。

## 2. 遇到的核心问题与解决方案

### 2.1 构建与依赖问题 (Build & Dependencies)
*   **问题现象**：
    *   Maven 构建失败，提示找不到 `tools.jar`。
    *   依赖包版本过老，与 Java 11 模块系统冲突。
*   **解决方案**：
    *   **修改 `pom.xml`**：移除了对 `tools.jar` 的系统依赖（Java 9+ 已移除该库）。
    *   升级 `maven-compiler-plugin` 配置以支持 Java 11。
    *   更新 `commons-io`, `junit` 等基础库版本。
    *   使用 `maven-shade-plugin` 处理依赖打包，确保运行时 Classpath 完整。

### 2.2 故障定位失效 (Fault Localization)
*   **问题现象**：
    *   原版使用的 GZoltar 在 Java 11 下无法正确插桩，导致无法生成覆盖率报告。
    *   切换到 `Defects4JFaultLocalizer` 后，工具报错 `[defects4j] /home/x/arja/external is not a valid working directory`。
*   **根本原因**：
    *   GZoltar 与新版 JDK 不兼容。
    *   `AbstractRepairProblem.java` 中初始化 `Defects4JFaultLocalizer` 时，错误地传递了 `externalProjRoot`（指向 ARJA 自身的 external 目录），而非被测项目的根目录。
*   **代码修复** (`src/main/java/us/msu/cse/repair/core/AbstractRepairProblem.java`)：
    *   修改构造函数调用，移除 `externalProjRoot` 参数。
    *   让 `Defects4JFaultLocalizer` 根据 `binJavaDir` 自动推导项目的真实根目录。

### 2.3 "0 Modification Points" 问题 (关键修复)
*   **问题现象**：
    *   故障定位成功找到了可疑行（Suspicious lines），但 ARJA 提示 `Total modification points: 0`，导致无法生成任何补丁。
*   **根本原因**：
    *   本次测试的目标 Bug (`Math_1b`) 的故障点位于构造函数或初始化逻辑中。
    *   ARJA 的 AST 访问器 `InitASTVisitor.java` 中包含一段逻辑，默认**跳过构造函数中的修改点**，认为那里不适合进行自动修复。
*   **代码修复** (`src/main/java/us/msu/cse/repair/core/util/visitors/InitASTVisitor.java`)：
    *   注释掉了 `if (Helper.isInConstructor(statement))` 检查，允许工具在构造函数中生成修改点。
    *   **结果**：ARJA 成功识别了修改点并生成了有效补丁。

### 2.4 磁盘空间与文件损坏事故
*   **问题现象**：
    *   运行过程中磁盘空间耗尽（Usage 100%），导致 `pom.xml` 和部分 Java 源文件被截断为空文件。
    *   之前的代码修复在恢复过程中丢失。
*   **解决方案**：
    *   清理 `defects4j_test` 临时目录释放空间。
    *   使用 `git restore` 恢复受损文件。
    *   **重新应用** 之前丢失的针对 `AbstractRepairProblem.java` 和 `InitASTVisitor.java` 的关键修复。

## 3. 关键文件修改清单

### `src/main/java/us/msu/cse/repair/core/AbstractRepairProblem.java`
```java
// 修改前：错误地传递 externalProjRoot
faultLocalizer = new Defects4JFaultLocalizer(..., externalProjRoot);

// 修改后：移除参数，启用自动路径推导
faultLocalizer = new Defects4JFaultLocalizer(binJavaClasses, binExecuteTestClasses, binJavaDir, binTestDir, dependences);
```

### `src/main/java/us/msu/cse/repair/core/util/visitors/InitASTVisitor.java`
```java
// 修改前：跳过构造函数
if (Helper.isInConstructor(statement)) {
    System.out.println("Skipping modification point in constructor: " + lcNode);
} else { ... }

// 修改后：允许构造函数修改
// if (Helper.isInConstructor(statement)) { ... } else {
    ModificationPoint mp = new ModificationPoint();
    // ...
    modificationPoints.add(mp);
// }
```

## 4. 验证结果
使用 `Math_1b` 项目进行验证：
1.  **编译**：成功（Java 11）。
2.  **故障定位**：成功识别出 `BigFraction.java` 和 `Fraction.java` 中的溢出检查逻辑。
3.  **补丁生成**：成功生成 90+ 个候选补丁。
4.  **补丁质量**：确认 `Patch_9` 等补丁正确移除了导致异常的过激检查逻辑，符合预期修复方案。

## 5. 如何运行
使用提供的自动化脚本即可一键运行验证：

```bash
./test_arja_ns.sh
```

该脚本会自动：
1.  编译 ARJA。
2.  准备 Defects4J 项目环境。
3.  执行修复流程。
4.  监控日志并提取生成的补丁。
