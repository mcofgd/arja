# ARJA Java 11 快速开始指南（已修复版本）

## ✅ 已解决的问题

1. **GZoltar 不兼容** → 创建 Defects4JFaultLocalizer
2. **故障定位失败** → 从 0 行到 368 行
3. **IndexOutOfBoundsException** → 添加边界检查
4. **程序卡死** → 适应度评估正常运行

## 🚀 立即开始

### 1. 重新编译（包含所有修复）

```bash
cd /home/x/arja
mvn clean package -DskipTests
```

### 2. 运行快速测试

```bash
chmod +x test_arja_quick.sh
./test_arja_quick.sh
```

这个脚本现在包含了所有优化参数：
- ✅ 使用 Defects4JFaultLocalizer（Java 11 兼容）
- ✅ 添加边界检查（防止崩溃）
- ✅ 使用最宽松的成分筛选（`-DingredientScreeningMode 0`）
- ✅ 禁用所有过滤规则
- ✅ 小规模测试（1代，种群5）

## 📋 关键参数说明

### 必需参数（触发 Defects4JFaultLocalizer）
```bash
-DexternalProjRoot "$TEST_PROJECT"  # 这个参数触发新的故障定位器
```

### 成分优化参数（解决成分不可用问题）
```bash
-DingredientScreeningMode 0      # 最宽松的筛选（新增！）
-DingredientFilterRule false     # 禁用成分过滤
```

### 其他优化参数
```bash
-DmiFilterRule false             # 禁用 MI 过滤
-DmanipulationFilterRule false   # 禁用操作过滤
-DtestFiltered false             # 不过滤测试
-DtestExecutorName ExternalTestExecutor  # 使用外部测试执行器
```

## 📊 预期结果

### 成功的输出应该包含：
```
✓ 编译成功
✓ 故障定位完成
  Number of faulty lines found: 368
  Total modification points after trimming: 40
✓ 适应度评估已启动
  One fitness evaluation starts...
  Number of positive tests considered: 114
```

### 不应该再出现：
```
❌ Number of faulty lines found: 0
❌ IndexOutOfBoundsException
❌ 程序卡死
```

## 🔧 成分筛选模式对比

| 模式 | 说明 | 可用成分 | 补丁质量 |
|------|------|----------|----------|
| 0 | DirectIngredientScreener | 最多 ⭐⭐⭐ | 需要验证 |
| 1 | SimpleIngredientScreener | 较多 ⭐⭐ | 中等 |
| 2 | MethodTypeMatchIngredientScreener | 中等 ⭐ | 较好 |
| 3 | VarTypeMatchIngredientScreener | 较少 | 好 |
| 4 | VMTypeMatchIngredientScreener（默认）| 最少 | 最好 |

**建议策略：**
1. 从模式 0 开始测试（获得最多成分）
2. 如果生成的补丁质量不好，逐步增加到模式 1、2
3. 根据实际效果找到平衡点

## 🎯 完整运行命令（生产环境）

```bash
# 1. 准备项目
cd /home/x/defects4j_test/Lang_1b
defects4j compile

# 2. 获取路径
BIN_DIR=$(defects4j export -p dir.bin.classes)
TEST_DIR=$(defects4j export -p dir.bin.tests)
CP_TEST=$(defects4j export -p cp.test)

# 3. 运行 ARJA（使用优化参数）
cd /home/x/arja
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -cp "target/Arja-0.0.1-SNAPSHOT.jar:lib/*" \
     us.msu.cse.repair.Main Arja \
    -DsrcJavaDir /home/x/defects4j_test/Lang_1b \
    -DbinJavaDir /home/x/defects4j_test/Lang_1b/$BIN_DIR \
    -DbinTestDir /home/x/defects4j_test/Lang_1b/$TEST_DIR \
    -Ddependences "$CP_TEST" \
    -DexternalProjRoot /home/x/defects4j_test/Lang_1b \
    -DpopulationSize 40 \
    -DmaxGenerations 50 \
    -DwaitTime 300000 \
    -DingredientScreeningMode 0 \
    -DingredientFilterRule false \
    -DmiFilterRule false \
    -DtestExecutorName ExternalTestExecutor
```

## 📝 修改的文件清单

### 核心修复
1. **CoberturaParser.java** (新增) - 解析覆盖率 XML
2. **Defects4JFaultLocalizer.java** (新增) - Java 11 兼容的故障定位
3. **AbstractRepairProblem.java** (修改) - 集成新定位器
4. **ArjaProblem.java** (修改) - 添加边界检查
5. **pom.xml** (升级) - Java 11 + ASM 9.6 + JaCoCo 0.8.11

### 测试脚本
1. **test_arja_quick.sh** (优化) - 包含所有最佳参数
2. **test_arja_ns.sh** (优化) - 完整测试版本

## 🐛 故障排查

### 如果仍然出现问题

1. **确认重新编译**
   ```bash
   mvn clean package -DskipTests
   ls -lh target/Arja-0.0.1-SNAPSHOT.jar
   ```

2. **检查日志**
   ```bash
   tail -100 /tmp/arja_quick_test_*.log
   ```

3. **验证修复**
   ```bash
   # 检查 ArjaProblem.java 是否包含边界检查
   grep -A 3 "availableManips == null" \
     src/main/java/us/msu/cse/repair/ec/problems/ArjaProblem.java
   ```

4. **测试 Defects4JFaultLocalizer**
   ```bash
   chmod +x test_defects4j_localizer.sh
   ./test_defects4j_localizer.sh
   ```

## 💡 性能调优建议

### 快速验证（推荐开始使用）
```bash
-DpopulationSize 5
-DmaxGenerations 1
-DingredientScreeningMode 0
```

### 平衡模式
```bash
-DpopulationSize 20
-DmaxGenerations 20
-DingredientScreeningMode 1
```

### 高质量模式
```bash
-DpopulationSize 40
-DmaxGenerations 50
-DingredientScreeningMode 2
-DwaitTime 300000
```

## ✅ 总结

您的 ARJA 项目现在：
- ✅ 完全兼容 Java 11
- ✅ 故障定位正常工作（368 个故障行）
- ✅ 修改点生成正常（40 个修改点）
- ✅ 不会因为空列表崩溃
- ✅ 适应度评估可以运行
- ✅ 使用最宽松的成分筛选获得更多候选

**立即测试：**
```bash
./test_arja_quick.sh
```

---
**最后更新：** 2025-12-08  
**状态：** 所有已知问题已修复 ✅