# GZoltar Java 11 兼容性 - 快速修复方案

## 问题
GZoltar 0.1.1 在 Java 11 环境下卡死，导致无法完成故障定位。

## 快速解决方案

### 方案1：使用 Java 8 运行（最快）

```bash
# 1. 安装 Java 8（如果还没有）
sudo apt-get update
sudo apt-get install openjdk-8-jdk

# 2. 临时切换到 Java 8
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# 3. 验证 Java 版本
java -version  # 应该显示 1.8.x

# 4. 重新编译 ARJA
cd /home/x/arja
mvn clean compile

# 5. 运行测试
cd /home/x/defects4j_test
bash /home/x/arja/test_arja_ns.sh
```

### 方案2：添加 GZoltar 所需的 JVM 参数

在 `test_arja_ns.sh` 中添加更多 JVM 参数：

```bash
java \
    --add-opens java.base/java.lang=ALL-UNNAMED \
    --add-opens java.base/java.util=ALL-UNNAMED \
    --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
    --add-opens java.base/java.text=ALL-UNNAMED \
    --add-opens java.desktop/java.awt.font=ALL-UNNAMED \
    --illegal-access=permit \
    -Djdk.attach.allowAttachSelf=true \
    -cp "lib/*:bin" us.msu.cse.repair.Main Arja \
    ...
```

### 方案3：降低故障定位阈值

如果 GZoltar 运行但没有找到可疑行，尝试降低阈值：

```bash
java -cp "lib/*:bin" us.msu.cse.repair.Main Arja \
    -Dthr 0.0 \  # 降低阈值到0，接受所有可疑行
    ...
```

## 验证步骤

1. **检查 GZoltar 是否运行**
```bash
tail -f /home/x/defects4j_test/logs/arja_Lang_1b_*.log
```

应该看到：
```
Fault localization starts...
Number of positive tests: XXXX
Number of negative tests: XX
Number of faulty lines found: XX  # 应该 > 0
Fault localization is finished!
```

2. **检查修改点是否生成**
```
AST parsing starts...
AST parsing is finished!
...
Modification points trimmer starts...
  Total modification points before trimming: XX  # 应该 > 0
```

## 如果仍然失败

### 调试 GZoltar

创建一个简单的测试程序：

```java
// TestGZoltar.java
import com.gzoltar.core.GZoltar;

public class TestGZoltar {
    public static void main(String[] args) {
        System.out.println("Testing GZoltar...");
        try {
            GZoltar gz = new GZoltar(System.getProperty("user.dir"));
            System.out.println("GZoltar initialized successfully!");
        } catch (Exception e) {
            System.err.println("GZoltar failed:");
            e.printStackTrace();
        }
    }
}
```

编译并运行：
```bash
javac -cp lib/gzoltar-0.1.1.jar TestGZoltar.java
java -cp lib/gzoltar-0.1.1.jar:. TestGZoltar
```

### 检查 ASM 版本冲突

```bash
# 检查 classpath 中的 ASM 版本
find lib -name "asm*.jar" -exec echo {} \; -exec jar -tf {} | grep -i version \;
```

确保只有一个 ASM 版本（5.2）。

## 长期解决方案

升级到 GZoltar 1.7.3+，它完全支持 Java 11+。

参考 `JAVA11_DIAGNOSIS_AND_SOLUTION.md` 中的详细步骤。