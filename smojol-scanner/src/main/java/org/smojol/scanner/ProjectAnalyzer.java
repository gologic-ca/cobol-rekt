package org.smojol.scanner;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates project scanning and CBL-JCL matching
 * Generates mapping report and project structure
 */
@Slf4j
public class ProjectAnalyzer {
    private final ProjectScanner scanner;
    private final JclMatcher matcher;
    private final ObjectMapper mapper = new ObjectMapper();
    
    public ProjectAnalyzer(Path cobolDir, Path jclDir, Path copyDir) throws java.io.IOException {
        this.scanner = new ProjectScanner(
                cobolDir.getParent() != null ? cobolDir.getParent() : Paths.get("."),
                cobolDir, jclDir, copyDir
        );
        this.matcher = new JclMatcher(jclDir);
    }
    
    /**
     * Analyze the entire project and create mappings
     */
    public ProjectAnalysisResult analyzeProject() throws java.io.IOException {
        log.info("Starting project analysis...");
        long startTime = System.currentTimeMillis();
        
        // Step 1: Scan project structure
        ProjectStructure structure = scanner.scanProject();
        
        // Step 2: Create CBL-JCL mappings
        List<CobolJclMapping> mappings = new ArrayList<>();
        int orphanedCount = 0;
        
        for (String cobolFile : structure.getCobolFiles()) {
            String cobolName = cobolFile.replace(".cbl", "").toUpperCase();
            JclMatchResult result = matcher.findJcl(cobolName);
            
            CobolJclMapping mapping = CobolJclMapping.builder()
                    .cobolName(cobolName)
                    .cobolPath(cobolFile)
                    .jclName(result.getJclName())
                    .matchStrategy(result.getMatchStrategy())
                    .confidence(result.getConfidence())
                    .jobName(result.getJobName())
                    .orphaned(result.isOrphaned())
                    .alternateJcls(result.getAlternateJcls())
                    .build();
            
            mappings.add(mapping);
            if (result.isOrphaned()) {
                orphanedCount++;
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        ProjectAnalysisResult result = ProjectAnalysisResult.builder()
                .projectStructure(structure)
                .cblJclMappings(mappings)
                .totalMappings(mappings.size())
                .successfulMappings(mappings.stream().filter(m -> !m.isOrphaned()).count())
                .orphanedPrograms(orphanedCount)
                .matchingStatistics(calculateStatistics(mappings))
                .analysisTimeMs(duration)
                .build();
        
        log.info("Analysis complete in {}ms: {} mappings, {} orphaned",
                duration, result.getTotalMappings(), orphanedCount);
        
        return result;
    }
    
    /**
     * Calculate matching statistics
     */
    private Map<String, Object> calculateStatistics(List<CobolJclMapping> mappings) {
        Map<String, Object> stats = new LinkedHashMap<>();
        
        long byProgramName = mappings.stream()
                .filter(m -> "program_name".equals(m.getMatchStrategy()))
                .count();
        long byNamingConvention = mappings.stream()
                .filter(m -> "naming_convention".equals(m.getMatchStrategy()))
                .count();
        long byJobName = mappings.stream()
                .filter(m -> "job_name".equals(m.getMatchStrategy()))
                .count();
        long byHeuristic = mappings.stream()
                .filter(m -> "heuristic".equals(m.getMatchStrategy()))
                .count();
        
        stats.put("by_program_name", byProgramName);
        stats.put("by_naming_convention", byNamingConvention);
        stats.put("by_job_name", byJobName);
        stats.put("by_heuristic", byHeuristic);
        
        double avgConfidence = mappings.stream()
                .filter(m -> !m.isOrphaned())
                .mapToDouble(CobolJclMapping::getConfidence)
                .average()
                .orElse(0.0);
        stats.put("average_confidence", Math.round(avgConfidence * 1000.0) / 1000.0);
        
        return stats;
    }
    
    /**
     * Export analysis result to JSON file
     */
    public void exportToJson(ProjectAnalysisResult result, Path outputFile) throws java.io.IOException {
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(outputFile.toFile(), result);
        log.info("Analysis exported to: {}", outputFile);
    }
}

/**
 * Complete result of project analysis
 */
@lombok.Data
@lombok.Builder
class ProjectAnalysisResult {
    @JsonProperty("project_structure")
    ProjectStructure projectStructure;
    
    @JsonProperty("cbl_jcl_mappings")
    List<CobolJclMapping> cblJclMappings;
    
    @JsonProperty("total_mappings")
    long totalMappings;
    
    @JsonProperty("successful_mappings")
    long successfulMappings;
    
    @JsonProperty("orphaned_programs")
    int orphanedPrograms;
    
    @JsonProperty("matching_statistics")
    Map<String, Object> matchingStatistics;
    
    @JsonProperty("analysis_time_ms")
    long analysisTimeMs;
}
