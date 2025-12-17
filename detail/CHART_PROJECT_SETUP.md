# Chart_1 项目设置指南

## 问题说明

当您尝试使用 Chart_1 项目时遇到错误：
```
cat: defects4j.build.classpath: 没有那个文件或目录
```

**原因：** Chart 项目使用不同的构建系统（Ant），路径结构与 Lang 项目（Maven）不同。

## 解决方案

### 1. 准备 Chart_1 项目

```bash
# 检出项目
defects4j checkout -p Chart -v 1b -w /home/x/defects4j_test/Chart_1

# 进入项目目录
cd /home/x/defects4j_test/Chart_1

# 编译项目
defects4j compile

# 验证编译成功
ls -la build/
# 应该看到 build/classes 和 build/tests 目录
```

### 2. 运行优化测试脚本

```bash
cd /home/x/arja
chmod +x test_arja_optimized.sh
./test_arja_optimized.sh
```

脚本已经更新，现在可以：
- ✅ 自动检测项目是否已编译
- ✅ 兼容 Defects4J v2 和 v3
- ✅ 自动构建 classpath（如果 export 失败）
- ✅ 支持 Ant 和 Maven 项目

### 3. 手动运行（如果需要）

```bash
cd /home/x/arja

# 获取项目路径
cd /home/x/defects4j_test/Chart_1
BIN_DIR="build/classes"
TEST_DIR="build/tests"

# 构建 classpath
CP_TEST="$PWD/$BIN_DIR:$PWD/$TEST_DIR"
CP_TEST="$CP_TEST:/home/x/defects4j/framework/projects/lib/junit-4.12-hamcrest-1.3.jar"
for jar in /home/x/defects4j/framework/projects/Chart/lib/*.jar; do
    CP_TEST="$CP_TEST:$jar"
done

# 运行 ARJA
cd /home/x/arja
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -cp "target/Arja-0.0.1-SNAPSHOT.jar:lib/*" \
     us.msu.cse.repair.Main Arja \
    -DsrcJavaDir /home/x/defects4j_test/Chart_1 \
    -DbinJavaDir /home/x/defects4j_test/Chart_1/build/classes \
    -DbinTestDir /home/x/defects4j_test/Chart_1/build/tests \
    -Ddependences "$CP_TEST" \
    -DexternalProjRoot /home/x/defects4j_test/Chart_1 \
    -DpopulationSize 20 \
    -DmaxGenerations 10 \
    -DwaitTime 600000 \
    -DingredientScreeningMode 0 \
    -DseedLineGenerated true \
    -DmiFilterRule false \
    -DmanipulationFilterRule false \
    -DingredientFilterRule false
```

## 项目路径对比

| 项目 | 构建系统 | 类文件目录 | 测试类目录 |
|------|----------|------------|------------|
| Lang | Maven | target/classes | target/tests |
| Chart | Ant | build/classes | build/tests |
| Math | Maven | target/classes | target/tests |
| Time | Maven | target/classes | target/tests |

## 常见问题

### Q: 如何检查项目是否已编译？
```bash
cd /home/x/defects4j_test/Chart_1
ls -la build/classes  # Chart 项目
ls -la target/classes # Lang/Math 项目
```

### Q: 如何重新编译项目？
```bash
cd /home/x/defects4j_test/Chart_1
defects4j compile
```

### Q: 脚本仍然报错怎么办？
检查脚本输出的详细错误信息，确保：
1. 项目已正确检出
2. 项目已成功编译
3. build/classes 和 build/tests 目录存在

## 总结

现在 `test_arja_optimized.sh` 已经更新，可以自动处理：
- ✅ Chart 项目（Ant + build/ 目录）
- ✅ Lang 项目（Maven + target/ 目录）
- ✅ 其他 Defects4J 项目

只需确保项目已编译，然后运行脚本即可！