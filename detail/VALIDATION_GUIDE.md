# ARJA Java 11 升级版验证指南

## 📋 验证前准备

### 1. 环境要求检查

确保你的系统满足以下要求：

```bash
# 检查 Java 版本（必须是 11 或更高）
java -version
# 应该显示：openjdk version "11.x.x" 或更高

# 检查 Defects4J 是否安装
defects4j version
# 应该显示：Defects4J v3.0.1 或更高

# 检查 Maven（可选，用于编译）
mvn -version
```

### 2. 配置脚本路径

编辑 `test_arja_ns.sh` 脚本，修改以下配置变量：

```bash
DEFECTS4J_HOME="$HOME/defects4j"          # 你的 Defects4J 安装路径
JAVA11_HOME="/usr/lib/jvm/java-11-openjdk-amd64"  # 你的 Java 11 路径
WORK_DIR="$HOME/defects4j_test"           # 工作目录
PROJECT_NAME="Lang_1b"                    # 测试项目（推荐简单 bug）
ARJA_HOME="$HOME/arja"                    # ARJA 项目路径
```

**如何找到 Java 11 路径**：
```bash
# Linux
update-alternatives --list java
# 或
readlink -f $(which java)

# 查看所有已安装的 Java
ls /usr/lib/jvm/
```

## 🔧 验证步骤

### 步骤 1：编译 ARJA 项目

#### 方式 A：使用 Maven（推荐）

```bash
cd ~/arja
mvn clean compile
```

**验证编译成功**：
- 检查 `target/classes/` 目录是否存在
- 检查是否有编译错误

#### 方式 B：使用 javac

```bash
cd ~/arja
rm -rf bin
mkdir bin
javac --release 11 -cp "lib/*:" -d bin $(find src/main/java -name '*.java')
```

**验证编译成功**：
```bash
# 检查 Main.class 是否存在
ls -la bin/us/msu/cse/repair/Main.class
```

### 步骤 2：快速功能测试

测试 ARJA 是否能正常启动并显示参数列表：

```bash
cd ~/arja
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -cp "lib/*:bin" us.msu.cse.repair.Main -listParameters
```

**预期结果**：应该显示参数列表，没有错误。

### 步骤 3：使用验证脚本

运行修改后的验证脚本：

```bash
cd ~/arja
chmod +x test_arja_ns.sh
./test_arja_ns.sh
```

脚本会自动：
1. ✅ 检查环境（Java 11、Defects4J）
2. ✅ 编译 ARJA 项目
3. ✅ 准备 Defects4J 项目
4. ✅ 运行 ARJA 修复流程
5. ✅ 分析结果

### 步骤 4：手动验证（可选）

如果你想手动验证，可以按照以下步骤：

#### 4.1 准备 Defects4J 项目

```bash
export DEFECTS4J_HOME=/path/to/defects4j
cd /tmp
$DEFECTS4J_HOME/framework/bin/defects4j checkout -p Lang -v 1b -w Lang_1_buggy
cd Lang_1_buggy
$DEFECTS4J_HOME/framework/bin/defects4j compile
```

#### 4.2 获取项目路径信息

```bash
cd Lang_1_buggy
SRC_DIR=$(defects4j export -p dir.src.classes)
BIN_DIR=$(defects4j export -p dir.bin.classes)
TEST_DIR=$(defects4j export -p dir.bin.tests)
CP_TEST=$(defects4j export -p cp.test)

echo "源码目录: $SRC_DIR"
echo "类文件目录: $BIN_DIR"
echo "测试目录: $TEST_DIR"
```

**验证路径**：
```bash
# Defects4J v3.0.1 应该使用 build/ 目录
ls -la build/classes
ls -la build/tests
```

#### 4.3 运行 ARJA

```bash
cd ~/arja
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -cp "lib/*:bin" us.msu.cse.repair.Main Arja \
     -DsrcJavaDir /tmp/Lang_1_buggy/src/main/java \
     -DbinJavaDir /tmp/Lang_1_buggy/build/classes \
     -DbinTestDir /tmp/Lang_1_buggy/build/tests \
     -Ddependences $(defects4j export -p cp.test | tr ',' ':') \
     -DpopulationSize 40 \
     -DmaxGenerations 10 \
     -DwaitTime 30000 \
     -DpatchOutputRoot /tmp/arja_patches_lang_1
```

## ✅ 验证检查清单

### 编译验证

- [ ] Java 11 编译无错误
- [ ] `bin/us/msu/cse/repair/Main.class` 存在
- [ ] 所有依赖库加载正常

### 功能验证

- [ ] ARJA 能正常启动（`-listParameters` 命令成功）
- [ ] 能正确解析命令行参数
- [ ] 时区自动设置为 `America/Los_Angeles`

### Defects4J 集成验证

- [ ] 能正确检出 Defects4J 项目
- [ ] 能识别 Defects4J v3.0.1 的路径结构（`build/` 目录）
- [ ] 能正确获取类路径信息
- [ ] 能执行测试并收集结果

### 修复流程验证

- [ ] 能进行缺陷定位
- [ ] 能解析 AST 并识别修改点
- [ ] 能执行测试评估
- [ ] 能生成补丁（如果找到修复）

## 🐛 常见问题排查

### 问题 1：编译错误

**错误信息**：`javac: invalid target release: 11`

**解决方案**：
```bash
# 检查 Java 版本
java -version
# 如果版本不对，设置 JAVA_HOME
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

### 问题 2：运行时模块访问错误

**错误信息**：`java.lang.IllegalAccessException`

**解决方案**：
- 确保运行时添加了 `--add-opens` 参数
- 检查脚本中的 Java 命令是否包含这些参数

### 问题 3：路径错误

**错误信息**：`The build directory of Java classes is not specified!`

**解决方案**：
```bash
# 检查 Defects4J 项目是否正确编译
cd /path/to/defects4j/project
defects4j compile

# 检查路径是否存在
defects4j export -p dir.bin.classes
defects4j export -p dir.bin.tests

# 验证目录
ls -la build/classes  # Defects4J v3.0.1
# 或
ls -la target/classes  # 旧版本
```

### 问题 4：Defects4J 版本不匹配

**错误信息**：找不到 `build/` 目录

**解决方案**：
- 确保使用 Defects4J v3.0.1
- ARJA 已自动适配新旧路径，但如果仍有问题，检查 `DataSet.java` 中的路径逻辑

### 问题 5：时区问题

**错误信息**：测试结果不一致

**解决方案**：
- ARJA 已自动设置时区，通常无需手动配置
- 如果仍有问题，检查系统时区设置

## 📊 验证结果解读

### 成功标志

1. **编译成功**：
   - 无编译错误
   - 所有类文件生成

2. **运行成功**：
   - ARJA 正常启动
   - 能读取项目信息
   - 能执行测试

3. **修复成功**（如果找到补丁）：
   - 补丁目录存在
   - 补丁文件生成
   - 补丁通过所有测试

### 失败标志

1. **编译失败**：
   - 检查 Java 版本
   - 检查依赖库

2. **运行失败**：
   - 检查参数是否正确
   - 检查路径是否存在
   - 检查 JVM 参数

3. **修复失败**（未找到补丁）：
   - 这是正常的，ARJA 不一定能找到所有 bug 的修复
   - 可以尝试更简单的 bug 或增加搜索参数

## 🔍 详细日志分析

验证脚本会生成详细日志，位置在：`$WORK_DIR/logs/arja_${PROJECT_NAME}_*.log`

### 关键日志信息

1. **启动信息**：
   ```
   Setting timezone to America/Los_Angeles
   Loading modification points...
   ```

2. **测试执行**：
   ```
   Running tests...
   Tests passed: X, failed: Y
   ```

3. **补丁生成**：
   ```
   Patch generated: patches_xxx/patch_1.patch
   ```

### 错误模式识别

- `IllegalAccessException` → 需要 `--add-opens` 参数
- `ClassNotFoundException` → 类路径配置错误
- `The build directory is not specified` → 路径参数缺失
- `UnsupportedClassVersionError` → 编译版本不匹配

## 📝 验证报告模板

验证完成后，记录以下信息：

```
验证日期：YYYY-MM-DD
Java 版本：11.x.x
Defects4J 版本：3.0.1
测试项目：Lang_1b

编译结果：✅ 成功 / ❌ 失败
运行结果：✅ 成功 / ❌ 失败
修复结果：✅ 找到补丁 / ⚠️ 未找到补丁（正常）

遇到的问题：
1. [描述问题]
2. [描述问题]

解决方案：
1. [解决方案]
2. [解决方案]
```

## 🎯 下一步

验证成功后，你可以：

1. **测试更多项目**：修改 `PROJECT_NAME` 测试不同的 bug
2. **调整参数**：修改种群大小、代数等参数
3. **使用 Novelty Search**：修改 `noveltySearchMode` 参数
4. **集成到 CI/CD**：将验证脚本集成到自动化流程

## 📚 相关文档

- [UPGRADE_NOTES.md](UPGRADE_NOTES.md) - 详细升级记录
- [README_CN.md](README_CN.md) - 中文使用指南
- [README.md](README.md) - 原始英文文档

## 💡 提示

1. **首次验证建议使用简单 bug**：如 `Lang_1b`, `Math_1b` 等
2. **逐步增加复杂度**：先验证基本功能，再测试复杂场景
3. **保存日志**：验证过程中的日志有助于问题排查
4. **定期验证**：代码更新后重新验证确保兼容性

