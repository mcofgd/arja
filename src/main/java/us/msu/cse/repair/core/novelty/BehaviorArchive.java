package us.msu.cse.repair.core.novelty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 行为档案：存储已探索的行为描述符，用于计算 Novelty Score
 */
public class BehaviorArchive {
	private List<BehaviorDescriptor> archive;
	private int maxSize;
	
	/**
	 * 构造函数
	 * @param maxSize 档案最大容量
	 */
	public BehaviorArchive(int maxSize) {
		this.maxSize = maxSize;
		this.archive = new ArrayList<BehaviorDescriptor>();
	}
	
	/**
	 * 添加行为描述符到档案
	 * @param descriptor 行为描述符
	 */
	public void add(BehaviorDescriptor descriptor) {
		// 如果档案未满，直接添加
		if (archive.size() < maxSize) {
			archive.add(descriptor);
		} else {
			// 档案已满，随机替换一个旧条目
			int randomIndex = (int) (Math.random() * archive.size());
			archive.set(randomIndex, descriptor);
		}
	}
	
	/**
	 * 计算 Novelty Score：使用 k-近邻方法
	 * @param descriptor 待评估的行为描述符
	 * @param k k-近邻的 k 值
	 * @return Novelty Score（平均距离）
	 */
	public double computeNoveltyScore(BehaviorDescriptor descriptor, int k) {
		if (archive.isEmpty()) {
			// 档案为空，返回一个较大的默认值
			return 1.0;
		}
		
		// 计算与档案中所有描述符的距离
		List<Double> distances = new ArrayList<Double>();
		for (BehaviorDescriptor archived : archive) {
			double distance = descriptor.normalizedHammingDistance(archived);
			distances.add(distance);
		}
		
		// 排序并取前 k 个最小距离
		Collections.sort(distances);
		int actualK = Math.min(k, distances.size());
		
		// 计算平均距离作为 Novelty Score
		double sum = 0.0;
		for (int i = 0; i < actualK; i++) {
			sum += distances.get(i);
		}
		
		return sum / actualK;
	}
	
	/**
	 * 计算与当前种群中其他个体的平均距离（用于种群内多样性）
	 * @param descriptor 待评估的行为描述符
	 * @param populationDescriptors 种群中其他个体的行为描述符列表
	 * @param k k-近邻的 k 值
	 * @return 平均距离
	 */
	public double computePopulationNovelty(BehaviorDescriptor descriptor, 
			List<BehaviorDescriptor> populationDescriptors, int k) {
		if (populationDescriptors.isEmpty()) {
			return 1.0;
		}
		
		List<Double> distances = new ArrayList<Double>();
		for (BehaviorDescriptor other : populationDescriptors) {
			if (!other.equals(descriptor)) {
				double distance = descriptor.normalizedHammingDistance(other);
				distances.add(distance);
			}
		}
		
		if (distances.isEmpty()) {
			return 1.0;
		}
		
		Collections.sort(distances);
		int actualK = Math.min(k, distances.size());
		
		double sum = 0.0;
		for (int i = 0; i < actualK; i++) {
			sum += distances.get(i);
		}
		
		return sum / actualK;
	}
	
	/**
	 * 获取档案大小
	 * @return 档案大小
	 */
	public int size() {
		return archive.size();
	}
	
	/**
	 * 清空档案
	 */
	public void clear() {
		archive.clear();
	}
}

