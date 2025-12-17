package us.msu.cse.repair.core.faultlocalizer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import us.msu.cse.repair.core.parser.LCNode;

/**
 * Fault localizer that uses Defects4J's coverage data
 * Compatible with Java 11 and Defects4J v3.0+
 */
public class Defects4JFaultLocalizer implements IFaultLocalizer {
    
    private String projectDir;
    private Map<LCNode, Double> faultyLines;
    private Set<String> positiveTestMethods;
    private Set<String> negativeTestMethods;
    
    /**
     * Constructor matching GZoltarFaultLocalizer signature
     *
     * @param binJavaClasses Classes to analyze (not used, kept for compatibility)
     * @param binExecuteTestClasses Test classes to execute (not used, kept for compatibility)
     * @param binJavaDir Binary directory for Java classes
     * @param binTestDir Binary directory for test classes
     * @param dependences Dependencies (not used, kept for compatibility)
     */
    /**
     * Constructor with explicit project root (recommended)
     */
    public Defects4JFaultLocalizer(Set<String> binJavaClasses, Set<String> binExecuteTestClasses,
                                   String binJavaDir, String binTestDir, Set<String> dependences,
                                   String externalProjRoot) {
        this.projectDir = externalProjRoot;
        this.faultyLines = new HashMap<>();
        this.positiveTestMethods = new HashSet<>();
        this.negativeTestMethods = new HashSet<>();
        
        System.out.println("Defects4JFaultLocalizer initialized");
        System.out.println("  binJavaDir: " + binJavaDir);
        System.out.println("  externalProjRoot: " + externalProjRoot);
        System.out.println("  Using projectDir: " + projectDir);
    }
    
    /**
     * Constructor with auto-derived project root (fallback)
     */
    public Defects4JFaultLocalizer(Set<String> binJavaClasses, Set<String> binExecuteTestClasses,
                                   String binJavaDir, String binTestDir, Set<String> dependences) {
        // Derive project directory from binJavaDir
        // binJavaDir is typically: /path/to/project/target/classes or /path/to/project/build/classes
        // We need: /path/to/project
        File binDir = new File(binJavaDir);
        File targetDir = binDir.getParentFile(); // target or build
        this.projectDir = targetDir.getParent(); // project root
        
        this.faultyLines = new HashMap<>();
        this.positiveTestMethods = new HashSet<>();
        this.negativeTestMethods = new HashSet<>();
        
        System.out.println("Defects4JFaultLocalizer initialized (auto-derived)");
        System.out.println("  binJavaDir: " + binJavaDir);
        System.out.println("  Derived projectDir: " + projectDir);
    }
    
    @Override
    public Map<LCNode, Double> searchSuspicious(double thr) {
        try {
            System.out.println("=== Defects4J Fault Localizer (Java 11+) ===");
            System.out.println("Project directory: " + projectDir);
            System.out.println("Suspiciousness threshold: " + thr);
            
            // Step 1: Run Defects4J coverage analysis
            runDefects4JCoverage();
            
            // Step 2: Get all tests
            Set<String> allTests = getAllTests();
            System.out.println("Total tests: " + allTests.size());
            
            // Step 3: Parse failing tests
            parseFailingTests();
            System.out.println("Failing tests: " + negativeTestMethods.size());
            
            // Step 4: Calculate passing tests
            positiveTestMethods.addAll(allTests);
            positiveTestMethods.removeAll(negativeTestMethods);
            System.out.println("Passing tests: " + positiveTestMethods.size());
            
            // Step 5: Parse coverage and calculate suspiciousness
            parseCoverageAndCalculateSuspiciousness();
            
            // Step 6: Filter by threshold
            Map<LCNode, Double> filtered = new HashMap<>();
            for (Map.Entry<LCNode, Double> entry : faultyLines.entrySet()) {
                if (entry.getValue() >= thr) {
                    filtered.put(entry.getKey(), entry.getValue());
                }
            }
            
            System.out.println("Faulty lines found (total): " + faultyLines.size());
            System.out.println("Faulty lines above threshold " + thr + ": " + filtered.size());
            
            // Print top 10 faulty lines
            System.out.println("Top 10 faulty lines:");
            faultyLines.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .limit(10)
                .forEach(e -> System.out.println("  " + e.getKey() + " -> suspiciousness: " + e.getValue()));
            
            return filtered;
            
        } catch (Exception e) {
            System.err.println("Error in fault localization: " + e.getMessage());
            e.printStackTrace();
            return new HashMap<>();
        }
    }
    
    /**
     * Run Defects4J coverage command
     */
    private void runDefects4JCoverage() throws Exception {
        System.out.println("Running Defects4J coverage analysis...");
        System.out.println("  Project directory: " + projectDir);
        
        ProcessBuilder pb = new ProcessBuilder("defects4j", "coverage");
        pb.directory(new File(projectDir));
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        // Read output and error
        BufferedReader reader = new BufferedReader(
            new java.io.InputStreamReader(process.getInputStream())
        );
        BufferedReader errorReader = new BufferedReader(
            new java.io.InputStreamReader(process.getErrorStream())
        );
        
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("  [defects4j] " + line);
        }
        
        // Read error stream
        while ((line = errorReader.readLine()) != null) {
            System.err.println("  [defects4j ERROR] " + line);
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.err.println("Defects4J coverage failed with exit code: " + exitCode);
            System.err.println("Project directory: " + projectDir);
            System.err.println("Make sure the project is properly checked out and compiled.");
            throw new Exception("Defects4J coverage command failed with exit code: " + exitCode);
        }
        
        System.out.println("Defects4J coverage completed successfully");
    }
    
    /**
     * Get all tests using defects4j export
     */
    private Set<String> getAllTests() throws Exception {
        Set<String> allTests = new HashSet<>();
        
        ProcessBuilder pb = new ProcessBuilder(
            "defects4j", "export", "-p", "tests.all", "-w", projectDir
        );
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(
            new java.io.InputStreamReader(process.getInputStream())
        );
        
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty()) {
                // Convert :: to # for consistency
                allTests.add(line.replace("::", "#"));
            }
        }
        
        process.waitFor();
        return allTests;
    }
    
    /**
     * Parse failing tests from failing_tests file
     */
    private void parseFailingTests() throws IOException {
        File failingTestsFile = new File(projectDir, "failing_tests");
        
        if (!failingTestsFile.exists()) {
            System.out.println("WARNING: failing_tests file not found");
            return;
        }
        
        try (BufferedReader br = new BufferedReader(new FileReader(failingTestsFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("---")) {
                    // Convert :: to # for consistency
                    negativeTestMethods.add(line.replace("::", "#"));
                }
            }
        }
    }
    
    /**
     * Parse coverage.xml and calculate Ochiai suspiciousness scores
     */
    private void parseCoverageAndCalculateSuspiciousness() throws Exception {
        File coverageXml = new File(projectDir, "coverage.xml");
        
        if (!coverageXml.exists()) {
            System.out.println("ERROR: coverage.xml not found");
            throw new Exception("coverage.xml not found in " + projectDir);
        }
        
        System.out.println("Parsing coverage.xml...");
        
        // Parse Cobertura XML
        CoberturaParser parser = new CoberturaParser();
        Map<String, CoberturaParser.LineCoverage> coverageMap = parser.parse(coverageXml);
        
        System.out.println("Total lines in coverage: " + coverageMap.size());
        
        // Detect project package prefix from coverage data
        String projectPackage = detectProjectPackage(coverageMap);
        System.out.println("Detected project package: " + projectPackage);
        
        // Filter to only project code
        Map<String, CoberturaParser.LineCoverage> projectCoverage = 
            parser.filterProjectCode(coverageMap, projectPackage);
        
        System.out.println("Project code lines: " + projectCoverage.size());
        
        // Calculate Ochiai scores
        calculateOchiaiScores(projectCoverage);
    }
    
    /**
     * Detect project package prefix from coverage data
     */
    private String detectProjectPackage(Map<String, CoberturaParser.LineCoverage> coverageMap) {
        // Find the most common package prefix
        Map<String, Integer> packageCounts = new HashMap<>();
        
        for (CoberturaParser.LineCoverage coverage : coverageMap.values()) {
            String className = coverage.className;
            
            // Skip test classes
            if (className.contains("Test") || className.contains("test")) {
                continue;
            }
            
            // Get package (first 3 parts of class name)
            String[] parts = className.split("\\.");
            if (parts.length >= 3) {
                String packagePrefix = parts[0] + "." + parts[1] + "." + parts[2];
                packageCounts.put(packagePrefix, packageCounts.getOrDefault(packagePrefix, 0) + 1);
            }
        }
        
        // Return the most common package
        return packageCounts.entrySet().stream()
            .max((e1, e2) -> Integer.compare(e1.getValue(), e2.getValue()))
            .map(Map.Entry::getKey)
            .orElse("org.apache.commons");
    }
    
    /**
     * Calculate Ochiai suspiciousness scores
     * 
     * Ochiai formula: ef / sqrt((ef + ep) * (ef + nf))
     * where:
     *   ef = number of failing tests that execute the line
     *   ep = number of passing tests that execute the line
     *   nf = number of failing tests that do not execute the line
     *   np = number of passing tests that do not execute the line
     */
    private void calculateOchiaiScores(Map<String, CoberturaParser.LineCoverage> projectCoverage) {
        int totalFailingTests = negativeTestMethods.size();
        int totalPassingTests = positiveTestMethods.size();
        
        System.out.println("Calculating Ochiai scores...");
        System.out.println("  Total failing tests: " + totalFailingTests);
        System.out.println("  Total passing tests: " + totalPassingTests);
        
        if (totalFailingTests == 0) {
            System.out.println("WARNING: No failing tests found!");
            return;
        }
        
        for (Map.Entry<String, CoberturaParser.LineCoverage> entry : projectCoverage.entrySet()) {
            CoberturaParser.LineCoverage coverage = entry.getValue();
            
            // For simplicity, assume if a line has hits > 0, it was executed by tests
            // In reality, we would need per-test coverage data
            // Here we use a heuristic: lines with low hits are more suspicious
            
            if (coverage.hits > 0) {
                // Heuristic: assume lines executed by fewer tests are more suspicious
                // This is a simplification since we don't have per-test coverage
                
                // Estimate ef and ep based on hits
                // Lines with very few hits are likely only executed by failing tests
                double suspiciousness;
                
                if (coverage.hits <= totalFailingTests) {
                    // Likely executed mainly by failing tests
                    int ef = Math.min(coverage.hits, totalFailingTests);
                    int ep = Math.max(0, coverage.hits - ef);
                    int nf = totalFailingTests - ef;
                    
                    if (ef > 0) {
                        suspiciousness = ef / Math.sqrt((ef + ep) * (ef + nf));
                    } else {
                        suspiciousness = 0.0;
                    }
                } else {
                    // Executed by many tests, less suspicious
                    int ef = totalFailingTests;
                    int ep = coverage.hits - ef;
                    int nf = 0;
                    
                    suspiciousness = ef / Math.sqrt((ef + ep) * (ef + nf + 1));
                }
                
                if (suspiciousness > 0) {
                    LCNode lcNode = new LCNode(coverage.className, coverage.lineNumber);
                    faultyLines.put(lcNode, suspiciousness);
                }
            }
        }
    }
    
    @Override
    public Set<String> getPositiveTests() {
        return positiveTestMethods;
    }
    
    @Override
    public Set<String> getNegativeTests() {
        return negativeTestMethods;
    }
}