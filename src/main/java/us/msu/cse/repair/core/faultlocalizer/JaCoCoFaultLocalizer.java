package us.msu.cse.repair.core.faultlocalizer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.ILine;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.IRuntime;
import org.jacoco.core.runtime.LoggerRuntime;
import org.jacoco.core.runtime.RuntimeData;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import us.msu.cse.repair.core.parser.LCNode;
import us.msu.cse.repair.core.util.SafeClassLoader;

/**
 * JaCoCo-based Fault Localizer for Java 11+
 * 
 * This implementation uses JaCoCo for coverage collection and implements
 * the Ochiai formula for fault localization, providing a drop-in replacement
 * for GZoltar that works with Java 11+.
 */
public class JaCoCoFaultLocalizer implements IFaultLocalizer {
    Set<String> positiveTestMethods;
    Set<String> negativeTestMethods;
    Map<LCNode, Double> faultyLines;

    public JaCoCoFaultLocalizer(Set<String> binJavaClasses, Set<String> binExecuteTestClasses, 
            String binJavaDir, String binTestDir, Set<String> dependences) 
            throws FileNotFoundException, IOException {
        
        System.out.println("=== JaCoCo Fault Localizer (Java 11+) ===");
        System.out.println("Binary Java Dir: " + binJavaDir);
        System.out.println("Binary Test Dir: " + binTestDir);
        System.out.println("Java Classes to instrument: " + binJavaClasses.size());
        System.out.println("Test Classes to execute: " + binExecuteTestClasses.size());
        
        positiveTestMethods = new HashSet<String>();
        negativeTestMethods = new HashSet<String>();
        faultyLines = new HashMap<LCNode, Double>();

        try {
            // Build classpath
            List<String> classpathList = new ArrayList<>();
            classpathList.add(binJavaDir);
            classpathList.add(binTestDir);
            if (dependences != null) {
                classpathList.addAll(dependences);
            }
            
            // Create URL array for class loader
            URL[] urls = new URL[classpathList.size()];
            for (int i = 0; i < classpathList.size(); i++) {
                urls[i] = new File(classpathList.get(i)).toURI().toURL();
            }
            
            // Create class loader
            URLClassLoader classLoader = new URLClassLoader(urls, getClass().getClassLoader());
            
            // Initialize JaCoCo runtime
            IRuntime runtime = new LoggerRuntime();
            
            // Storage for coverage data per test
            Map<String, ExecutionDataStore> testCoverageMap = new HashMap<>();
            
            // Run each test and collect coverage
            for (String testClassName : binExecuteTestClasses) {
                try {
                    // Load test class using SafeClassLoader
                    Class<?> testClass = SafeClassLoader.loadClass(testClassName, classLoader);
                    
                    // Get all test methods
                    Method[] methods = testClass.getDeclaredMethods();
                    for (Method method : methods) {
                        if (method.isAnnotationPresent(org.junit.Test.class)) {
                            String testMethodName = testClassName + "#" + method.getName();
                            
                            // Create runtime data for this test
                            RuntimeData runtimeData = new RuntimeData();
                            runtime.startup(runtimeData);
                            
                            // Run the test
                            JUnitCore junit = new JUnitCore();
                            Result result = junit.run(testClass);
                            
                            // Collect coverage data
                            ExecutionDataStore executionData = new ExecutionDataStore();
                            SessionInfoStore sessionInfo = new SessionInfoStore();
                            runtimeData.collect(executionData, sessionInfo, false);
                            runtime.shutdown();
                            
                            // Store coverage for this test
                            testCoverageMap.put(testMethodName, executionData);
                            
                            // Classify test result
                            if (result.wasSuccessful()) {
                                positiveTestMethods.add(testMethodName);
                            } else {
                                negativeTestMethods.add(testMethodName);
                                // Print failure info
                                for (Failure failure : result.getFailures()) {
                                    System.out.println("Test failed: " + testMethodName);
                                    System.out.println("  " + failure.getMessage());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error running test class: " + testClassName);
                    e.printStackTrace();
                }
            }
            
            System.out.println("Passed tests: " + positiveTestMethods.size());
            System.out.println("Failed tests: " + negativeTestMethods.size());
            
            // Analyze coverage and calculate suspiciousness
            if (!negativeTestMethods.isEmpty()) {
                analyzeCoverageAndCalculateSuspiciousness(
                    testCoverageMap, 
                    binJavaDir, 
                    binJavaClasses
                );
            } else {
                System.out.println("WARNING: No failing tests found!");
            }
            
            System.out.println("Faulty lines found: " + faultyLines.size());
            
            classLoader.close();
            
        } catch (Exception e) {
            System.err.println("ERROR in JaCoCo Fault Localizer: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Fault localization failed", e);
        }
    }
    
    /**
     * Analyze coverage data and calculate suspiciousness using Ochiai formula
     */
    private void analyzeCoverageAndCalculateSuspiciousness(
            Map<String, ExecutionDataStore> testCoverageMap,
            String binJavaDir,
            Set<String> binJavaClasses) throws IOException {
        
        // For each class, analyze coverage
        for (String className : binJavaClasses) {
            String classFilePath = binJavaDir + "/" + className.replace('.', '/') + ".class";
            File classFile = new File(classFilePath);
            
            if (!classFile.exists()) {
                continue;
            }
            
            // Count coverage for each line
            Map<Integer, LineCoverage> lineCoverageMap = new HashMap<>();
            
            for (Map.Entry<String, ExecutionDataStore> entry : testCoverageMap.entrySet()) {
                String testName = entry.getKey();
                ExecutionDataStore executionData = entry.getValue();
                boolean testFailed = negativeTestMethods.contains(testName);
                
                try {
                    // Analyze this execution
                    CoverageBuilder coverageBuilder = new CoverageBuilder();
                    Analyzer analyzer = new Analyzer(executionData, coverageBuilder);
                    
                    try (FileInputStream fis = new FileInputStream(classFile)) {
                        analyzer.analyzeClass(fis, className);
                    }
                    
                    // Get coverage for this class
                    for (IClassCoverage classCoverage : coverageBuilder.getClasses()) {
                        if (classCoverage.getName().replace('/', '.').equals(className)) {
                            // Process each line
                            for (int line = classCoverage.getFirstLine(); 
                                 line <= classCoverage.getLastLine(); line++) {
                                ILine lineInfo = classCoverage.getLine(line);
                                
                                if (lineInfo.getStatus() != ICounter.EMPTY) {
                                    LineCoverage lc = lineCoverageMap.computeIfAbsent(
                                        line, k -> new LineCoverage());
                                    
                                    if (lineInfo.getInstructionCounter().getCoveredCount() > 0) {
                                        if (testFailed) {
                                            lc.ef++; // executed by failing test
                                        } else {
                                            lc.ep++; // executed by passing test
                                        }
                                    } else {
                                        if (testFailed) {
                                            lc.nf++; // not executed by failing test
                                        } else {
                                            lc.np++; // not executed by passing test
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Continue with next test
                }
            }
            
            // Calculate Ochiai suspiciousness for each line
            for (Map.Entry<Integer, LineCoverage> entry : lineCoverageMap.entrySet()) {
                int lineNumber = entry.getKey();
                LineCoverage lc = entry.getValue();
                
                double suspiciousness = calculateOchiai(lc.ef, lc.ep, lc.nf, lc.np);
                
                if (suspiciousness > 0) {
                    LCNode lcNode = new LCNode(className, lineNumber);
                    faultyLines.put(lcNode, suspiciousness);
                }
            }
        }
    }
    
    /**
     * Calculate Ochiai suspiciousness score
     * Formula: ef / sqrt((ef + nf) * (ef + ep))
     */
    private double calculateOchiai(int ef, int ep, int nf, int np) {
        if (ef == 0) {
            return 0.0;
        }
        
        double denominator = Math.sqrt((ef + nf) * (ef + ep));
        if (denominator == 0) {
            return 0.0;
        }
        
        return ef / denominator;
    }

    @Override
    public Map<LCNode, Double> searchSuspicious(double thr) {
        Map<LCNode, Double> partFaultyLines = new HashMap<LCNode, Double>();
        for (Map.Entry<LCNode, Double> entry : faultyLines.entrySet()) {
            if (entry.getValue() >= thr)
                partFaultyLines.put(entry.getKey(), entry.getValue());
        }
        return partFaultyLines;
    }

    @Override
    public Set<String> getPositiveTests() {
        return this.positiveTestMethods;
    }

    @Override
    public Set<String> getNegativeTests() {
        return this.negativeTestMethods;
    }
    
    /**
     * Helper class to track line coverage statistics
     */
    private static class LineCoverage {
        int ef = 0; // executed by failing tests
        int ep = 0; // executed by passing tests
        int nf = 0; // not executed by failing tests
        int np = 0; // not executed by passing tests
    }
}