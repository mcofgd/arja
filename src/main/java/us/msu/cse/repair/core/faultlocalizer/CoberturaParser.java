package us.msu.cse.repair.core.faultlocalizer;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Parser for Cobertura coverage XML files
 * Used by Defects4JFaultLocalizer to extract line coverage information
 */
public class CoberturaParser {
    
    /**
     * Represents coverage information for a single line
     */
    public static class LineCoverage {
        public final String className;
        public final int lineNumber;
        public final int hits;
        
        public LineCoverage(String className, int lineNumber, int hits) {
            this.className = className;
            this.lineNumber = lineNumber;
            this.hits = hits;
        }
        
        @Override
        public String toString() {
            return className + "#" + lineNumber + " (hits: " + hits + ")";
        }
    }
    
    /**
     * Parse Cobertura coverage.xml file
     * 
     * @param coverageXml The coverage.xml file
     * @return Map of (className#lineNumber) -> LineCoverage
     * @throws Exception if parsing fails
     */
    public Map<String, LineCoverage> parse(File coverageXml) throws Exception {
        Map<String, LineCoverage> coverageMap = new HashMap<>();
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        
        // Disable DTD validation and external entity resolution
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/validation", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(coverageXml);
        doc.getDocumentElement().normalize();
        
        // Get all <class> elements
        NodeList classList = doc.getElementsByTagName("class");
        
        for (int i = 0; i < classList.getLength(); i++) {
            Element classElement = (Element) classList.item(i);
            String className = classElement.getAttribute("name");
            
            // Get all <line> elements within this class
            NodeList lineList = classElement.getElementsByTagName("line");
            
            for (int j = 0; j < lineList.getLength(); j++) {
                Element lineElement = (Element) lineList.item(j);
                
                try {
                    int lineNumber = Integer.parseInt(lineElement.getAttribute("number"));
                    int hits = Integer.parseInt(lineElement.getAttribute("hits"));
                    
                    String key = className + "#" + lineNumber;
                    LineCoverage coverage = new LineCoverage(className, lineNumber, hits);
                    coverageMap.put(key, coverage);
                    
                } catch (NumberFormatException e) {
                    // Skip malformed lines
                    continue;
                }
            }
        }
        
        return coverageMap;
    }
    
    /**
     * Filter coverage map to only include project source code
     * Excludes test classes and framework code
     * 
     * @param coverageMap The full coverage map
     * @param projectPackagePrefix Package prefix for project code (e.g., "org.apache.commons")
     * @return Filtered coverage map
     */
    public Map<String, LineCoverage> filterProjectCode(
            Map<String, LineCoverage> coverageMap, 
            String projectPackagePrefix) {
        
        Map<String, LineCoverage> filtered = new HashMap<>();
        
        for (Map.Entry<String, LineCoverage> entry : coverageMap.entrySet()) {
            String className = entry.getValue().className;
            
            // Include only project code
            if (className.startsWith(projectPackagePrefix)) {
                // Exclude test classes
                if (!className.contains("Test") && !className.contains("test")) {
                    filtered.put(entry.getKey(), entry.getValue());
                }
            }
        }
        
        return filtered;
    }
    
    /**
     * Get all classes covered in the coverage map
     * 
     * @param coverageMap The coverage map
     * @return Set of class names
     */
    public Set<String> getCoveredClasses(Map<String, LineCoverage> coverageMap) {
        Set<String> classes = new HashSet<>();
        for (LineCoverage coverage : coverageMap.values()) {
            classes.add(coverage.className);
        }
        return classes;
    }
    
    /**
     * Get all lines with non-zero hits (executed lines)
     * 
     * @param coverageMap The coverage map
     * @return Set of keys (className#lineNumber) for executed lines
     */
    public Set<String> getExecutedLines(Map<String, LineCoverage> coverageMap) {
        Set<String> executedLines = new HashSet<>();
        for (Map.Entry<String, LineCoverage> entry : coverageMap.entrySet()) {
            if (entry.getValue().hits > 0) {
                executedLines.add(entry.getKey());
            }
        }
        return executedLines;
    }
}