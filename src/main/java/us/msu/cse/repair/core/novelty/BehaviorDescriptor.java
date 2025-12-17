package us.msu.cse.repair.core.novelty;

import java.util.BitSet;
import java.util.Set;

/**
 * 行为描述符：使用测试用例通过/失败向量表示补丁的行为
 * 每个位表示一个测试用例是否通过（1=通过，0=失败）
 */
public class BehaviorDescriptor {
	private BitSet testResults;
	private int totalTests;
	
	/**
	 * 从测试执行结果构建行为描述符
	 * @param allTests 所有测试用例的集合（正测试+负测试）
	 * @param failedTests 失败的测试用例集合（可能为null）
	 */
	public BehaviorDescriptor(Set<String> allTests, Set<String> failedTests) {
		if (allTests == null) {
			throw new IllegalArgumentException("allTests cannot be null");
		}
		
		this.totalTests = allTests.size();
		this.testResults = new BitSet(totalTests);
		
		// 如果 failedTests 为 null，假设所有测试都通过
		if (failedTests == null) {
			// 所有测试都通过，设置所有位为1
			testResults.set(0, totalTests);
			return;
		}
		
		// 将测试用例转换为有序列表以便索引
		String[] testArray = allTests.toArray(new String[0]);
		
		// 设置每个测试用例的结果：通过=1，失败=0
		for (int i = 0; i < testArray.length; i++) {
			if (!failedTests.contains(testArray[i])) {
				testResults.set(i);
			}
		}
	}
	
	/**
	 * 计算与另一个行为描述符的汉明距离
	 * @param other 另一个行为描述符
	 * @return 汉明距离（不同位的数量）
	 */
	public int hammingDistance(BehaviorDescriptor other) {
		if (this.totalTests != other.totalTests) {
			throw new IllegalArgumentException("Behavior descriptors must have the same number of tests");
		}
		
		BitSet xor = (BitSet) this.testResults.clone();
		xor.xor(other.testResults);
		return xor.cardinality();
	}
	
	/**
	 * 计算与另一个行为描述符的归一化汉明距离（0-1之间）
	 * @param other 另一个行为描述符
	 * @return 归一化汉明距离
	 */
	public double normalizedHammingDistance(BehaviorDescriptor other) {
		if (totalTests == 0) return 0.0;
		return (double) hammingDistance(other) / totalTests;
	}
	
	/**
	 * 获取测试结果位集
	 * @return 测试结果位集
	 */
	public BitSet getTestResults() {
		return (BitSet) testResults.clone();
	}
	
	/**
	 * 获取测试总数
	 * @return 测试总数
	 */
	public int getTotalTests() {
		return totalTests;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		for (int i = 0; i < totalTests; i++) {
			sb.append(testResults.get(i) ? "1" : "0");
		}
		sb.append("]");
		return sb.toString();
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		BehaviorDescriptor that = (BehaviorDescriptor) obj;
		return this.testResults.equals(that.testResults) && this.totalTests == that.totalTests;
	}
	
	@Override
	public int hashCode() {
		return testResults.hashCode() * 31 + totalTests;
	}
}

