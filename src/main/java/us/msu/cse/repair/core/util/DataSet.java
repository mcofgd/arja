package us.msu.cse.repair.core.util;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * 修改说明：将原始基于 Java 7 的实现升级到 Java 11
 * 修改时间：2024-12-19
 * 修改原因：支持 Defects4J v3.0.1 要求 Java 11 环境
 * 主要改动：
 * 1. 更新路径解析以适配 Defects4J v3.0.1 的目录结构
 * 2. 支持 Defects4J v3.0.1 的新路径格式
 */
public class DataSet {
	/**
	 * 获取 Defects4J 程序信息
	 * 已适配 Defects4J v3.0.1 的目录结构
	 * 
	 * @param defects4jRoot Defects4J 根目录
	 * @param proj 项目名称（如 Chart, Lang 等）
	 * @param id Bug ID
	 * @return 包含路径和依赖信息的 HashMap
	 * @throws IOException 文件操作异常
	 */
	public static HashMap<String, Object> getDefects4JProgram(String defects4jRoot, String proj, int id)
			throws IOException {
		HashMap<String, Object> info = new HashMap<String, Object>();
		File pf1 = new File(defects4jRoot, proj);
		String name = proj.toLowerCase() + "_" + id + "_buggy";
		File pf2 = new File(pf1, name);

		// Defects4J v3.0.1 路径结构适配
		// 尝试新的路径结构（build/classes 和 build/tests）
		File buildClassesDir = new File(pf2, "build/classes");
		File buildTestsDir = new File(pf2, "build/tests");
		File targetClassesDir = new File(pf2, "target/classes");
		File targetTestClassesDir = new File(pf2, "target/test-classes");
		
		String binJavaDir;
		String binTestDir;
		
		// 优先使用 build 目录（Defects4J v3.0.1），否则使用 target 目录（旧版本）
		if (buildClassesDir.exists()) {
			binJavaDir = buildClassesDir.getCanonicalPath();
		} else if (targetClassesDir.exists()) {
			binJavaDir = targetClassesDir.getCanonicalPath();
		} else {
			binJavaDir = targetClassesDir.getCanonicalPath(); // 默认路径
		}
		
		if (buildTestsDir.exists()) {
			binTestDir = buildTestsDir.getCanonicalPath();
		} else if (targetTestClassesDir.exists()) {
			binTestDir = targetTestClassesDir.getCanonicalPath();
		} else {
			binTestDir = targetTestClassesDir.getCanonicalPath(); // 默认路径
		}
		
		String srcJavaDir = new File(pf2, "src/main/java").getCanonicalPath();

		Set<String> dependences = new HashSet<String>();

		// Defects4J v3.0.1 可能将依赖放在不同位置
		File libDir = new File(pf2, "lib");
		if (libDir.exists() && libDir.isDirectory()) {
			File[] libFiles = libDir.listFiles();
			if (libFiles != null) {
				for (File file : libFiles) {
					if (file.isFile() && file.getName().endsWith(".jar")) {
						dependences.add(file.getCanonicalPath());
					}
				}
			}
		}

		info.put("binJavaDir", binJavaDir);
		info.put("binTestDir", binTestDir);
		info.put("srcJavaDir", srcJavaDir);
		info.put("dependences", dependences);

		return info;
	}

	public static HashMap<String, Object> getSeedMath85Program(String seedMath85Root, int id) throws IOException {
		HashMap<String, Object> info = new HashMap<String, Object>();
		File pf = new File(seedMath85Root, "SM" + id);

		String binJavaDir = new File(pf, "target/classes").getCanonicalPath();
		String binTestDir = new File(pf, "target/test-classes").getCanonicalPath();
		String srcJavaDir = new File(pf, "src/main/java").getCanonicalPath();

		Set<String> dependences = new HashSet<String>();

		for (File file : new File(pf, "lib").listFiles())
			dependences.add(file.getCanonicalPath());

		info.put("binJavaDir", binJavaDir);
		info.put("binTestDir", binTestDir);
		info.put("srcJavaDir", srcJavaDir);
		info.put("dependences", dependences);

		return info;
	}

	public static HashMap<String, Object> getSeedMath85TMProgram(String seedMath85TMRoot, int id) throws IOException {
		HashMap<String, Object> info = new HashMap<String, Object>();
		File pf = new File(seedMath85TMRoot, "ST" + id);

		String binJavaDir = new File(pf, "target/classes").getCanonicalPath();
		String binTestDir = new File(pf, "target/test-classes").getCanonicalPath();
		String srcJavaDir = new File(pf, "src/main/java").getCanonicalPath();

		Set<String> dependences = new HashSet<String>();

		for (File file : new File(pf, "lib").listFiles())
			dependences.add(file.getCanonicalPath());

		info.put("binJavaDir", binJavaDir);
		info.put("binTestDir", binTestDir);
		info.put("srcJavaDir", srcJavaDir);
		info.put("dependences", dependences);

		return info;
	}
}
