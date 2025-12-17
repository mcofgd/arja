package us.msu.cse.repair;

import java.util.HashMap;
import java.util.TimeZone;

import us.msu.cse.repair.algorithms.kali.Kali;
import us.msu.cse.repair.algorithms.kali.KaliAlg;
import us.msu.cse.repair.core.AbstractRepairAlgorithm;

/**
 * 修改说明：将原始基于 Java 7 的实现升级到 Java 11
 * 修改时间：2024-12-19
 * 修改原因：支持 Defects4J v3.0.1 要求 Java 11 环境
 * 主要改动：
 * 1. 添加时区设置以匹配 Defects4J v3.0.1 要求
 */
public class KaliMain {
	public static void main(String args[]) throws Exception {
		// 设置时区以匹配 Defects4J v3.0.1 要求
		TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
		
		HashMap<String, String> parameterStrs = Interpreter.getParameterStrings(args);
		HashMap<String, Object> parameters = Interpreter.getBasicParameterSetting(parameterStrs);
	
		Kali problem = new Kali(parameters);
		AbstractRepairAlgorithm repairAlg = new KaliAlg(problem);	
		repairAlg.execute();
	}
}
