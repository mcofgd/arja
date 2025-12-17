# ARJA 故障排查指南

## 问题：ARJA 运行但没有生成补丁

### 症状
- ARJA 正常启动
- 看到大量 "One fitness evaluation starts..." 消息
- 但没有看到 "One fitness evaluation is finished..." 消息
- 没有生成补丁

### 可能原因分析

#### 1. 测试执行超时或卡住（最可能）

**表现**：
- 日志中只有 "One fitness evaluation starts..." 没有完成消息
- 评估次数很多（如 3600 次）但都没有完成

**原因**：
- 测试执行时间超过 `waitTime` 设置（默认 90 秒）
- 测试执行器卡在某个测试上
- 类加载或测试运行出现问题

**解决方案**：

1. **增加 waitTime**：
   ```bash
   -DwaitTime 180000  # 增加到 180 秒
   ```

2. **使用 ExternalTestExecutor**（更稳定）：
   ```bash
   -DtestExecutorName ExternalTestExecutor
   ```

3. **减少测试数量**：
   ```bash
   -DtestFiltered true  # 启用测试过滤
   -Dpercentage 0.1     # 只使用 10% 的测试
   ```

#### 2. 编译失败但没有输出错误

**表现**：
- 没有看到 "Compilation fails!" 消息
- 但评估没有完成

**原因**：
- 编译过程异常但没有被捕获
- 编译错误被静默处理

**解决方案**：

1. **检查编译错误**：
   查看日志中是否有编译相关的异常

2. **手动测试编译**：
   ```bash
   # 在 Defects4J 项目目录中
   defects4j compile
   ```

#### 3. 测试执行器配置问题

**表现**：
- 使用 InternalTestExecutor 时可能有问题
- 类路径配置不正确

**解决方案**：

1. **切换到 ExternalTestExecutor**：
   ```bash
   -DtestExecutorName ExternalTestExecutor
   ```

2. **检查类路径**：
   ```bash
   defects4j export -p cp.test
   defects4j export -p cp.compile
   ```

#### 4. Java 11 兼容性问题

**表现**：
- 在 Java 11 环境下运行时出现问题
- 模块系统访问限制

**解决方案**：

1. **确保添加 JVM 参数**：
   ```bash
   java --add-opens java.base/java.lang=ALL-UNNAMED \
        --add-opens java.base/java.util=ALL-UNNAMED \
        ...
   ```

2. **检查 Java 版本**：
   ```bash
   java -version  # 应该是 11 或更高
   ```

## 诊断步骤

### 步骤 1：检查日志详细信息

```bash
# 查看完整日志
cat /path/to/arja_log.log

# 查找错误
grep -i "error\|exception\|fail" /path/to/arja_log.log

# 查找测试相关消息
grep -i "test\|timeout\|compilation" /path/to/arja_log.log
```

### 步骤 2：测试 Defects4J 项目

```bash
cd /path/to/defects4j/project
defects4j test
```

确保测试能正常运行。

### 步骤 3：手动运行单个测试

```bash
# 获取测试类路径
CP=$(defects4j export -p cp.test)

# 运行单个测试
java -cp "$CP" org.junit.runner.JUnitCore TestClassName#testMethod
```

### 步骤 4：使用更简单的配置

```bash
# 减少种群大小和代数
-DpopulationSize 20
-DmaxGenerations 10

# 增加超时时间
-DwaitTime 180000

# 使用测试过滤
-DtestFiltered true
-Dpercentage 0.1
```

## 推荐的运行配置

### 配置 1：快速验证（适合调试）

```bash
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -cp "lib/*:bin" us.msu.cse.repair.Main Arja \
     -DsrcJavaDir /path/to/source \
     -DbinJavaDir /path/to/build/classes \
     -DbinTestDir /path/to/build/tests \
     -Ddependences $(defects4j export -p cp.test | tr ',' ':') \
     -DpopulationSize 20 \
     -DmaxGenerations 5 \
     -DwaitTime 180000 \
     -DtestExecutorName ExternalTestExecutor \
     -DtestFiltered true \
     -Dpercentage 0.1 \
     -DpatchOutputRoot /tmp/arja_patches
```

### 配置 2：完整运行（适合实际修复）

```bash
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -cp "lib/*:bin" us.msu.cse.repair.Main Arja \
     -DsrcJavaDir /path/to/source \
     -DbinJavaDir /path/to/build/classes \
     -DbinTestDir /path/to/build/tests \
     -Ddependences $(defects4j export -p cp.test | tr ',' ':') \
     -DpopulationSize 40 \
     -DmaxGenerations 50 \
     -DwaitTime 120000 \
     -DtestExecutorName ExternalTestExecutor \
     -DpatchOutputRoot /tmp/arja_patches
```

## 常见错误消息及解决方案

### "One fitness evaluation starts..." 但没有完成

**原因**：测试执行超时或卡住

**解决**：
1. 增加 `waitTime`
2. 使用 `ExternalTestExecutor`
3. 启用测试过滤

### "Compilation fails!"

**原因**：代码编译失败

**解决**：
1. 检查修改后的代码语法
2. 查看编译错误详情
3. 确保依赖正确

### "Timeout occurs!"

**原因**：测试执行超时

**解决**：
1. 增加 `waitTime`
2. 减少测试数量
3. 使用测试过滤

### "The build directory is not specified!"

**原因**：路径参数缺失

**解决**：
1. 检查 `-DbinJavaDir` 和 `-DbinTestDir` 参数
2. 使用 `defects4j export` 获取正确路径

## 性能优化建议

1. **使用测试过滤**：
   - `-DtestFiltered true`
   - `-Dpercentage 0.1`（只使用 10% 的测试进行初步评估）

2. **使用 ExternalTestExecutor**：
   - 更稳定，不容易卡住
   - 更好的超时控制

3. **调整搜索参数**：
   - 减少 `populationSize` 和 `maxGenerations` 进行快速测试
   - 增加这些参数进行完整搜索

4. **选择合适的 bug**：
   - 从简单的 bug 开始（如 Lang_1b, Math_1b）
   - 逐步尝试更复杂的 bug

## 联系支持

如果以上方法都无法解决问题，请提供：
1. 完整的日志文件
2. Defects4J 版本信息
3. Java 版本信息
4. 使用的运行参数
5. 具体的错误消息

