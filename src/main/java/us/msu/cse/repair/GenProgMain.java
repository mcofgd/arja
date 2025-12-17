package us.msu.cse.repair;

import java.util.HashMap;
import java.util.TimeZone;

import jmetal.operators.crossover.Crossover;
import jmetal.operators.mutation.Mutation;
import jmetal.operators.selection.Selection;
import jmetal.operators.selection.SelectionFactory;

import us.msu.cse.repair.algorithms.genprog.GenProg;
import us.msu.cse.repair.core.AbstractRepairAlgorithm;
import us.msu.cse.repair.ec.operators.crossover.ExtendedCrossoverFactory;
import us.msu.cse.repair.ec.operators.mutation.ExtendedMutationFactory;
import us.msu.cse.repair.ec.problems.GenProgProblem;

/**
 * 修改说明：将原始基于 Java 7 的实现升级到 Java 11
 * 修改时间：2024-12-19
 * 修改原因：支持 Defects4J v3.0.1 要求 Java 11 环境
 * 主要改动：
 * 1. 添加时区设置以匹配 Defects4J v3.0.1 要求
 */
public class GenProgMain {
	public static void main(String args[]) throws Exception {
		// 设置时区以匹配 Defects4J v3.0.1 要求
		TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
		
		HashMap<String, String> parameterStrs = Interpreter.getParameterStrings(args);
		HashMap<String, Object> parameters = Interpreter.getBasicParameterSetting(parameterStrs);

		
		parameters.put("ingredientFilterRule", false);
		parameters.put("manipulationFilterRule", false);
		parameters.put("ingredientScreenerName", "Simple");
		
		int populationSize = 40;
		int maxGenerations = 50;
		
		String populationSizeS = parameterStrs.get("populationSize");
		if (populationSizeS != null)
			populationSize = Integer.parseInt(populationSizeS);
		
		String maxGenerationsS = parameterStrs.get("maxGenerations");
		if (maxGenerationsS != null)
			maxGenerations = Integer.parseInt(maxGenerationsS);
		
		
		GenProgProblem problem = new GenProgProblem(parameters);
		AbstractRepairAlgorithm repairAlg = new GenProg(problem);

		repairAlg.setInputParameter("populationSize", populationSize);
		repairAlg.setInputParameter("maxEvaluations", populationSize * maxGenerations);

		Crossover crossover;
		Mutation mutation;
		Selection selection;

		parameters = new HashMap<String, Object>();
		parameters.put("probability", 1.0);
		crossover = ExtendedCrossoverFactory.getCrossoverOperator("GenProgSinglePointCrossover", parameters);

		parameters = new HashMap<String, Object>();
		parameters.put("probability", 0.06);
		mutation = ExtendedMutationFactory.getMutationOperator("GenProgMutation", parameters);

		// Selection Operator
		parameters = null;
		selection = SelectionFactory.getSelectionOperator("BinaryTournament", parameters);

		// Add the operators to the algorithm
		repairAlg.addOperator("crossover", crossover);
		repairAlg.addOperator("mutation", mutation);
		repairAlg.addOperator("selection", selection);
		
		repairAlg.execute();
	}
}
