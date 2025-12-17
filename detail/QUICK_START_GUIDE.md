# ARJA Java 11 快速开始指南

## 🎯 概述

ARJA 项目已经完成了 Java 11 的代码适配，但由于第三方依赖 GZoltar 的兼容性问题，目前有两种运行方式可供选择。

## ✅ 方案对比

| 特性 | Java 8 方案 | Java 11 方案 |
|------|------------|-------------|
| **可用性** | ✅ 立即可用 | ⚠️ 部分可用 |
| **故障定位** | ✅ 完整支持 | ❌ 需要升级 |
| **补丁生成** | ✅ 完整支持 | ❌ 受故障定位影响 |
| **Java 特性** | Java 8 | Java 11+ |
| **推荐度** | ⭐⭐⭐⭐⭐ | ⭐⭐ |

## 🚀 方案 1：使用 Java 8 运行（推荐）

### 优点
- ✅ 所有功能完整可用
- ✅ GZoltar 0.1.1 稳定运行
- ✅ 无需额外配置
- ✅ 经过充分测试

### 快速开始

```bash
# 1. 使用自动脚本（推荐）
bash quick_start_java8.sh test

# 2. 手动运行
# 2.1 切换到 Java 8
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# 2.2 编译项目
mvn clean compile

# 2.3 运行 ARJA
java -cp target/classes:lib/* us.msu.cse.repair.Main <参数>
```

### 验证安装

```bash
# 检查 Java 版本
java -version
# 应该显示：openjdk version "1.8.x"

# 编译测试
mvn clean compile
# 应该显示：BUILD SUCCESS
```

## 🔧 方案 2：Java 11 + 手动故障定位

### 当前状态
- ✅ 代码已完全适配 Java 11
- ✅ 编译器和 AST 解析器已更新
- ✅ 模块系统支持已添加
- ❌ GZoltar 故障定位不可用

### 适用场景
如果你已经有故障定位结果（可疑行列表），可以使用 Java 11 运行：

```bash
# 1. 使用 Java 11
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# 2. 编译项目
mvn clean compile

# 3. 提供预先计算的故障定位结果
# （需要修改代码跳过 GZoltar 步骤）
```

### 限制
- ⚠️ 无法自动进行故障定位
- ⚠️ 需要手动提供可疑行
- ⚠️ 不推荐用于生产环境

## 🔮 方案 3：等待 GZoltar 1.7.3 完整集成

### 当前进度
- ✅ pom.xml 已更新到 GZoltar 1.7.3
- ✅ 创建了新的故障定位器框架
- ⏳ 核心实现待完成（估计 10-15 天）

### 所需工作
1. 实现测试执行器（3-4 天）
2. 集成覆盖率收集（2-3 天）
3. Spectrum 构建和分析（2-3 天）
4. 测试和调试（3-5 天）

### 跟踪进度
查看 `GZOLTAR_UPGRADE_STATUS.md` 了解最新状态。

## 📋 完整运行示例

### 使用 Java 8 运行 ARJA

```bash
#!/bin/bash

# 设置 Java 8 环境
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# 编译项目
cd /home/x/arja
mvn clean compile

# 运行 ARJA（示例参数）
java -cp "target/classes:lib/*:external/lib/*" \
  us.msu.cse.repair.Main \
  --proj-dir /path/to/project \
  --proj-src /path/to/src \
  --proj-bin /path/to/bin \
  --proj-test-src /path/to/test \
  --proj-test-bin /path/to/test-bin \
  --test-class "com.example.TestClass" \
  --algorithm arja \
  --max-generations 100 \
  --population-size 40
```

### 使用 Defects4J 基准测试

```bash
#!/bin/bash

# 1. 设置 Java 8
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# 2. 检出 Defects4J 缺陷
defects4j checkout -p Lang -v 1b -w /tmp/lang_1b

# 3. 编译 ARJA
cd /home/x/arja
mvn clean compile

# 4. 运行 ARJA
bash test_arja_ns.sh
```

## 🐛 故障排除

### 问题 1：Maven 编译失败

```bash
# 清理并重新下载依赖
mvn clean
mvn dependency:purge-local-repository
mvn dependency:resolve
mvn compile
```

### 问题 2：Java 版本错误

```bash
# 检查当前 Java 版本
java -version
javac -version

# 如果不是 Java 8，重新设置
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

### 问题 3：GZoltar 卡死

```bash
# 确认使用 Java 8
java -version

# 如果使用 Java 11，切换回 Java 8
# GZoltar 0.1.1 不支持 Java 11
```

### 问题 4：找不到类

```bash
# 确保类路径正确
java -cp "target/classes:lib/*:external/lib/*" \
  us.msu.cse.repair.Main --help

# 检查 jar 文件是否存在
ls -la lib/
ls -la external/lib/
```

## 📚 相关文档

- **JAVA11_DIAGNOSIS_AND_SOLUTION.md** - 详细的技术诊断
- **GZOLTAR_UPGRADE_STATUS.md** - GZoltar 升级状态
- **FINAL_SUMMARY.md** - 完整项目总结
- **UPGRADE_NOTES.md** - 升级说明
- **README_CN.md** - 中文说明文档

## 💡 最佳实践

### 1. 开发环境设置

```bash
# 在 ~/.bashrc 或 ~/.zshrc 中添加
alias java8='export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64; export PATH=$JAVA_HOME/bin:$PATH'
alias java11='export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64; export PATH=$JAVA_HOME/bin:$PATH'

# 使用时
java8  # 切换到 Java 8
java11 # 切换到 Java 11
```

### 2. 项目编译

```bash
# 始终先清理
mvn clean

# 然后编译
mvn compile

# 或者一步完成
mvn clean compile
```

### 3. 运行测试

```bash
# 使用提供的测试脚本
bash test_arja_ns.sh

# 或者使用快速验证脚本
bash quick_validate.sh
```

## 🎓 学习资源

### ARJA 相关
- 原始论文：https://arxiv.org/abs/1712.07804
- GitHub 仓库：https://github.com/yyxhdy/arja

### GZoltar 相关
- GZoltar 0.1.1：https://github.com/GZoltar/gzoltar/releases/tag/v0.1.1
- GZoltar 1.7.3：https://github.com/GZoltar/gzoltar

### Java 版本管理
- SDKMAN：https://sdkman.io/
- jEnv：https://www.jenv.be/

## 📞 获取帮助

如果遇到问题：

1. **检查文档**：查看相关的 .md 文件
2. **查看日志**：检查 `defects4j_test/logs/` 目录
3. **验证环境**：确认 Java 版本和依赖
4. **清理重试**：`mvn clean compile`

## 🎯 推荐工作流程

```bash
# 1. 克隆或更新项目
cd /home/x/arja
git pull  # 如果使用 git

# 2. 切换到 Java 8
bash quick_start_java8.sh

# 3. 编译项目
mvn clean compile

# 4. 运行测试
bash test_arja_ns.sh

# 5. 查看结果
ls -la defects4j_test/logs/
```

## ✨ 总结

**当前最佳方案：使用 Java 8 运行 ARJA**

- ✅ 稳定可靠
- ✅ 功能完整
- ✅ 立即可用
- ✅ 经过验证

**未来计划：完成 GZoltar 1.7.3 集成**

- 🔄 正在进行中
- 📅 预计 10-15 天完成
- 🎯 将支持 Java 11+

---

**最后更新：** 2025-12-07  
**版本：** 1.0  
**状态：** Java 8 方案可用，Java 11 方案开发中