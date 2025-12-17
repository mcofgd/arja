package us.msu.cse.repair;

import java.util.TimeZone;

import jmetal.util.JMException;

/**
 * 修改说明：将原始基于 Java 7 的实现升级到 Java 11
 * 修改时间：2024-12-19
 * 修改原因：支持 Defects4J v3.0.1 要求 Java 11 环境
 * 主要改动：
 * 1. 添加时区设置以匹配 Defects4J v3.0.1 要求
 */
class Main {
	public static void main(String args[]) throws Exception {
		// 设置时区以匹配 Defects4J v3.0.1 要求
		TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
		
		if (args[0].equalsIgnoreCase("Arja"))
			ArjaMain.main(args);
		else if (args[0].equalsIgnoreCase("GenProg"))
			GenProgMain.main(args);
		else if (args[0].equalsIgnoreCase("RSRepair"))
			RSRepairMain.main(args);
		else if (args[0].equalsIgnoreCase("Kali"))
			KaliMain.main(args);
		else if (args[0].equalsIgnoreCase("-listParameters"))
			ParameterInfoMain.main(args);
		else {
			throw new JMException("The repair apporach " + args[0] + " does not exist!");
		}
	}
}