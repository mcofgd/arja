package us.msu.cse.repair.ec.problems;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import javax.tools.JavaFileObject;

import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import jmetal.core.Solution;
import jmetal.encodings.variable.ArrayInt;
import jmetal.encodings.variable.Binary;
import jmetal.util.Configuration;
import jmetal.util.JMException;
import us.msu.cse.repair.core.AbstractRepairProblem;
import us.msu.cse.repair.core.filterrules.MIFilterRule;
import us.msu.cse.repair.core.novelty.BehaviorArchive;
import us.msu.cse.repair.core.novelty.BehaviorDescriptor;
import us.msu.cse.repair.core.parser.ModificationPoint;
import us.msu.cse.repair.core.testexecutors.ITestExecutor;
import us.msu.cse.repair.core.util.IO;
import us.msu.cse.repair.ec.representation.ArrayIntAndBinarySolutionType;

public class ArjaProblem extends AbstractRepairProblem {
	private static final long serialVersionUID = 1L;
	Double weight;

	Integer numberOfObjectives;
	Integer maxNumberOfEdits;
	Double mu;

	String initializationStrategy;

	Boolean miFilterRule;
	
	// Novelty Search 相关参数
	String noveltySearchMode;  // "none", "lightweight", "full"
	Integer noveltyKNeighbors;  // k-近邻的 k 值
	Integer noveltyArchiveSize;  // 行为档案大小
	Double noveltyDiversityWeight;  // 多样性权重（用于 lightweight 模式）
	
	// Novelty Search 相关对象
	BehaviorArchive behaviorArchive;
	Set<String> allTests;  // 所有测试用例（正测试+负测试）

	public ArjaProblem(Map<String, Object> parameters) throws Exception {
		super(parameters);

		weight = (Double) parameters.get("weight");
		if (weight == null)
			weight = 0.5;

		mu = (Double) parameters.get("mu");
		if (mu == null)
			mu = 0.06;

		numberOfObjectives = (Integer) parameters.get("numberOfObjectives");
		if (numberOfObjectives == null)
			numberOfObjectives = 2;

		initializationStrategy = (String) parameters.get("initializationStrategy");
		if (initializationStrategy == null)
			initializationStrategy = "Prior";

		miFilterRule = (Boolean) parameters.get("miFilterRule");
		if (miFilterRule == null)
			miFilterRule = true;

		maxNumberOfEdits = (Integer) parameters.get("maxNumberOfEdits");
		
		// 初始化 Novelty Search 参数
		noveltySearchMode = (String) parameters.get("noveltySearchMode");
		if (noveltySearchMode == null)
			noveltySearchMode = "none";
		
		noveltyKNeighbors = (Integer) parameters.get("noveltyKNeighbors");
		if (noveltyKNeighbors == null)
			noveltyKNeighbors = 15;
		
		noveltyArchiveSize = (Integer) parameters.get("noveltyArchiveSize");
		if (noveltyArchiveSize == null)
			noveltyArchiveSize = 200;
		
		noveltyDiversityWeight = (Double) parameters.get("noveltyDiversityWeight");
		if (noveltyDiversityWeight == null)
			noveltyDiversityWeight = 0.3;

		setProblemParams();
		
		// 初始化行为档案（如果启用 Novelty Search）
		if (!noveltySearchMode.equalsIgnoreCase("none")) {
			behaviorArchive = new BehaviorArchive(noveltyArchiveSize);
			// 合并所有测试用例
			allTests = new java.util.HashSet<String>();
			allTests.addAll(positiveTests);
			allTests.addAll(negativeTests);
		}
	}

	void setProblemParams() throws JMException {
		numberOfVariables_ = 2;
		numberOfObjectives_ = numberOfObjectives;
		numberOfConstraints_ = 0;
		problemName_ = "ArjaProblem";

		int size = modificationPoints.size();

		double[] prob = new double[size];
		if (initializationStrategy.equalsIgnoreCase("Prior")) {
			for (int i = 0; i < size; i++)
				prob[i] = modificationPoints.get(i).getSuspValue() * mu;
		} else if (initializationStrategy.equalsIgnoreCase("Random")) {
			for (int i = 0; i < size; i++)
				prob[i] = 0.5;
		} else {
			Configuration.logger_.severe("Initialization strategy " + initializationStrategy + " not found");
			throw new JMException("Exception in initialization strategy: " + initializationStrategy);
		}

		solutionType_ = new ArrayIntAndBinarySolutionType(this, size, prob);

		upperLimit_ = new double[2 * size];
		lowerLimit_ = new double[2 * size];
		for (int i = 0; i < size; i++) {
			lowerLimit_[i] = 0;
			upperLimit_[i] = availableManipulations.get(i).size() - 1;
		}

		for (int i = size; i < 2 * size; i++) {
			lowerLimit_[i] = 0;
			upperLimit_[i] = modificationPoints.get(i - size).getIngredients().size() - 1;
		}
	}

	@Override
	public void evaluate(Solution solution) throws JMException {
		// TODO Auto-generated method stub
		System.out.println("One fitness evaluation starts...");
		
		int[] array = ((ArrayInt) solution.getDecisionVariables()[0]).array_;
		BitSet bits = ((Binary) solution.getDecisionVariables()[1]).bits_;

		int size = modificationPoints.size();
		Map<String, ASTRewrite> astRewriters = new HashMap<String, ASTRewrite>();

		Map<Integer, Double> selectedMP = new HashMap<Integer, Double>();

		for (int i = 0; i < size; i++) {
			if (bits.get(i)) {
				double suspValue = modificationPoints.get(i).getSuspValue();
				
				// 检查是否有可用的操作
				List<String> availableManips = availableManipulations.get(i);
				if (availableManips == null || availableManips.isEmpty()) {
					// 没有可用操作，跳过此修改点
					bits.set(i, false);
					continue;
				}
				
				if (miFilterRule) {
					String manipName = availableManips.get(array[i]);
					ModificationPoint mp = modificationPoints.get(i);

					Statement seed = null;
					if (!mp.getIngredients().isEmpty())
						seed = mp.getIngredients().get(array[i + size]);
					
					int index = MIFilterRule.canFiltered(manipName, seed, modificationPoints.get(i));
					if (index == -1)
						selectedMP.put(i, suspValue);
					else if (index < mp.getIngredients().size()) {
						array[i + size] = index;
						selectedMP.put(i, suspValue);
					}
					else
						bits.set(i, false);
				} else
					selectedMP.put(i, suspValue);
			}
		}

		if (selectedMP.isEmpty()) {
			assignMaxObjectiveValues(solution);
			// ✅ 增强日志：输出为什么没有选中修改点
			System.out.println("No modification points selected, skipping evaluation");
			System.out.println("  Total modification points: " + size);
			System.out.println("  Bits set: " + bits.cardinality());
			System.out.println("  miFilterRule enabled: " + miFilterRule);
			if (miFilterRule) {
				System.out.println("  Suggestion: Try disabling miFilterRule (-DmiFilterRule false)");
			}
			return;
		}

		int numberOfEdits = selectedMP.size();
		List<Map.Entry<Integer, Double>> list = new ArrayList<Map.Entry<Integer, Double>>(selectedMP.entrySet());

		if (maxNumberOfEdits != null && selectedMP.size() > maxNumberOfEdits) {
			Collections.sort(list, new Comparator<Map.Entry<Integer, Double>>() {
				@Override
				public int compare(Entry<Integer, Double> o1, Entry<Integer, Double> o2) {
					return o2.getValue().compareTo(o1.getValue());
				}
			});

			numberOfEdits = maxNumberOfEdits;
		}

		for (int i = 0; i < numberOfEdits; i++)
			manipulateOneModificationPoint(list.get(i).getKey(), size, array, astRewriters);

		for (int i = numberOfEdits; i < selectedMP.size(); i++)
			bits.set(list.get(i).getKey(), false);

		Map<String, String> modifiedJavaSources = getModifiedJavaSources(astRewriters);
		System.out.println("Compiling modified sources...");
		Map<String, JavaFileObject> compiledClasses = getCompiledClassesForTestExecution(modifiedJavaSources);

		boolean status = false;
		if (compiledClasses != null) {
			System.out.println("Compilation successful, starting test execution...");
			// 设置编辑数量目标（如果不是 full NS 模式，或者目标数量>=2）
			if (!noveltySearchMode.equalsIgnoreCase("full") && (numberOfObjectives == 2 || numberOfObjectives == 3)) {
				solution.setObjective(0, numberOfEdits);
			} else if (noveltySearchMode.equalsIgnoreCase("full") && numberOfObjectives >= 2) {
				// full 模式下，目标0是编辑数量
				solution.setObjective(0, numberOfEdits);
			}
			try {
				System.out.println("Invoking test executor...");
				status = invokeTestExecutor(compiledClasses, solution);
				System.out.println("Test execution completed, status: " + status);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				System.err.println("Exception during test execution: " + e.getMessage());
				e.printStackTrace();
			}
		} else {
			assignMaxObjectiveValues(solution);
			System.out.println("Compilation fails! (This is normal for some individuals)");
		}

		if (status) {
			save(solution, modifiedJavaSources, compiledClasses, list, numberOfEdits);
		}

		evaluations++;
		System.out.println("One fitness evaluation is finished...");
	}

	void save(Solution solution, Map<String, String> modifiedJavaSources, Map<String, JavaFileObject> compiledClasses,
			List<Map.Entry<Integer, Double>> list, int numberOfEdits) {
		List<Integer> opList = new ArrayList<Integer>();
		List<Integer> locList = new ArrayList<Integer>();
		List<Integer> ingredList = new ArrayList<Integer>();

		int[] var0 = ((ArrayInt) solution.getDecisionVariables()[0]).array_;
		int size = var0.length / 2;

		for (int i = 0; i < numberOfEdits; i++) {
			int loc = list.get(i).getKey();
			int op = var0[loc];
			int ingred = var0[loc + size];
			opList.add(op);
			locList.add(loc);
			ingredList.add(ingred);
		}

		try {
			if (addTestAdequatePatch(opList, locList, ingredList)) {
				if (diffFormat) {
					try {
						IO.savePatch(modifiedJavaSources, srcJavaDir, this.patchOutputRoot, globalID);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				saveTestAdequatePatch(opList, locList, ingredList);
				globalID++;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	boolean manipulateOneModificationPoint(int i, int size, int array[], Map<String, ASTRewrite> astRewriters)
			throws JMException {
		ModificationPoint mp = modificationPoints.get(i);
		String manipName = availableManipulations.get(i).get(array[i]);

		Statement ingredStatement = null;
		if (!mp.getIngredients().isEmpty())
			ingredStatement = mp.getIngredients().get(array[i + size]);

		return manipulateOneModificationPoint(mp, manipName, ingredStatement, astRewriters);
	}

	boolean invokeTestExecutor(Map<String, JavaFileObject> compiledClasses, Solution solution) throws Exception {
		Set<String> samplePosTests = getSamplePositiveTests();
		System.out.println("Getting test executor, sample tests: " + samplePosTests.size());
		ITestExecutor testExecutor = getTestExecutor(compiledClasses, samplePosTests);
		System.out.println("Test executor created, running tests (waitTime: " + waitTime + "ms)...");

		boolean status = testExecutor.runTests();
		System.out.println("Tests run completed, status: " + status + ", exceptional: " + testExecutor.isExceptional());

		if (status && percentage != null && percentage < 1) {
			testExecutor = getTestExecutor(compiledClasses, positiveTests);
			status = testExecutor.runTests();
		}

		if (!testExecutor.isExceptional()) {
			double ratioOfFailuresInPositive = testExecutor.getRatioOfFailuresInPositive();
			double ratioOfFailuresInNegative = testExecutor.getRatioOfFailuresInNegative();
			double fitness = weight * testExecutor.getRatioOfFailuresInPositive()
					+ testExecutor.getRatioOfFailuresInNegative();
			
			// 计算行为描述符（如果启用 Novelty Search）
			BehaviorDescriptor behaviorDescriptor = null;
			if (!noveltySearchMode.equalsIgnoreCase("none")) {
				try {
					Set<String> failedTests = testExecutor.getFailedTests();
					if (failedTests == null) {
						System.out.println("Warning: getFailedTests() returned null, using empty set");
						failedTests = new java.util.HashSet<String>();
					}
					behaviorDescriptor = new BehaviorDescriptor(allTests, failedTests);
					
					// 将行为描述符添加到档案
					behaviorArchive.add(behaviorDescriptor);
				} catch (Exception e) {
					System.err.println("Error creating behavior descriptor: " + e.getMessage());
					e.printStackTrace();
					// 继续使用原始适应度，不中断执行
				}
			}
			
			System.out.println("Number of failed tests: "
					+ (testExecutor.getFailureCountInNegative() + testExecutor.getFailureCountInPositive()));
			System.out.println("Weighted failure rate: " + fitness);
			
			// 根据 Novelty Search 模式设置目标值
			if (noveltySearchMode.equalsIgnoreCase("full")) {
				// 完全使用 Novelty Search：使用负的 Novelty Score 作为适应度（越大越好）
				if (behaviorDescriptor != null) {
					try {
						double noveltyScore = behaviorArchive.computeNoveltyScore(behaviorDescriptor, noveltyKNeighbors);
						// 转换为最小化问题（负号）
						double noveltyFitness = -noveltyScore;
						
						if (numberOfObjectives == 1) {
							solution.setObjective(0, noveltyFitness);
						} else if (numberOfObjectives == 2) {
							// 目标0：编辑数量，目标1：负的 Novelty Score
							solution.setObjective(1, noveltyFitness);
						} else {
							// 目标0：编辑数量，目标1：负的 Novelty Score，目标2：保留原始适应度作为参考
							solution.setObjective(1, noveltyFitness);
							solution.setObjective(2, fitness);
						}
						
						System.out.println("Novelty Score: " + noveltyScore);
					} catch (Exception e) {
						System.err.println("Error computing novelty score: " + e.getMessage());
						e.printStackTrace();
						assignMaxObjectiveValues(solution);
					}
				} else {
					System.err.println("Error: behaviorDescriptor is null in full NS mode");
					assignMaxObjectiveValues(solution);
				}
			} else if (noveltySearchMode.equalsIgnoreCase("lightweight")) {
				// 轻量级模式：在适应度基础上加入多样性惩罚项
				if (behaviorDescriptor != null) {
					try {
						double noveltyScore = behaviorArchive.computeNoveltyScore(behaviorDescriptor, noveltyKNeighbors);
						// 多样性越高，惩罚越小（1 - noveltyScore 作为奖励）
						double diversityBonus = (1.0 - noveltyScore) * noveltyDiversityWeight;
						double adjustedFitness = fitness - diversityBonus;
						
						if (numberOfObjectives == 1 || numberOfObjectives == 2) 
							solution.setObjective(numberOfObjectives - 1, adjustedFitness);
						else {
							solution.setObjective(1, ratioOfFailuresInPositive);
							solution.setObjective(2, ratioOfFailuresInNegative);
						}
						
						System.out.println("Novelty Score: " + noveltyScore + ", Diversity Bonus: " + diversityBonus);
					} catch (Exception e) {
						System.err.println("Error computing novelty score: " + e.getMessage());
						e.printStackTrace();
						// 回退到原始适应度
						if (numberOfObjectives == 1 || numberOfObjectives == 2) 
							solution.setObjective(numberOfObjectives - 1, fitness);
						else {
							solution.setObjective(1, ratioOfFailuresInPositive);
							solution.setObjective(2, ratioOfFailuresInNegative);
						}
					}
				} else {
					// behaviorDescriptor 为 null，使用原始适应度
					System.out.println("Warning: behaviorDescriptor is null, using original fitness");
					if (numberOfObjectives == 1 || numberOfObjectives == 2) 
						solution.setObjective(numberOfObjectives - 1, fitness);
					else {
						solution.setObjective(1, ratioOfFailuresInPositive);
						solution.setObjective(2, ratioOfFailuresInNegative);
					}
				}
			} else {
				// 原始模式：使用标准适应度
				if (numberOfObjectives == 1 || numberOfObjectives == 2) 
					solution.setObjective(numberOfObjectives - 1, fitness);
				else {
					solution.setObjective(1, ratioOfFailuresInPositive);
					solution.setObjective(2, ratioOfFailuresInNegative);
				}
			}
		} else {
			assignMaxObjectiveValues(solution);
			System.out.println("Timeout occurs!");
		}
		return status;
	}

	void assignMaxObjectiveValues(Solution solution) {
		for (int i = 0; i < solution.getNumberOfObjectives(); i++)
			solution.setObjective(i, Double.MAX_VALUE);
	}

}
