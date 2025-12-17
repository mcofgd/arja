# GZoltar 升级状态报告

## 当前状态

### ✅ 已完成的工作

1. **pom.xml 更新**
   - 升级 ASM 从 5.2 → 9.6 (支持 Java 11+)
   - 添加 GZoltar 1.7.3 依赖（com.gzoltar.core, com.gzoltar.agent.rt, com.gzoltar.fl）
   - 升级 Eclipse JDT 到 3.32.0
   - 添加 JaCoCo 0.8.11
   - 更新所有 Eclipse 平台依赖

2. **创建新的故障定位器框架**
   - 创建了 `GZoltarFaultLocalizer173.java`
   - 实现了基本的 API 适配层
   - 定义了测试执行结果数据结构

### ⚠️ 未完成的工作

1. **GZoltar 1.7.3 完整集成**
   - 测试执行器实现（需要与 JUnit 4 集成）
   - 字节码插桩配置
   - 覆盖率数据收集
   - Spectrum 构建和分析

2. **API 差异适配**
   - GZoltar 0.1.1 使用简单的 `GZoltar.run()` 方法
   - GZoltar 1.7.3 需要手动配置 Agent、Collector、Spectrum 等

## 技术挑战

### GZoltar 1.7.3 API 复杂度

GZoltar 1.7.3 的 API 与 0.1.1 完全不同：

**0.1.1 版本（简单）：**
```java
GZoltar gz = new GZoltar(projLoc);
gz.addTestToExecute(testClass);
gz.addClassToInstrument(javaClass);
gz.run();  // 一键执行
```

**1.7.3 版本（复杂）：**
```java
// 1. 配置 Agent
AgentConfigs configs = new AgentConfigs();

// 2. 启动 Collector
Collector collector = Collector.instance();
collector.start();

// 3. 执行测试（需要自己实现 JUnit 运行器）
// ... 复杂的测试执行逻辑 ...

// 4. 构建 Spectrum
SpectrumBuilder builder = new SpectrumBuilder();
ISpectrum spectrum = builder.build(collector);

// 5. 应用故障定位公式
IFormula formula = new Ochiai();
// ... 更多配置 ...
```

### 主要问题

1. **测试执行集成**
   - GZoltar 1.7.3 不提供内置的测试运行器
   - 需要手动集成 JUnit 4 并收集覆盖率数据
   - 需要处理类加载器隔离

2. **字节码插桩**
   - 需要在测试执行前插桩目标类
   - 需要配置正确的类路径和插桩级别

3. **数据收集**
   - 需要在测试执行期间收集覆盖率数据
   - 需要正确处理多线程和并发

## 推荐方案

### 方案 1：使用 Java 8 运行（推荐）✅

**优点：**
- 立即可用，无需修改代码
- GZoltar 0.1.1 在 Java 8 下稳定运行
- 所有功能完整可用

**缺点：**
- 无法使用 Java 11+ 的新特性
- 需要维护 Java 8 环境

**实施步骤：**
```bash
# 使用提供的脚本
bash quick_start_java8.sh test
```

### 方案 2：完成 GZoltar 1.7.3 集成（长期）

**优点：**
- 完全支持 Java 11+
- 使用最新的故障定位技术
- 更好的性能和准确性

**缺点：**
- 需要 1-2 周的开发时间
- 需要深入理解 GZoltar 1.7.3 API
- 需要大量测试验证

**所需工作量估算：**
- 测试执行器实现：3-4 天
- 覆盖率收集集成：2-3 天
- Spectrum 构建和分析：2-3 天
- 测试和调试：3-5 天
- **总计：10-15 天**

### 方案 3：使用替代故障定位工具

**可选工具：**
1. **JaCoCo + 自定义分析**
   - 使用 JaCoCo 收集覆盖率
   - 实现 Ochiai/Tarantula 等公式
   - 工作量：5-7 天

2. **Defects4J 内置的故障定位**
   - 如果使用 Defects4J 基准测试
   - 可以直接使用其故障定位结果
   - 工作量：2-3 天

3. **Cobertura**
   - 较老但稳定的覆盖率工具
   - 支持 Java 8-11
   - 工作量：3-5 天

## 当前代码状态

### 已创建的文件

1. **GZoltarFaultLocalizer173.java**
   - 位置：`src/main/java/us/msu/cse/repair/core/faultlocalizer/`
   - 状态：框架完成，核心逻辑待实现
   - 需要：实现 `GZoltarTestRunner.runTests()` 方法

2. **pom.xml**
   - 状态：已更新所有依赖
   - GZoltar 1.7.3 依赖已添加
   - 需要：验证依赖下载成功

### 需要修改的文件

1. **AbstractRepairProblem.java**
   - 需要：添加选项使用新的故障定位器
   - 建议：添加配置参数 `faultLocalizerVersion`

2. **配置文件**
   - 需要：添加故障定位器选择配置
   - 建议：`gzoltar.version=1.7.3` 或 `0.1.1`

## 下一步行动

### 立即可行（1-2 小时）

1. **验证 Maven 依赖**
   ```bash
   mvn dependency:tree | grep gzoltar
   ```

2. **创建配置开关**
   - 在 `AbstractRepairProblem.java` 中添加版本选择
   - 允许用户选择使用哪个版本的 GZoltar

3. **文档更新**
   - 更新 README 说明两种运行方式
   - 提供清晰的选择指南

### 短期目标（1-2 天）

1. **实现基本的测试执行器**
   - 集成 JUnit 4 运行器
   - 收集测试结果（通过/失败）

2. **简化的覆盖率收集**
   - 使用 JaCoCo 替代 GZoltar 的插桩
   - 实现基本的行覆盖率分析

### 长期目标（1-2 周）

1. **完整的 GZoltar 1.7.3 集成**
   - 实现所有必需的组件
   - 全面测试验证

2. **性能优化**
   - 优化插桩和数据收集
   - 减少内存占用

## 建议

基于当前情况，我强烈建议：

1. **短期：使用 Java 8 运行**
   - 这是最快速、最可靠的解决方案
   - 可以立即开始使用 ARJA 进行缺陷修复

2. **中期：评估替代方案**
   - 考虑使用 JaCoCo + 自定义故障定位
   - 这比完整集成 GZoltar 1.7.3 更简单

3. **长期：完整升级**
   - 如果项目需要长期维护
   - 投入时间完成 GZoltar 1.7.3 集成

## 参考资源

- GZoltar 1.7.3 文档：https://github.com/GZoltar/gzoltar
- GZoltar 示例：https://github.com/GZoltar/gzoltar/tree/master/com.gzoltar.examples
- JaCoCo 文档：https://www.jacoco.org/jacoco/trunk/doc/
- Defects4J：https://github.com/rjust/defects4j

## 联系与支持

如果需要进一步的帮助或有任何问题，请参考：
- ARJA 原始项目：https://github.com/yyxhdy/arja
- GZoltar 问题追踪：https://github.com/GZoltar/gzoltar/issues

---

**最后更新：** 2025-12-07
**状态：** GZoltar 1.7.3 框架已创建，核心实现待完成
**推荐：** 使用 Java 8 运行作为当前最佳方案