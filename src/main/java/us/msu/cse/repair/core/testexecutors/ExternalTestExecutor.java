package us.msu.cse.repair.core.testexecutors;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import us.msu.cse.repair.core.util.ProcessWithTimeout;
import us.msu.cse.repair.core.util.StreamReaderThread;

/**
 * 修改说明：将原始基于 Java 7 的实现升级到 Java 11
 * 修改时间：2024-12-19
 * 修改原因：支持 Defects4J v3.0.1 要求 Java 11 环境
 * 主要改动：
 * 1. 已包含时区环境变量设置（TZ=America/Los_Angeles）
 * 2. 与 Defects4J v3.0.1 兼容
 */
public class ExternalTestExecutor implements ITestExecutor {
	String binJavaDir;
	String binTestDir;
	Set<String> dependences;

	String binWorkingDir;

	String externalProjRoot;

	Set<String> positiveTests;
	Set<String> negativeTests;
	String finalTestsInfoPath;

	String jvmPath;

	int waitTime;
	boolean isExceptional;

	int failuresInPositive;
	int failuresInNegative;

	Set<String> failedTests;

	final int MAX = 300;

	public ExternalTestExecutor(Set<String> positiveTests, Set<String> negativeTests, String finalTestsInfoPath,
			String binJavaDir, String binTestDir, Set<String> dependences, String binWorkingDir,
			String externalProjRoot, String jvmPath, int waitTime) {
		this.positiveTests = positiveTests;
		this.negativeTests = negativeTests;
		this.finalTestsInfoPath = finalTestsInfoPath;

		this.binJavaDir = binJavaDir;
		this.binTestDir = binTestDir;
		this.dependences = dependences;
		this.externalProjRoot = externalProjRoot;

		this.binWorkingDir = binWorkingDir;

		this.jvmPath = jvmPath;

		this.waitTime = waitTime;

		this.failuresInPositive = 0;
		this.failuresInNegative = 0;
		this.isExceptional = false;
	}

	@Override
	public boolean runTests() throws IOException, InterruptedException {
		// ✅ 关键修复：检查是否有测试可运行
		if (positiveTests.isEmpty() && negativeTests.isEmpty()) {
			System.err.println("⚠️  ExternalTestExecutor: No tests to run!");
			System.err.println("   This usually means test filtering failed.");
			System.err.println("   Marking as exceptional to avoid timeout.");
			isExceptional = true;
			return false;  // 立即返回，不启动进程
		}
		
		// TODO Auto-generated method stub
		List<String> params = new ArrayList<String>();
		params.add(jvmPath);
		params.add("-cp");

		String cpStr = "";
		cpStr += (binWorkingDir + File.pathSeparator);
		cpStr += (binJavaDir + File.pathSeparator);
		cpStr += (binTestDir + File.pathSeparator);
		cpStr += new File(externalProjRoot, "bin").getCanonicalPath();
		if (dependences != null) {
			for (String dp : dependences)
				cpStr += (File.pathSeparator + dp);
		}
		params.add(cpStr);

		params.add("us.msu.cse.repair.external.junit.JUnitTestRunner");

		if (finalTestsInfoPath != null && positiveTests.size() > MAX)
			params.add("@" + finalTestsInfoPath);
		else {
			String testStrs = "";
			for (String test : positiveTests)
				testStrs += (test + File.pathSeparator);
			for (String test : negativeTests)
				testStrs += (test + File.pathSeparator);
			params.add(testStrs);
		}

		ProcessBuilder builder = new ProcessBuilder(params);
		// ✅ 修复：移除错误的方法调用
		// builder.redirectOutput();  // ❌ 删除：无参数调用会导致编译错误
		builder.redirectErrorStream(true);  // ✅ 保留：将错误流合并到标准输出
		// builder.directory();  // ❌ 删除：无参数调用会导致编译错误
		
		// ✅ 设置时区环境变量
		builder.environment().put("TZ", "America/Los_Angeles");

		Process process = builder.start();

		StreamReaderThread streamReaderThread = new StreamReaderThread(process.getInputStream());
		streamReaderThread.start();

		ProcessWithTimeout processWithTimeout = new ProcessWithTimeout(process);
		int exitCode = processWithTimeout.waitForProcess(waitTime);

		streamReaderThread.join();

		if (exitCode != 0 || streamReaderThread.isStreamExceptional()) {
			isExceptional = true;
			System.err.println("ExternalTestExecutor failed with exit code: " + exitCode);
			System.err.println("Stream exceptional: " + streamReaderThread.isStreamExceptional());
			System.err.println("Process Output:");
			for (String line : streamReaderThread.getOutput()) {
				System.err.println("  " + line);
			}
			return false;
		}

		List<String> output = streamReaderThread.getOutput();

		failedTests = new HashSet<String>();
		for (String str : output) {
			if (str.startsWith("FailedTest"))
				failedTests.add(str.split(":")[1].trim());
		}

		for (String test : failedTests) {
			if (negativeTests.contains(test))
				failuresInNegative++;
			else
				failuresInPositive++;
		}
		return failedTests.isEmpty();
	}

	@Override
	public int getFailureCountInPositive() {
		// TODO Auto-generated method stub
		return this.failuresInPositive;
	}

	@Override
	public int getFailureCountInNegative() {
		// TODO Auto-generated method stub
		return this.failuresInNegative;
	}

	@Override
	public double getRatioOfFailuresInPositive() {
		// TODO Auto-generated method stub
		if (!positiveTests.isEmpty())
			return (double) failuresInPositive / positiveTests.size();
		else
			return 0;
	}

	@Override
	public double getRatioOfFailuresInNegative() {
		// TODO Auto-generated method stub
		if (!negativeTests.isEmpty())
			return (double) failuresInNegative / negativeTests.size();
		else
			return 0;
	}

	@Override
	public boolean isExceptional() {
		// TODO Auto-generated method stub
		return this.isExceptional;
	}

	@Override
	public Set<String> getFailedTests() {
		return this.failedTests;
	}

}
