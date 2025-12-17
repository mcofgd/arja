# JaCoCo 故障定位器实施计划

## 📋 项目状态

### 当前情况
- ✅ pom.xml 已配置 JaCoCo 0.8.11 依赖
- ✅ 创建了 `JaCoCoFaultLocalizer.java` 框架
- ⏳ 需要解决依赖版本兼容性问题
- ⏳ 需要完成实现和测试

### 核心问题
1. **Defects4J v3.0.1 强制使用 Java 11**
2. **GZoltar 0.1.1 与 Java 11 不兼容**
3. **需要替代的故障定位方案**

## 🎯 解决方案：JaCoCo + Ochiai

### 为什么选择 JaCoCo？
- ✅ 完全支持 Java 11+
- ✅ 成熟稳定的覆盖率工具
- ✅ 活跃维护（最新版本 0.8.11）
- ✅ 易于集成
- ✅ 性能优秀

### 实现策略
使用 JaCoCo 收集覆盖率数据，然后实现 Ochiai 公式计算可疑度。

## 📝 详细实施步骤

### 第1步：修复依赖问题（1-2小时）

#### 问题分析
当前 pom.xml 中的 Eclipse 依赖版本过高，导致编译失败。

#### 解决方案
使用与 Java 11 兼容的 Eclipse 版本：

```xml
<!-- 推荐的 Eclipse 依赖版本（Java 11 兼容） -->
<dependency>
    <groupId>org.eclipse.jdt</groupId>
    <artifactId>org.eclipse.jdt.core</artifactId>
    <version>3.24.0</version>
</dependency>
<dependency>
    <groupId>org.eclipse.platform</groupId>
    <artifactId>org.eclipse.core.runtime</artifactId>
    <version>3.20.0</version>
</dependency>
<dependency>
    <groupId>org.eclipse.platform</groupId>
    <artifactId>org.eclipse.core.resources</artifactId>
    <version>3.14.0</version>
</dependency>
```

或者更简单的方案：**使用本地 jar 文件**

```xml
<dependency>
    <groupId>org.eclipse.jdt</groupId>
    <artifactId>org.eclipse.jdt.core</artifactId>
    <version>3.10.0</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/org.eclipse.jdt.core_3.10.0.jar</systemPath>
</dependency>
```

### 第2步：简化 JaCoCo 实现（2-3小时）

当前的 `JaCoCoFaultLocalizer.java` 过于复杂。推荐使用更简单的方法：

#### 方案 A：使用 Defects4J 的覆盖率数据

Defects4J 已经提供了覆盖率信息，我们可以直接使用：

```bash
# Defects4J 可以导出覆盖率矩阵
defects4j coverage -w /path/to/project
```

然后解析这个矩阵文件，计算 Ochiai 分数。

#### 方案 B：使用外部进程运行 JaCoCo

```java
// 伪代码
public class JaCoCoFaultLocalizer implements IFaultLocalizer {
    public JaCoCoFaultLocalizer(...) {
        // 1. 为每个测试运行 JaCoCo agent
        for (String testClass : testClasses) {
            String jacocoAgent = "-javaagent:lib/jacocoagent.jar=destfile=jacoco-" + testClass + ".exec";
            
            // 2. 运行测试
            ProcessBuilder pb = new ProcessBuilder(
                "java", jacocoAgent, "-cp", classpath,
                "org.junit.runner.JUnitCore", testClass
            );
            Process p = pb.start();
            
            // 3. 收集结果
            boolean passed = (p.waitFor() == 0);
            if (passed) {
                positiveTests.add(testClass);
            } else {
                negativeTests.add(testClass);
            }
        }
        
        // 4. 分析所有 .exec 文件
        analyzeCoverage();
        
        // 5. 计算 Ochiai 分数
        calculateSuspiciousness();
    }
}
```

### 第3步：实现 Ochiai 计算（1小时）

```java
/**
 * Ochiai 公式：
 * suspiciousness = ef / sqrt((ef + nf) * (ef + ep))
 * 
 * 其中：
 * ef = 执行该行的失败测试数
 * ep = 执行该行的通过测试数
 * nf = 未执行该行的失败测试数
 * np = 未执行该行的通过测试数
 */
private double calculateOchiai(int ef, int ep, int nf, int np) {
    if (ef == 0) return 0.0;
    
    double denominator = Math.sqrt((ef + nf) * (ef + ep));
    if (denominator == 0) return 0.0;
    
    return ef / denominator;
}
```

### 第4步：集成到 ARJA（30分钟）

修改 `AbstractRepairProblem.java`，添加故障定位器选择：

```java
// 在 invokeFaultLocalizer() 方法中
String faultLocalizerName = System.getProperty("faultLocalizer", "jacoco");

if ("jacoco".equals(faultLocalizerName)) {
    faultLocalizer = new JaCoCoFaultLocalizer(
        binJavaClasses, binExecuteTestClasses,
        binJavaDir, binTestDir, dependences
    );
} else if ("gzoltar".equals(faultLocalizerName)) {
    // 保留旧的 GZoltar（仅用于 Java 8）
    faultLocalizer = new GZoltarFaultLocalizer(...);
}
```

### 第5步：测试验证（1-2小时）

```bash
# 1. 编译项目
mvn clean compile

# 2. 运行测试
java -DfaultLocalizer=jacoco \
     -cp "target/classes:lib/*" \
     us.msu.cse.repair.Main Arja \
     -DsrcJavaDir "/path/to/src" \
     ...

# 3. 验证输出
# 应该看到：
# - Faulty lines found: > 0
# - Total modification points: > 0
```

## 🔧 快速实施方案（推荐）

如果时间紧迫，推荐使用最简单的方案：

### 使用 Defects4J 的故障定位结果

Defects4J 本身就提供故障定位功能：

```bash
# 1. 使用 Defects4J 进行故障定位
defects4j coverage -w /path/to/project -t

# 2. 导出可疑行
defects4j export -p spectra -w /path/to/project

# 3. 解析结果文件
# Defects4J 会生成一个包含可疑度分数的文件
```

然后创建一个简单的适配器：

```java
public class Defects4JFaultLocalizer implements IFaultLocalizer {
    public Defects4JFaultLocalizer(String projectDir) {
        // 1. 调用 defects4j coverage
        ProcessBuilder pb = new ProcessBuilder(
            "defects4j", "coverage", "-w", projectDir
        );
        pb.start().waitFor();
        
        // 2. 读取结果文件
        File spectraFile = new File(projectDir, "spectra");
        parseSpectraFile(spectraFile);
    }
    
    private void parseSpectraFile(File file) {
        // 解析 Defects4J 的输出格式
        // 格式：ClassName#LineNumber,Suspiciousness
    }
}
```

## 📊 工作量估算

| 任务 | 时间 | 优先级 |
|------|------|--------|
| 修复依赖问题 | 1-2小时 | 高 |
| 简化 JaCoCo 实现 | 2-3小时 | 高 |
| 实现 Ochiai 计算 | 1小时 | 中 |
| 集成到 ARJA | 30分钟 | 高 |
| 测试验证 | 1-2小时 | 高 |
| **总计** | **5-8小时** | - |

### 快速方案（使用 Defects4J）
| 任务 | 时间 | 优先级 |
|------|------|--------|
| 创建 Defects4J 适配器 | 1-2小时 | 高 |
| 集成到 ARJA | 30分钟 | 高 |
| 测试验证 | 1小时 | 高 |
| **总计** | **2.5-3.5小时** | - |

## 🚀 立即可行的临时方案

在完成完整实现之前，可以使用以下临时方案：

### 方案：手动提供可疑行

1. 使用 Defects4J 或其他工具获取可疑行
2. 创建一个配置文件 `suspicious_lines.txt`：
   ```
   org.apache.commons.lang3.BooleanUtils,123,0.95
   org.apache.commons.lang3.BooleanUtils,124,0.87
   ```
3. 创建 `ManualFaultLocalizer.java`：
   ```java
   public class ManualFaultLocalizer implements IFaultLocalizer {
       public ManualFaultLocalizer(String configFile) {
           // 读取配置文件
           parseConfigFile(configFile);
       }
   }
   ```

## 📚 参考资源

### JaCoCo 文档
- 官方文档：https://www.jacoco.org/jacoco/trunk/doc/
- Java API：https://www.jacoco.org/jacoco/trunk/doc/api/index.html
- 示例代码：https://github.com/jacoco/jacoco/tree/master/org.jacoco.examples

### Ochiai 公式
- 论文：Jones, J. A., & Harrold, M. J. (2005). Empirical evaluation of the tarantula automatic fault-localization technique.
- 实现参考：GZoltar 源码

### Defects4J
- 文档：https://github.com/rjust/defects4j
- 覆盖率命令：`defects4j coverage -h`

## 💡 建议

基于当前情况，我建议：

1. **短期（今天）**：使用 Defects4J 适配器方案
   - 最快速（2-3小时）
   - 最可靠（利用 Defects4J 现有功能）
   - 立即可用

2. **中期（本周）**：完成 JaCoCo 实现
   - 更通用（不依赖 Defects4J）
   - 更灵活（可用于任何项目）
   - 更专业（完整的故障定位工具）

3. **长期（下月）**：优化和扩展
   - 支持多种故障定位公式（Tarantula, DStar等）
   - 性能优化
   - 并行化处理

## 🎯 下一步行动

**请选择一个方案：**

**A. Defects4J 适配器（推荐，2-3小时）**
- 我将创建 `Defects4JFaultLocalizer.java`
- 集成到 ARJA
- 立即测试验证

**B. 完整 JaCoCo 实现（5-8小时）**
- 修复所有依赖问题
- 完成 JaCoCo 集成
- 全面测试

**C. 手动配置方案（1小时）**
- 创建 `ManualFaultLocalizer.java`
- 提供配置文件格式
- 快速验证流程

---

**当前状态：** 等待您的选择
**推荐方案：** A（Defects4J 适配器）
**预计完成时间：** 2-3小时