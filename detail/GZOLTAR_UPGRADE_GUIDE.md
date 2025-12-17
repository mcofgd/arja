# GZoltar 升级指南

## 问题背景

GZoltar 0.1.1 在 Java 11 环境下无法正常工作，导致 ARJA 无法完成故障定位和补丁生成。

## 解决方案对比

### 方案1：使用 Java 8 运行（推荐，最简单）⭐

**优点：**
- 无需修改代码
- 立即可用
- 稳定可靠

**缺点：**
- 需要安装 Java 8
- 不是真正的 Java 11 解决方案

**实施步骤：**

```bash
# 1. 安装 Java 8
sudo apt-get update
sudo apt-get install openjdk-8-jdk

# 2. 切换到 Java 8
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# 3. 验证版本
java -version  # 应该显示 1.8.x

# 4. 重新编译 ARJA
cd /home/x/arja
mvn clean compile

# 5. 运行测试
cd /home/x/defects4j_test
bash /home/x/arja/test_arja_ns.sh
```

### 方案2：升级到 GZoltar 1.7.3+（长期方案）

**优点：**
- 完全支持 Java 11+
- 获得最新功能和性能改进
- 长期可维护

**缺点：**
- API 完全不同，需要大量代码修改
- 需要时间测试和验证
- 可能引入新的问题

**当前状态：**
- ✅ pom.xml 已更新到 GZoltar 1.7.3
- ❌ GZoltarFaultLocalizer.java 需要完全重写
- ❌ 需要学习新的 API 并适配

**GZoltar 1.7.3 API 主要变化：**

1. **不再有 `GZoltar` 类**
   - 0.1.1: `GZoltar gz = new GZoltar(projLoc);`
   - 1.7.3: 使用 `GZoltarAgent` 和 `Instrumenter`

2. **测试执行方式改变**
   - 0.1.1: `gz.run()`
   - 1.7.3: 需要使用 JUnit Runner 或自定义执行器

3. **故障定位方式改变**
   - 0.1.1: `gz.getSuspiciousStatements()`
   - 1.7.3: 需要使用 `com.gzoltar.fl` 包中的类

**实施步骤（需要大量工作）：**

1. **研究 GZoltar 1.7.3 文档**
   - 官方文档：https://github.com/GZoltar/gzoltar
   - 示例代码：https://github.com/GZoltar/gzoltar/tree/master/com.gzoltar.examples

2. **重写 GZoltarFaultLocalizer.java**
   ```java
   // 需要使用新的 API 结构
   // 参考 GZoltar 1.7.3 的示例代码
   ```

3. **更新依赖**
   ```xml
   <!-- 已完成 -->
   <dependency>
       <groupId>com.gzoltar</groupId>
       <artifactId>com.gzoltar.core</artifactId>
       <version>1.7.3</version>
   </dependency>
   ```

4. **测试验证**
   - 单元测试
   - 集成测试
   - 端到端测试

### 方案3：使用预计算的故障定位数据

如果已经有 GZoltar 的输出数据，可以跳过故障定位阶段：

```bash
java -cp "lib/*:bin" us.msu.cse.repair.Main Arja \
    -DgzoltarDataDir "/path/to/gzoltar/data" \
    ...其他参数...
```

这会使用 `GZoltarFaultLocalizer2` 类，直接读取预先计算的数据。

## 推荐行动计划

### 短期（立即可行）

1. **使用 Java 8 运行 ARJA**
   - 按照方案1的步骤操作
   - 验证 ARJA 功能正常
   - 确认可以生成补丁

### 中期（1-2周）

1. **研究 GZoltar 1.7.3 API**
   - 阅读官方文档
   - 运行示例代码
   - 理解新的架构

2. **创建 GZoltar 1.7.3 适配器**
   - 创建新的 `GZoltarFaultLocalizer17.java`
   - 保留旧的 `GZoltarFaultLocalizer.java`（用于 Java 8）
   - 根据 Java 版本自动选择

### 长期（1-2月）

1. **完全迁移到 GZoltar 1.7.3**
   - 移除对 GZoltar 0.1.1 的依赖
   - 全面测试
   - 更新文档

## 当前项目状态

### 已完成 ✅
- Java 11 编译器兼容性
- AST 解析器兼容性
- 线程中断处理
- 模块系统支持
- 修改点保留逻辑
- 增强日志输出
- pom.xml 更新到 GZoltar 1.7.3

### 待完成 ❌
- GZoltarFaultLocalizer.java 适配新 API
- 测试验证

## 快速开始（使用 Java 8）

```bash
#!/bin/bash
# quick_start_java8.sh

echo "=== ARJA Java 8 快速启动 ==="

# 检查 Java 8 是否安装
if ! command -v /usr/lib/jvm/java-8-openjdk-amd64/bin/java &> /dev/null; then
    echo "安装 Java 8..."
    sudo apt-get update
    sudo apt-get install -y openjdk-8-jdk
fi

# 切换到 Java 8
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

echo "当前 Java 版本:"
java -version

# 编译 ARJA
echo "编译 ARJA..."
cd /home/x/arja
mvn clean compile

# 运行测试
echo "运行测试..."
cd /home/x/defects4j_test
bash /home/x/arja/test_arja_ns.sh

echo "=== 完成 ==="
```

## 参考资料

- GZoltar 官方仓库：https://github.com/GZoltar/gzoltar
- GZoltar 文档：https://github.com/GZoltar/gzoltar/wiki
- ARJA 原始项目：https://github.com/yyxhdy/arja
- Java 11 迁移指南：https://docs.oracle.com/en/java/javase/11/migrate/

## 总结

**当前最佳方案：使用 Java 8 运行 ARJA**

这是最快速、最可靠的解决方案。虽然不是真正的 Java 11 解决方案，但可以让 ARJA 立即工作。

长期来看，应该逐步迁移到 GZoltar 1.7.3，但这需要大量的开发和测试工作。