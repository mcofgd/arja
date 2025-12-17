package us.msu.cse.repair.core.testexecutors;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;

import us.msu.cse.repair.core.util.CustomURLClassLoader;

/**
 * 修改说明：将原始基于 Java 7 的实现升级到 Java 11
 * 修改时间：2024-12-19
 * 修改原因：支持 Defects4J v3.0.1 要求 Java 11 环境
 * 主要改动：
 * 1. 替换已弃用的 Thread.stop() 方法为中断机制
 * 2. 添加时区设置以匹配 Defects4J v3.0.1 要求
 */
public class InternalTestExecutor implements ITestExecutor {
	Set<String> positiveTests;
	Set<String> negativeTests;

	CustomURLClassLoader urlClassLoader;

	int waitTime;
	boolean isTimeout;

	int failuresInPositive;
	int failuresInNegative;

	Set<String> failedTests;

	public InternalTestExecutor(Set<String> positiveTests, Set<String> negativeTests,
			CustomURLClassLoader urlClassLoader, int waitTime) throws MalformedURLException {
		// 设置时区以匹配 Defects4J v3.0.1 要求
		TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
		
		this.positiveTests = positiveTests;
		this.negativeTests = negativeTests;

		this.urlClassLoader = urlClassLoader;

		this.waitTime = waitTime;
		this.isTimeout = false;

		this.failuresInPositive = 0;
		this.failuresInNegative = 0;
	}

	@Override
	public boolean runTests() throws IOException {
		// TODO Auto-generated method stub
		failedTests = new HashSet<String>();

		TestRunThread thread = new TestRunThread();
		thread.start();
		try {
			thread.join(waitTime);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		boolean flag;

		if (thread.isAlive()) {
			isTimeout = true;
			// ✅ Java 11 兼容性修复：改进线程中断处理
			thread.interrupt();
			
			// 等待一小段时间让线程响应中断
			try {
				thread.join(1000);
			} catch (InterruptedException e) {
				// 忽略
			}
			
			// 如果线程仍然存活，记录警告
			if (thread.isAlive()) {
				System.err.println("Warning: Test thread did not respond to interrupt, may cause resource leak");
			}
			
			flag = false;
		} else
			flag = thread.isSuccess;

		urlClassLoader.close();
		return flag;
	}

	private class TestRunThread extends Thread {
		boolean isSuccess;

		@Override
		public void run() {
			try {
				boolean posSuccess = runPositiveTests();
				// ✅ 检查中断状态
				if (Thread.currentThread().isInterrupted()) {
					System.out.println("Test execution interrupted after positive tests");
					isSuccess = false;
					return;
				}
				boolean negSuccess = runNegativeTests();
				// ✅ 再次检查中断状态
				if (Thread.currentThread().isInterrupted()) {
					System.out.println("Test execution interrupted after negative tests");
					isSuccess = false;
					return;
				}
				isSuccess = posSuccess & negSuccess;
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				isSuccess = false;
			} catch (Exception e) {
				// ✅ 处理中断异常
				if (Thread.currentThread().isInterrupted() || e instanceof InterruptedException) {
					System.out.println("Test execution interrupted by exception");
					isSuccess = false;
				} else {
					e.printStackTrace();
					isSuccess = false;
				}
			}
		}
	}

	boolean runPositiveTests() throws ClassNotFoundException {
		for (String test : positiveTests) {
			// ✅ 在每个测试前检查中断状态
			if (Thread.currentThread().isInterrupted()) {
				System.out.println("Interrupted during positive test execution");
				return false;
			}
			
			String[] temp = test.split("#");

			Class<?> targetClass = urlClassLoader.loadClass(temp[0]);
			Request request = Request.method(targetClass, temp[1]);

			Result result = new JUnitCore().run(request);

			if (!result.wasSuccessful()) {
				failuresInPositive++;
				failedTests.add(test);
			}
		}

		return failuresInPositive == 0;
	}

	boolean runNegativeTests() throws ClassNotFoundException {
		for (String test : negativeTests) {
			// ✅ 在每个测试前检查中断状态
			if (Thread.currentThread().isInterrupted()) {
				System.out.println("Interrupted during negative test execution");
				return false;
			}
			
			String[] temp = test.split("#");
			Class<?> targetClass = urlClassLoader.loadClass(temp[0]);
			Request request = Request.method(targetClass, temp[1]);
			Result result = new JUnitCore().run(request);

			if (!result.wasSuccessful()) {
				failuresInNegative++;
				failedTests.add(test);
			}
		}
		return failuresInNegative == 0;
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
		if (positiveTests.size() != 0)
			return (double) failuresInPositive / positiveTests.size();
		else
			return 0;
	}

	@Override
	public double getRatioOfFailuresInNegative() {
		// TODO Auto-generated method stub
		if (negativeTests.size() != 0)
			return (double) failuresInNegative / negativeTests.size();
		else
			return 0;
	}

	@Override
	public boolean isExceptional() {
		// TODO Auto-generated method stub
		return this.isTimeout;
	}

	@Override
	public Set<String> getFailedTests() {
		// TODO Auto-generated method stub
		return this.failedTests;
	}

}
