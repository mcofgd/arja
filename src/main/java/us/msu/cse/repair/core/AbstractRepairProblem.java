package us.msu.cse.repair.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import javax.tools.JavaFileObject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

import jmetal.core.Problem;
import jmetal.metaheuristics.moead.Utils;
import jmetal.util.Configuration;
import jmetal.util.JMException;
import us.msu.cse.repair.core.compiler.JavaJDKCompiler;
import us.msu.cse.repair.core.coverage.SeedLineGeneratorProcess;
import us.msu.cse.repair.core.coverage.TestFilterProcess;
import us.msu.cse.repair.core.faultlocalizer.*;
import us.msu.cse.repair.core.filterrules.IngredientFilterRule;
import us.msu.cse.repair.core.filterrules.ManipulationFilterRule;
import us.msu.cse.repair.core.manipulation.AbstractManipulation;
import us.msu.cse.repair.core.manipulation.ManipulationFactory;
import us.msu.cse.repair.core.parser.FieldVarDetector;
import us.msu.cse.repair.core.parser.FileASTRequestorImpl;
import us.msu.cse.repair.core.parser.LCNode;
import us.msu.cse.repair.core.parser.LocalVarDetector;
import us.msu.cse.repair.core.parser.MethodDetector;
import us.msu.cse.repair.core.parser.ModificationPoint;
import us.msu.cse.repair.core.parser.SeedStatement;
import us.msu.cse.repair.core.parser.SeedStatementInfo;
import us.msu.cse.repair.core.parser.ingredient.AbstractIngredientScreener;
import us.msu.cse.repair.core.parser.ingredient.IngredientMode;
import us.msu.cse.repair.core.parser.ingredient.IngredientScreenerFactory;
import us.msu.cse.repair.core.testexecutors.ExternalTestExecutor;
import us.msu.cse.repair.core.testexecutors.ITestExecutor;
import us.msu.cse.repair.core.testexecutors.InternalTestExecutor;
import us.msu.cse.repair.core.util.ClassFinder;
import us.msu.cse.repair.core.util.CustomURLClassLoader;
import us.msu.cse.repair.core.util.Helper;
import us.msu.cse.repair.core.util.IO;
import us.msu.cse.repair.core.util.Patch;

public abstract class AbstractRepairProblem extends Problem {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	protected String[] manipulationNames;

	protected Double percentage;
	protected Double thr;

	protected Integer maxNumberOfModificationPoints;

	protected List<ModificationPoint> modificationPoints;
	protected List<List<String>> availableManipulations;

	protected Map<String, CompilationUnit> sourceASTs;
	protected Map<String, String> sourceContents;

	protected Map<SeedStatement, SeedStatementInfo> seedStatements;
	protected Map<String, ITypeBinding> declaredClasses;

	protected Map<LCNode, Double> faultyLines;
	protected String faultyLinesInfoPath;

	protected Set<LCNode> seedLines;

	protected Set<String> positiveTests;
	protected Set<String> negativeTests;

	protected Boolean testFiltered;
	protected String orgPosTestsInfoPath;
	protected String finalTestsInfoPath;

	protected String srcJavaDir;

	protected String binJavaDir;
	protected String binTestDir;
	protected Set<String> dependences;

	protected String externalProjRoot;

	protected String binWorkingRoot;

	protected Set<String> binJavaClasses;
	protected Set<String> binExecuteTestClasses;
	protected String javaClassesInfoPath;
	protected String testClassesInfoPath;

	protected Integer waitTime;


	protected String patchOutputRoot;

	protected String testExecutorName;

	protected String ingredientScreenerName;
	protected IngredientMode ingredientMode;

	protected Boolean ingredientFilterRule;
	protected Boolean manipulationFilterRule;

	protected Boolean seedLineGenerated;
	
	protected Boolean diffFormat;

	protected String jvmPath;
	protected List<String> compilerOptions;

	protected URL[] progURLs;
	
	protected String gzoltarDataDir;

	protected static int globalID;
	protected Set<Patch> patches;

	protected static long launchTime;
	protected static int evaluations;

	

	@SuppressWarnings("unchecked")
	public AbstractRepairProblem(Map<String, Object> parameters) throws Exception {
		binJavaDir = (String) parameters.get("binJavaDir");
		binTestDir = (String) parameters.get("binTestDir");
		srcJavaDir = (String) parameters.get("srcJavaDir");
		dependences = (Set<String>) parameters.get("dependences");
		
		binExecuteTestClasses = (Set<String>) parameters.get("tests");

		percentage = (Double) parameters.get("percentage");

		javaClassesInfoPath = (String) parameters.get("javaClassesInfoPath");
		testClassesInfoPath = (String) parameters.get("testClassesInfoPath");
		
		faultyLinesInfoPath = (String) parameters.get("faultyLinesInfoPath");
	
		gzoltarDataDir = (String) parameters.get("gzoltarDataDir");
		
		String id = Helper.getRandomID();
		
		thr = (Double) parameters.get("thr");
		if (thr == null)
			thr = 0.1;
		
		maxNumberOfModificationPoints = (Integer) parameters.get("maxNumberOfModificationPoints");
		if (maxNumberOfModificationPoints == null)
			maxNumberOfModificationPoints = 40;

		jvmPath = (String) parameters.get("jvmPath");
		if (jvmPath == null)
			jvmPath = System.getProperty("java.home") + "/bin/java";

		externalProjRoot = (String) parameters.get("externalProjRoot");
		if (externalProjRoot == null)
			externalProjRoot = new File("external").getCanonicalPath();

		binWorkingRoot = (String) parameters.get("binWorkingRoot");
		if (binWorkingRoot == null)
			binWorkingRoot = System.getProperty("java.io.tmpdir") + System.getProperty("file.separator") + "working_" + id;
		
		patchOutputRoot = (String) parameters.get("patchOutputRoot");
		if (patchOutputRoot == null)
			patchOutputRoot = "patches_" + id;
		
		orgPosTestsInfoPath = (String) parameters.get("orgPosTestsInfoPath");
		if (orgPosTestsInfoPath == null)
			orgPosTestsInfoPath = System.getProperty("java.io.tmpdir") + System.getProperty("file.separator") + "orgTests_" + id + ".txt";
		
		finalTestsInfoPath = (String) parameters.get("finalTestsInfoPath");
		if (finalTestsInfoPath == null)
			finalTestsInfoPath = System.getProperty("java.io.tmpdir") + System.getProperty("file.separator") + "finalTests_" + id + ".txt";

		manipulationNames = (String[]) parameters.get("manipulationNames");
		if (manipulationNames == null)
			manipulationNames = new String[] { "Delete", "Replace", "InsertBefore" };

		testExecutorName = (String) parameters.get("testExecutorName");
		if (testExecutorName == null)
			testExecutorName = "ExternalTestExecutor";

		ingredientScreenerName = (String) parameters.get("ingredientScreenerName");
		if (ingredientScreenerName == null)
			ingredientScreenerName = "Direct";

		String modeStr = (String) parameters.get("ingredientMode");
		if (modeStr == null)
			ingredientMode = IngredientMode.Package;
		else
			ingredientMode = IngredientMode.valueOf(modeStr);
		
		diffFormat = (Boolean) parameters.get("diffFormat");
		if (diffFormat == null)
			diffFormat = false;
		

		testFiltered = (Boolean) parameters.get("testFiltered");
		if (testFiltered == null)
			testFiltered = true;

		waitTime = (Integer) parameters.get("waitTime");
		if (waitTime == null)
			waitTime = 6000;

		seedLineGenerated = (Boolean) parameters.get("seedLineGenerated");
		if (seedLineGenerated == null)
			seedLineGenerated = true;

		manipulationFilterRule = (Boolean) parameters.get("manipulationFilterRule");
		if (manipulationFilterRule == null)
			manipulationFilterRule = true;

		ingredientFilterRule = (Boolean) parameters.get("ingredientFilterRule");
		if (ingredientFilterRule == null)
			ingredientFilterRule = true;

		checkParameters();
		invokeModules();

		globalID = 0;
		evaluations = 0;
		launchTime = System.currentTimeMillis();
		patches = new HashSet<Patch>();
	}

	void checkParameters() throws Exception {
		if (binJavaDir == null)
			throw new Exception("The build directory of Java classes is not specified!");
		else if (binTestDir == null)
			throw new Exception("The build directory of test classes is not specified!");
		else if (srcJavaDir == null)
			throw new Exception("The directory of Java source code is not specified!");
		else if (dependences == null)
			throw new Exception("The dependences of the buggy program is not specified!");
		else if (!(new File(jvmPath).exists()))
			throw new Exception("The JVM path does not exist!");
		else if (!(new File(externalProjRoot).exists()))
			throw new Exception("The directory of external project does not exist!");
	}

	void invokeModules() throws Exception {
		invokeClassFinder();
		invokeFaultLocalizer();
		invokeSeedLineGenerator();
		invokeASTRequestor();
		invokeLocalVarDetector();
		invokeFieldVarDetector();
		invokeMethodDetector();
		invokeIngredientScreener();
		invokeManipulationInitializer();
		invokeModificationPointsTrimmer();
		invokeTestFilter();
		invokeCompilerOptionsInitializer();
		invokeProgURLsInitializer();
	}

	void invokeClassFinder() throws ClassNotFoundException, IOException {
		ClassFinder finder = new ClassFinder(binJavaDir, binTestDir, dependences);
		binJavaClasses = finder.findBinJavaClasses();
		
		if (binExecuteTestClasses == null)
			binExecuteTestClasses = finder.findBinExecuteTestClasses();

		if (javaClassesInfoPath != null)
			FileUtils.writeLines(new File(javaClassesInfoPath), binJavaClasses);
		if (testClassesInfoPath != null)
			FileUtils.writeLines(new File(testClassesInfoPath), binExecuteTestClasses);
	}

	void invokeFaultLocalizer() throws FileNotFoundException, IOException {
		System.out.println("Fault localization starts...");
		IFaultLocalizer faultLocalizer;
		
		// ✅ 使用 Defects4JFaultLocalizer 替代 GZoltar（Java 11 兼容）
		if (gzoltarDataDir == null) {
			System.out.println("Using Defects4JFaultLocalizer (Java 11 compatible)");
			// ✅ 修复：不传递 externalProjRoot，让 Defects4JFaultLocalizer 自动推导项目根目录
			// 因为 externalProjRoot 指向的是 ARJA 的 external 目录，而不是被修复项目的根目录
			faultLocalizer = new Defects4JFaultLocalizer(binJavaClasses, binExecuteTestClasses, binJavaDir, binTestDir,
					dependences);
		} else {
			System.out.println("Using GZoltarFaultLocalizer2 with pre-computed data");
			faultLocalizer = new GZoltarFaultLocalizer2(gzoltarDataDir);
		}

		faultyLines = faultLocalizer.searchSuspicious(thr);

		// ✅ 关键修复：防御性复制，避免测试集被后续操作修改
		Set<String> originalPositiveTests = faultLocalizer.getPositiveTests();
		Set<String> originalNegativeTests = faultLocalizer.getNegativeTests();
		
		positiveTests = new HashSet<String>(originalPositiveTests);
		negativeTests = new HashSet<String>(originalNegativeTests);
		
		System.out.println("DEBUG: Fault localizer returned " + originalPositiveTests.size() + " positive tests");
		System.out.println("DEBUG: After defensive copy, positiveTests = " + positiveTests.size());
		System.out.println("DEBUG: positiveTests object ID: " + System.identityHashCode(positiveTests));

		if (orgPosTestsInfoPath != null)
			FileUtils.writeLines(new File(orgPosTestsInfoPath), positiveTests);

		System.out.println("Number of positive tests: " + positiveTests.size());
		System.out.println("Number of negative tests: " + negativeTests.size());
		// ✅ 增强日志：输出故障定位找到的可疑行
		System.out.println("Number of faulty lines found: " + faultyLines.size());
		if (faultyLines.isEmpty()) {
			System.err.println("WARNING: No faulty lines found by fault localizer!");
			System.err.println("  This will result in 0 modification points.");
			System.err.println("  Check if:");
			System.err.println("    1. GZoltar is working correctly");
			System.err.println("    2. The threshold (thr=" + thr + ") is too high");
			System.err.println("    3. The test cases are running properly");
		} else {
			System.out.println("Top 10 faulty lines:");
			int count = 0;
			for (Map.Entry<LCNode, Double> entry : faultyLines.entrySet()) {
				System.out.println("  " + entry.getKey() + " -> suspiciousness: " + entry.getValue());
				if (++count >= 10) break;
			}
		}
		System.out.println("Fault localization is finished!");
		System.out.println("DEBUG: At end of invokeFaultLocalizer, positiveTests = " + positiveTests.size());
	}

	void invokeSeedLineGenerator() throws IOException, InterruptedException {
		System.out.println("DEBUG: At start of invokeSeedLineGenerator, positiveTests = " + positiveTests.size());
		
		if (seedLineGenerated) {
			// ✅ Java 11 兼容性修复：使用故障定位结果作为种子行
			// 原代码调用外部进程在 Java 11 下因模块系统限制而失败
			// SeedLineGeneratorProcess slgp = new SeedLineGeneratorProcess(binJavaClasses, javaClassesInfoPath,
			// 		binExecuteTestClasses, testClassesInfoPath, binJavaDir, binTestDir, dependences, externalProjRoot,
			// 		jvmPath);
			// seedLines = slgp.getSeedLines();
			
			// 使用故障定位结果作为种子行（更精确且 Java 11 兼容）
			seedLines = new HashSet<LCNode>(faultyLines.keySet());
			System.out.println("✅ Seed lines generated from fault localization: " + seedLines.size() + " lines");
			System.out.println("   This ensures ingredients are only selected from fault-related code.");
		} else {
			// ⚠️ 警告：设置为 null 会导致所有语句都被当作 seed（InitASTVisitor.java:91）
			// 这会导致 ingredient 池爆炸（5000+ 语句），过滤规则失效，修复能力下降
			seedLines = null;
			System.out.println("⚠️  WARNING: seedLineGenerated=false, all statements will be treated as seeds!");
			System.out.println("   This provides maximum repair capability at the cost of performance.");
			System.out.println("   Recommendation: Set -DseedLineGenerated true");
		}
		
		System.out.println("DEBUG: At end of invokeSeedLineGenerator, positiveTests = " + positiveTests.size());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	void invokeASTRequestor() throws IOException {
		System.out.println("AST parsing starts...");
		
		modificationPoints = new ArrayList<ModificationPoint>();
		seedStatements = new HashMap<SeedStatement, SeedStatementInfo>();
		sourceASTs = new HashMap<String, CompilationUnit>();
		sourceContents = new HashMap<String, String>();
		declaredClasses = new HashMap<String, ITypeBinding>();

		FileASTRequestorImpl requestor = new FileASTRequestorImpl(faultyLines, seedLines, modificationPoints,
				seedStatements, sourceASTs, sourceContents, declaredClasses);

		// ✅ Java 11 兼容性修复：Eclipse JDT 3.10.0 最高支持 JLS8 (Java 8)
		// 虽然使用 JLS8，但配合 Java 11 编译选项仍可正常工作
		ASTParser parser = ASTParser.newParser(AST.JLS8);
		String[] classpathEntries = null;
		if (dependences != null)
			classpathEntries = dependences.toArray(new String[dependences.size()]);

		parser.setEnvironment(classpathEntries, new String[] { srcJavaDir }, null, true);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);

		Map options = JavaCore.getOptions();
		// ✅ Java 11 兼容性修复：Eclipse JDT 3.10.0 最高支持 VERSION_1_8
		// 使用 VERSION_1_8 配合 Java 11 编译器选项可以正常工作
		JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
		parser.setCompilerOptions(options);

		File srcFile = new File(srcJavaDir);
		Collection<File> javaFiles = FileUtils.listFiles(srcFile, new SuffixFileFilter(".java"),
				TrueFileFilter.INSTANCE);
		String[] sourceFilePaths = new String[javaFiles.size()];

		int i = 0;
		for (File file : javaFiles) {
			sourceFilePaths[i++] = file.getCanonicalPath();
			if (file.getName().equals("Fraction.java")) {
				System.out.println("DEBUG: Found Fraction.java at " + file.getCanonicalPath());
			}
		}
		System.out.println("DEBUG: Total source files found: " + sourceFilePaths.length);

		parser.createASTs(sourceFilePaths, null, new String[] { "UTF-8" }, requestor, null);

		if (maxNumberOfModificationPoints != null && modificationPoints.size() > maxNumberOfModificationPoints) {
			Collections.sort(modificationPoints, new Comparator<ModificationPoint>() {
				@Override
				public int compare(ModificationPoint o1, ModificationPoint o2) {
					Double d1 = new Double(o1.getSuspValue());
					Double d2 = new Double(o2.getSuspValue());
					return d2.compareTo(d1);
				}
			});

			List<ModificationPoint> temp = new ArrayList<ModificationPoint>();
			int[] permutation = new int[maxNumberOfModificationPoints];
			Utils.randomPermutation(permutation, maxNumberOfModificationPoints);

			for (i = 0; i < maxNumberOfModificationPoints; i++) {
				ModificationPoint mp = modificationPoints.get(permutation[i]);
				temp.add(mp);
			}

			modificationPoints = temp;
		}
		
		System.out.println("AST parsing is finished!");
	}

	void invokeLocalVarDetector() {
		System.out.println("Detection of local variables starts...");
		LocalVarDetector lvd = new LocalVarDetector(modificationPoints);
		lvd.detect();
		System.out.println("Detection of local variables is finished!");
	}

	void invokeMethodDetector() throws ClassNotFoundException, IOException {
		System.out.println("Detection of methods starts...");
		MethodDetector md = new MethodDetector(modificationPoints, declaredClasses, dependences);
		md.detect();
		System.out.println("Detection of methods is finished!");
	}

	void invokeFieldVarDetector() throws ClassNotFoundException, IOException {
		System.out.println("Detection of fields starts...");
		FieldVarDetector fvd = new FieldVarDetector(modificationPoints, declaredClasses, dependences);
		fvd.detect();
		System.out.println("Detection of fields is finished!");
	}

	void invokeIngredientScreener() throws JMException {
		System.out.println("Ingredient screener starts...");
		AbstractIngredientScreener ingredientScreener = IngredientScreenerFactory
				.getIngredientScreener(ingredientScreenerName, modificationPoints, seedStatements, ingredientMode);
		ingredientScreener.screen();

		if (ingredientFilterRule) {
			for (ModificationPoint mp : modificationPoints) {
				Iterator<Statement> iterator = mp.getIngredients().iterator();
				while (iterator.hasNext()) {
					Statement seed = iterator.next();
					if (IngredientFilterRule.canFiltered(seed, mp))
						iterator.remove();
				}
			}
		}
		System.out.println("Ingredient screener is finished!");
	}

	void invokeManipulationInitializer() {
		System.out.println("Initialization of manipulations starts...");
		availableManipulations = new ArrayList<List<String>>(modificationPoints.size());

		for (int i = 0; i < modificationPoints.size(); i++) {
			ModificationPoint mp = modificationPoints.get(i);
			List<String> list = new ArrayList<String>();
			for (int j = 0; j < manipulationNames.length; j++) {
				String manipulationName = manipulationNames[j];

				if (manipulationFilterRule) {
					if (!ManipulationFilterRule.canFiltered(manipulationName, mp))
						list.add(manipulationName);
				} else
					list.add(manipulationName);
			}
			
			// ✅ 强制添加 Delete 操作（如果未被添加）
			// 确保每个修改点至少有一个操作可用，避免被 Trimmer 误删
			if (!list.contains("Delete")) {
				list.add("Delete");
			}
			
			availableManipulations.add(list);
		}
		System.out.println("Initialization of manipulations is finished!");
	}

	void invokeTestFilter() throws IOException, InterruptedException {
		System.out.println("DEBUG: At start of invokeTestFilter, positiveTests = " + positiveTests.size());
		System.out.println("DEBUG: positiveTests object ID: " + System.identityHashCode(positiveTests));
		System.out.println("DEBUG: testFiltered = " + testFiltered);
		System.out.println("Filtering of the tests starts...");
		
		// ✅ Java 11 兼容性修复：TestFilter 在 Java 11 下可能失败
		// 保存原始测试数量用于对比
		int originalPositiveTestsCount = positiveTests.size();
		
		if (testFiltered) {
			System.out.println("DEBUG: Entering testFiltered block");
			Set<LCNode> fLines;
			if (maxNumberOfModificationPoints != null) {
				fLines = new HashSet<LCNode>();
				for (ModificationPoint mp : modificationPoints)
					fLines.add(mp.getLCNode());
			} else
				fLines = faultyLines.keySet();

			if (faultyLinesInfoPath != null) {
				List<String> lines = new ArrayList<String>();
				for (LCNode node : fLines)
					lines.add(node.toString());
				FileUtils.writeLines(new File(faultyLinesInfoPath), lines);
			}

			try {
				TestFilterProcess tfp = new TestFilterProcess(fLines, faultyLinesInfoPath, positiveTests,
						orgPosTestsInfoPath, binJavaDir, binTestDir, dependences, externalProjRoot, jvmPath);
				Set<String> filteredTests = tfp.getFilteredPositiveTests();
				
				// ✅ 关键修复：如果过滤后测试为空，保留原始测试
				if (filteredTests == null || filteredTests.isEmpty()) {
					System.err.println("⚠️  WARNING: Test filtering returned 0 tests!");
					System.err.println("   This is likely due to JaCoCo coverage collection failure in Java 11.");
					System.err.println("   Keeping original " + originalPositiveTestsCount + " positive tests.");
					System.err.println("   Recommendation: Set -DtestFiltered false to skip filtering.");
				} else {
					positiveTests = filteredTests;
					System.out.println("✅ Test filtering succeeded: " + originalPositiveTestsCount + " → " + positiveTests.size() + " tests");
				}
			} catch (Exception e) {
				System.err.println("⚠️  Test filtering failed with exception: " + e.getMessage());
				System.err.println("   Keeping original " + originalPositiveTestsCount + " positive tests.");
				e.printStackTrace();
			}
		}

		if (finalTestsInfoPath != null) {
			List<String> finalTests = new ArrayList<String>();
			finalTests.addAll(positiveTests);
			finalTests.addAll(negativeTests);
			FileUtils.writeLines(new File(finalTestsInfoPath), finalTests);
		}
		
		System.out.println("DEBUG: After all operations, positiveTests = " + positiveTests.size());
		System.out.println("Number of positive tests considered: " + positiveTests.size() );
		System.out.println("Filtering of the tests is finished!");
	}

	void invokeCompilerOptionsInitializer() {
		compilerOptions = new ArrayList<String>();
		compilerOptions.add("-nowarn");
		compilerOptions.add("-source");
		// ✅ Java 11 兼容性修复：使用 "11" 替代 "1.7"
		compilerOptions.add("11");
		compilerOptions.add("-target");
		// ✅ Java 11 兼容性修复：添加 target 版本
		compilerOptions.add("11");
		compilerOptions.add("-cp");
		String cpStr = binJavaDir;

		if (dependences != null) {
			for (String str : dependences)
				cpStr += (File.pathSeparator + str);
		}

		compilerOptions.add(cpStr);
	}

	void invokeProgURLsInitializer() throws MalformedURLException {
		List<String> tempList = new ArrayList<String>();
		tempList.add(binJavaDir);
		tempList.add(binTestDir);
		if (dependences != null)
			tempList.addAll(dependences);
		progURLs = Helper.getURLs(tempList);
	}

	void invokeModificationPointsTrimmer() {
		// ✅ 修复：不删除没有ingredients的修改点，保留所有操作
		// 这样即使ingredient screening失败，仍然可以进行Delete操作
		System.out.println("Modification points trimmer starts...");
		System.out.println("  Total modification points before trimming: " + modificationPoints.size());
		
		int i = 0;
		int keptWithoutIngredients = 0;
		
		while (i < modificationPoints.size()) {
			ModificationPoint mp = modificationPoints.get(i);
			List<String> manips = availableManipulations.get(i);

			if (mp.getIngredients().isEmpty()) {
				// ✅ 关键修复：保留Delete操作，不删除修改点
				// 即使没有ingredients，Delete操作仍然有效
				keptWithoutIngredients++;
				
				// 只保留Delete操作（因为Replace/Insert需要ingredients）
				Iterator<String> iter = manips.iterator();
				while (iter.hasNext()) {
					String manipName = iter.next();
					if (!manipName.equalsIgnoreCase("Delete"))
						iter.remove();
				}
			}

			// ✅ 关键修复：即使manips为空也不删除修改点
			// 让后续流程决定如何处理
			if (manips.isEmpty()) {
				// 不删除，只是标记
				System.out.println("  Warning: MP at " + mp.getLCNode() + " has no available manipulations");
			}
			i++;
		}
		
		System.out.println("  Total modification points after trimming: " + modificationPoints.size());
		System.out.println("  Modification points without ingredients: " + keptWithoutIngredients);
		System.out.println("Modification points trimmer is finished!");
	}

	protected Map<String, String> getModifiedJavaSources(Map<String, ASTRewrite> astRewriters) {
		Map<String, String> javaSources = new HashMap<String, String>();

		for (Entry<String, ASTRewrite> entry : astRewriters.entrySet()) {
			String sourceFilePath = entry.getKey();
			String content = sourceContents.get(sourceFilePath);

			Document doc = new Document(content);
			TextEdit edits = entry.getValue().rewriteAST(doc, null);

			try {
				edits.apply(doc);
			} catch (BadLocationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			javaSources.put(sourceFilePath, doc.get());
		}
		return javaSources;
	}

	protected boolean manipulateOneModificationPoint(ModificationPoint mp, String manipName, Statement ingredStatement,
			Map<String, ASTRewrite> astRewriters) throws JMException {
		String sourceFilePath = mp.getSourceFilePath();
		ASTRewrite rewriter;
		if (astRewriters.containsKey(sourceFilePath))
			rewriter = astRewriters.get(sourceFilePath);
		else {
			CompilationUnit unit = sourceASTs.get(sourceFilePath);
			rewriter = ASTRewrite.create(unit.getAST());
			astRewriters.put(sourceFilePath, rewriter);
		}

		AbstractManipulation manipulation = ManipulationFactory.getManipulation(manipName, mp, ingredStatement,
				rewriter);
		return manipulation.manipulate();
	}

	protected Map<String, JavaFileObject> getCompiledClassesForTestExecution(Map<String, String> javaSources) {
		JavaJDKCompiler compiler = new JavaJDKCompiler(ClassLoader.getSystemClassLoader(), compilerOptions);
		try {
			boolean isCompiled = compiler.compile(javaSources);
			if (isCompiled)
				return compiler.getClassLoader().getCompiledClasses();
			else {
				// ✅ 增强错误日志：输出编译错误详情
				System.err.println("Compilation failed with the following errors:");
				List<String> errors = compiler.getErrors();
				for (String error : errors) {
					System.err.println("  " + error);
				}
				return null;
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			System.err.println("Exception during compilation:");
			e.printStackTrace();
		}
		return null;
	}

	protected ITestExecutor getTestExecutor(Map<String, JavaFileObject> compiledClasses, Set<String> executePosTests)
			throws JMException, IOException {
		if (testExecutorName.equalsIgnoreCase("ExternalTestExecutor")) {
			File binWorkingDirFile = new File(binWorkingRoot, "bin_" + (globalID++));
			IO.saveCompiledClasses(compiledClasses, binWorkingDirFile);
			String binWorkingDir = binWorkingDirFile.getCanonicalPath();
			String tempPath = (executePosTests == positiveTests) ? finalTestsInfoPath : null;
			return new ExternalTestExecutor(executePosTests, negativeTests, tempPath, binJavaDir, binTestDir,
					dependences, binWorkingDir, externalProjRoot, jvmPath, waitTime);

		} else if (testExecutorName.equalsIgnoreCase("InternalTestExecutor")) {
			CustomURLClassLoader urlClassLoader = new CustomURLClassLoader(progURLs, compiledClasses);
			return new InternalTestExecutor(executePosTests, negativeTests, urlClassLoader, waitTime);
		} else {
			Configuration.logger_.severe("test executor name '" + testExecutorName + "' not found ");
			throw new JMException("Exception in getTestExecutor()");
		}
	}

	protected Set<String> getSamplePositiveTests() {
		// ✅ 修复：如果测试过滤失败导致 positiveTests 为空，返回空集合
		if (positiveTests == null || positiveTests.isEmpty()) {
			System.err.println("⚠️  WARNING: No positive tests available for sampling!");
			System.err.println("   This usually means test filtering failed in Java 11 environment.");
			System.err.println("   Recommendation: Set -DtestFiltered false");
			return new HashSet<String>();  // 返回空集合，避免 NullPointerException
		}
		
		// ✅ 添加调试日志
		System.out.println("DEBUG: getSamplePositiveTests() called");
		System.out.println("  percentage = " + percentage);
		System.out.println("  positiveTests.size() = " + positiveTests.size());
		
		if (percentage == null || percentage == 1) {
			System.out.println("  Returning all " + positiveTests.size() + " tests (no sampling)");
			return positiveTests;
		} else {
			int num = (int) (positiveTests.size() * percentage);
			System.out.println("  Calculated sample size: " + num + " (" + (percentage * 100) + "%)");
			
			if (num == 0 && !positiveTests.isEmpty()) {
				// ✅ 修复：percentage 太小导致选中 0 个测试
				num = 1;  // 至少选中 1 个测试
				System.out.println("⚠️  Percentage too small, forcing at least 1 test");
			}
			List<String> tempList = new ArrayList<String>(positiveTests);
			Collections.shuffle(tempList);
			Set<String> samplePositiveTests = new HashSet<String>();
			for (int i = 0; i < num; i++)
				samplePositiveTests.add(tempList.get(i));
			
			System.out.println("✅ Sampled " + samplePositiveTests.size() + " tests from " + positiveTests.size());
			return samplePositiveTests;
		}
	}

	public List<List<String>> getAvailableManipulations() {
		return this.availableManipulations;
	}

	public List<ModificationPoint> getModificationPoints() {
		return this.modificationPoints;
	}

	public String[] getManipulationNames() {
		return this.manipulationNames;
	}

	public Map<String, CompilationUnit> getSourceASTs() {
		return this.sourceASTs;
	}

	public Map<String, String> getSourceContents() {
		return this.sourceContents;
	}

	public Set<String> getNegativeTests() {
		return this.negativeTests;
	}

	public Set<String> getPositiveTests() {
		return this.positiveTests;
	}

	public Double getPercentage() {
		return this.percentage;
	}

	public void saveTestAdequatePatch(List<Integer> opList, List<Integer> locList, List<Integer> ingredList)
			throws IOException {
		long estimatedTime = System.currentTimeMillis() - launchTime;
		if (patchOutputRoot != null)
			IO.savePatch(opList, locList, ingredList, modificationPoints, availableManipulations, patchOutputRoot,
					globalID, evaluations, estimatedTime);
	}

	public boolean addTestAdequatePatch(List<Integer> opList, List<Integer> locList, List<Integer> ingredList) {
		Patch patch = new Patch(opList, locList, ingredList, modificationPoints, availableManipulations);
		return patches.add(patch);
	}

	public String getSrcJavaDir() {
		return this.srcJavaDir;
	}

	public String getBinJavaDir() {
		return this.binJavaDir;
	}

	public String getBinTestDir() {
		return this.binTestDir;
	}

	public Set<String> getDependences() {
		return this.dependences;
	}

	public int getNumberOfModificationPoints() {
		return modificationPoints.size();
	}

	public Set<Patch> getPatches() {
		return this.patches;
	}

	public void clearPatches() {
		patches.clear();
	}

	public void resetPatchOutputRoot(String patchOutputRoot) {
		this.patchOutputRoot = patchOutputRoot;
	}


	public void resetBinWorkingRoot(String binWorkingRoot) {
		this.binWorkingRoot = binWorkingRoot;
	}

	public static void resetGlobalID(int id) {
		globalID = id;
	}

	public static void increaseGlobalID() {
		globalID++;
	}

	public static void resetLaunchTime(long time) {
		launchTime = time;
	}

	public static long getLaunchTime() {
		return launchTime;
	}

	public static void resetEvaluations(int evals) {
		evaluations = evals;
	}

	public static int getEvaluations() {
		return evaluations;
	}
	
	public String getBinWorkingRoot() {
		return binWorkingRoot;
	}
	
	public String getOrgPosTestsInfoPath() {
		return orgPosTestsInfoPath;
	}
	
	public String getFinalTestsInfoPath() {
		return finalTestsInfoPath;
	}

}
