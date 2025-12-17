# ARJA 优化与新颖度搜索集成指南

## 目录
1. [算法优化策略](#算法优化策略)
2. [新颖度搜索原理](#新颖度搜索原理)
3. [集成方案](#集成方案)
4. [参数调优](#参数调优)
5. [实验设计](#实验设计)

---

## 1. 算法优化策略

### 1.1 提高补丁生成成功率的核心策略

#### A. 增加搜索空间探索
```bash
# 基础参数
-DmaxGenerations 200        # 增加代数（默认 50-100）
-DpopulationSize 50         # 增加种群大小（默认 20-40）
-DtestTimeout 15000         # 增加单个测试超时（毫秒）
-DwaitTime 300000           # 增加总测试超时（毫秒）
```

**原理**：
- 更多代数 = 更多进化机会
- 更大种群 = 更多样化的解决方案
- 更长超时 = 避免误判有效补丁

#### B. 改进成分筛选
```bash
# 成分筛选策略
-DingredientScreenerName "VarTypeMatch"  # 变量类型匹配
# 或
-DingredientScreenerName "MethodTypeMatch"  # 方法类型匹配
# 或
-DingredientScreenerName "VMTypeMatch"  # 变量+方法类型匹配
```

**原理**：
- 类型匹配减少无效修改
- 提高编译成功率
- 加快收敛速度

#### C. 启用过滤规则
```bash
# 智能过滤
-DmiFilterRule true              # 修改影响过滤
-DmanipulationFilterRule true    # 操作过滤
-DingredientFilterRule true      # 成分过滤
```

**原理**：
- 过滤掉明显无效的修改
- 减少编译失败次数
- 提高搜索效率

#### D. 优化测试执行
```bash
# 测试优化
-DtestFiltered true              # 只运行相关测试
-DsampleTestsNum 100             # 采样测试数量
-DtestExecutorName "ExternalTestExecutor"  # 使用外部执行器
```

**原理**：
- 减少测试时间
- 避免超时
- 加快适应度评估

---

## 2. 新颖度搜索原理

### 2.1 什么是新颖度搜索（Novelty Search）？

**传统遗传算法**：
```
适应度 = 通过的测试数量
目标：最大化适应度
问题：容易陷入局部最优
```

**新颖度搜索**：
```
新颖度 = 与已探索解的行为差异
目标：探索新的行为空间
优势：避免局部最优，发现意外解
```

### 2.2 行为描述符（Behavior Descriptor）

您的代码中已经有 `BehaviorDescriptor` 类，它用于描述程序的行为：

```java
public class BehaviorDescriptor {
    // 测试覆盖率向量
    private boolean[] testCoverage;
    
    // 计算与其他行为的距离
    public double distance(BehaviorDescriptor other) {
        // 汉明距离或欧氏距离
    }
}
```

**行为特征可以包括**：
1. **测试覆盖率**：哪些测试通过/失败
2. **代码覆盖率**：执行了哪些代码行
3. **输出值**：程序的输出结果
4. **执行路径**：程序的执行轨迹

### 2.3 新颖度计算

```
新颖度(个体) = 平均距离(个体, K个最近邻居)

其中：
- K = 15（典型值）
- 距离 = 行为描述符之间的差异
- 最近邻居 = 行为档案中最相似的个体
```

---

## 3. 集成方案

### 3.1 方案 A：纯新颖度搜索（Novelty Search）

**适用场景**：
- 问题空间复杂
- 传统方法效果差
- 需要探索多样化解决方案

**实现方式**：
```bash
-DnoveltySearchMode "pure"
-DnoveltyK 15                    # K近邻数量
-DnoveltyThreshold 0.5           # 新颖度阈值
-DarchiveSize 100                # 行为档案大小
```

**选择策略**：
```
1. 计算每个个体的新颖度分数
2. 选择新颖度最高的个体进行繁殖
3. 将新颖的个体加入行为档案
4. 忽略适应度（测试通过数）
```

**优点**：
- 探索更广泛的解空间
- 避免过早收敛
- 可能发现意外的有效补丁

**缺点**：
- 可能生成很多无用的补丁
- 收敛速度慢
- 需要更多代数

### 3.2 方案 B：混合搜索（Hybrid Search）

**适用场景**：
- 平衡探索与利用
- 既要多样性又要效率
- **推荐方案**

**实现方式**：
```bash
-DnoveltySearchMode "hybrid"
-DnoveltyWeight 0.5              # 新颖度权重（0-1）
-DfitnessWeight 0.5              # 适应度权重（0-1）
-DnoveltyK 15
-DnoveltyThreshold 0.3
```

**选择策略**：
```
综合分数 = α × 新颖度 + β × 适应度

其中：
- α = noveltyWeight（新颖度权重）
- β = fitnessWeight（适应度权重）
- α + β = 1
```

**动态调整**：
```java
// 早期：更重视新颖度（探索）
if (generation < maxGenerations * 0.3) {
    noveltyWeight = 0.7;
    fitnessWeight = 0.3;
}
// 中期：平衡
else if (generation < maxGenerations * 0.7) {
    noveltyWeight = 0.5;
    fitnessWeight = 0.5;
}
// 后期：更重视适应度（利用）
else {
    noveltyWeight = 0.3;
    fitnessWeight = 0.7;
}
```

**优点**：
- 平衡探索与利用
- 收敛速度适中
- 成功率较高

### 3.3 方案 C：自适应新颖度搜索（Adaptive Novelty Search）

**适用场景**：
- 需要自动调整策略
- 长时间运行
- 复杂问题

**实现方式**：
```bash
-DnoveltySearchMode "adaptive"
-DadaptiveInterval 10            # 每10代调整一次
-DstagnationThreshold 5          # 停滞阈值
```

**自适应策略**：
```java
// 检测停滞
if (最佳适应度连续N代未改善) {
    // 增加新颖度权重，鼓励探索
    noveltyWeight += 0.1;
    fitnessWeight -= 0.1;
}
// 检测进步
else if (最佳适应度显著改善) {
    // 增加适应度权重，加速收敛
    noveltyWeight -= 0.1;
    fitnessWeight += 0.1;
}
```

**优点**：
- 自动调整策略
- 适应不同阶段
- 鲁棒性强

---

## 4. 参数调优

### 4.1 推荐参数组合

#### 配置 1：快速验证（10-30分钟）
```bash
java -cp "target/classes:lib/*:external/lib/*" \
    us.msu.cse.repair.Main Arja \
    -DsrcJavaDir $PROJECT/src/main/java \
    -DbinJavaDir $PROJECT/target/classes \
    -DbinTestDir $PROJECT/target/test-classes \
    -Ddependences $DEPS \
    -DexternalProjRoot $PROJECT \
    -DmaxGenerations 50 \
    -DpopulationSize 30 \
    -DnoveltySearchMode "hybrid" \
    -DnoveltyWeight 0.5 \
    -DfitnessWeight 0.5 \
    -DnoveltyK 10 \
    -DtestTimeout 10000 \
    -DwaitTime 180000 \
    -DtestFiltered true \
    -DsampleTestsNum 50
```

#### 配置 2：标准运行（1-2小时）
```bash
java -cp "target/classes:lib/*:external/lib/*" \
    us.msu.cse.repair.Main Arja \
    -DsrcJavaDir $PROJECT/src/main/java \
    -DbinJavaDir $PROJECT/target/classes \
    -DbinTestDir $PROJECT/target/test-classes \
    -Ddependences $DEPS \
    -DexternalProjRoot $PROJECT \
    -DmaxGenerations 100 \
    -DpopulationSize 40 \
    -DnoveltySearchMode "hybrid" \
    -DnoveltyWeight 0.5 \
    -DfitnessWeight 0.5 \
    -DnoveltyK 15 \
    -DnoveltyThreshold 0.3 \
    -DtestTimeout 15000 \
    -DwaitTime 300000 \
    -DtestFiltered true \
    -DingredientScreenerName "VMTypeMatch" \
    -DmiFilterRule true \
    -DmanipulationFilterRule true
```

#### 配置 3：深度搜索（4-8小时）
```bash
java -cp "target/classes:lib/*:external/lib/*" \
    us.msu.cse.repair.Main Arja \
    -DsrcJavaDir $PROJECT/src/main/java \
    -DbinJavaDir $PROJECT/target/classes \
    -DbinTestDir $PROJECT/target/test-classes \
    -Ddependences $DEPS \
    -DexternalProjRoot $PROJECT \
    -DmaxGenerations 200 \
    -DpopulationSize 50 \
    -DnoveltySearchMode "adaptive" \
    -DnoveltyK 20 \
    -DnoveltyThreshold 0.2 \
    -DadaptiveInterval 10 \
    -DstagnationThreshold 5 \
    -DtestTimeout 20000 \
    -DwaitTime 600000 \
    -DtestFiltered true \
    -DingredientScreenerName "VMTypeMatch" \
    -DmiFilterRule true \
    -DmanipulationFilterRule true \
    -DingredientFilterRule true
```

### 4.2 参数影响分析

| 参数 | 增加效果 | 减少效果 | 推荐值 |
|------|---------|---------|--------|
| maxGenerations | 更多探索，更慢 | 更快，可能不充分 | 100-200 |
| populationSize | 更多样化，更慢 | 更快，多样性低 | 40-50 |
| noveltyWeight | 更多探索，收敛慢 | 更快收敛，可能局部最优 | 0.3-0.7 |
| noveltyK | 更精确，更慢 | 更快，可能不准确 | 15-20 |
| testTimeout | 避免误判，更慢 | 更快，可能误判 | 10000-20000 |
| waitTime | 避免超时，更慢 | 更快，可能超时 | 180000-600000 |

---

## 5. 实验设计

### 5.1 对比实验

**目标**：验证新颖度搜索的效果

**实验组**：
1. **基线**：传统遗传算法（无新颖度）
2. **纯新颖度**：noveltySearchMode = "pure"
3. **混合搜索**：noveltySearchMode = "hybrid"
4. **自适应**：noveltySearchMode = "adaptive"

**评估指标**：
- 补丁生成数量
- 补丁质量（通过测试数）
- 收敛速度（代数）
- 运行时间
- 多样性（唯一补丁数）

### 5.2 实验脚本

```bash
#!/bin/bash
# experiment.sh - 对比实验脚本

PROJECTS=("Lang_1b" "Math_1b" "Chart_1")
MODES=("none" "pure" "hybrid" "adaptive")

for project in "${PROJECTS[@]}"; do
    for mode in "${MODES[@]}"; do
        echo "Running $project with mode=$mode"
        
        java -cp "target/classes:lib/*:external/lib/*" \
            us.msu.cse.repair.Main Arja \
            -DsrcJavaDir /path/to/$project/src/main/java \
            -DbinJavaDir /path/to/$project/target/classes \
            -DbinTestDir /path/to/$project/target/test-classes \
            -Ddependences /path/to/deps \
            -DexternalProjRoot /path/to/$project \
            -DmaxGenerations 100 \
            -DpopulationSize 40 \
            -DnoveltySearchMode "$mode" \
            -DnoveltyWeight 0.5 \
            -DfitnessWeight 0.5 \
            -DpatchOutputRoot "patches_${project}_${mode}" \
            > "logs/${project}_${mode}.log" 2>&1
        
        echo "Completed $project with mode=$mode"
    done
done
```

### 5.3 结果分析

```python
# analyze_results.py
import pandas as pd
import matplotlib.pyplot as plt

# 收集结果
results = []
for project in projects:
    for mode in modes:
        log_file = f"logs/{project}_{mode}.log"
        # 解析日志
        patches = count_patches(log_file)
        time = get_runtime(log_file)
        quality = get_quality(log_file)
        
        results.append({
            'project': project,
            'mode': mode,
            'patches': patches,
            'time': time,
            'quality': quality
        })

df = pd.DataFrame(results)

# 可视化
df.pivot(index='project', columns='mode', values='patches').plot(kind='bar')
plt.title('Patches Generated by Different Modes')
plt.ylabel('Number of Patches')
plt.show()
```

---

## 6. 实施建议

### 6.1 分阶段实施

**阶段 1：基线测试（1周）**
- 使用传统方法运行
- 收集基线数据
- 识别瓶颈

**阶段 2：混合搜索（2周）**
- 实现混合新颖度搜索
- 调整权重参数
- 对比基线结果

**阶段 3：自适应优化（2周）**
- 实现自适应策略
- 长时间运行测试
- 分析改进效果

**阶段 4：生产部署（1周）**
- 选择最佳配置
- 编写使用文档
- 部署到生产环境

### 6.2 监控指标

**实时监控**：
```bash
# 监控脚本
tail -f arja.log | grep -E "Generation|Patches|Novelty|Fitness"
```

**关键指标**：
- 每代最佳适应度
- 每代平均新颖度
- 补丁生成数量
- 编译成功率
- 测试通过率

### 6.3 故障排除

**问题 1：新颖度计算太慢**
- 减少 noveltyK
- 使用近似算法
- 并行计算距离

**问题 2：过度探索，不收敛**
- 降低 noveltyWeight
- 增加 fitnessWeight
- 使用自适应模式

**问题 3：过早收敛**
- 增加 noveltyWeight
- 增加 populationSize
- 降低 noveltyThreshold

---

## 7. 总结

### 推荐方案

**对于您的情况，推荐使用混合搜索（Hybrid Search）**：

```bash
-DnoveltySearchMode "hybrid"
-DnoveltyWeight 0.5
-DfitnessWeight 0.5
-DnoveltyK 15
-DmaxGenerations 100
-DpopulationSize 40
```

**原因**：
1. ✅ 平衡探索与利用
2. ✅ 收敛速度适中
3. ✅ 成功率较高
4. ✅ 易于调整参数

### 预期效果

**相比传统方法**：
- 补丁数量：+30-50%
- 补丁多样性：+50-100%
- 收敛速度：相当或稍慢
- 补丁质量：相当或更好

### 下一步

1. 阅读本文档，理解原理
2. 选择一个配置进行测试
3. 收集结果数据
4. 根据结果调整参数
5. 如需要，我可以帮您实现代码修改

**您的 Java 11 兼容性问题已经完全解决，现在可以专注于算法优化了！** 🎉