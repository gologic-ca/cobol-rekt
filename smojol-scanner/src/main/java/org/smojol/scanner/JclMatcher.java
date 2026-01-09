package org.smojol.scanner;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Matches COBOL programs with their associated JCL files using multiple strategies
 */
@Slf4j
public class JclMatcher {
    private final Path jclDir;
    private Map<String, String> jclContent = new HashMap<>();
    
    public JclMatcher(Path jclDir) throws java.io.IOException {
        this.jclDir = jclDir;
        loadJclContent();
    }
    
    /**
     * Load JCL file contents into memory for fast searching
     */
    private void loadJclContent() throws java.io.IOException {
        if (!Files.exists(jclDir)) {
            log.warn("JCL directory not found: {}", jclDir);
            return;
        }
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(jclDir, "*.jcl")) {
            for (Path file : stream) {
                try {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    jclContent.put(file.getFileName().toString(), content);
                } catch (java.io.IOException e) {
                    log.warn("Could not read JCL file: {}", file, e);
                }
            }
        }
        log.info("Loaded {} JCL files for matching", jclContent.size());
    }
    
    /**
     * Find JCL file(s) for a given COBOL program using multiple strategies
     */
    public JclMatchResult findJcl(String cobolName) {
        List<JclCandidate> candidates = new ArrayList<>();
        
        // Strategy 1: Exact PGM=COBOLNAME match
        candidates.addAll(strategyExactProgramName(cobolName));
        
        // Strategy 2: Naming convention match (e.g., COBOLNAME.jcl or COB_COBOLNAME.jcl)
        candidates.addAll(strategyNamingConvention(cobolName));
        
        // Strategy 3: Job name match (if job name is related to program name)
        candidates.addAll(strategyJobNameMatch(cobolName));
        
        // Strategy 4: Heuristic matching (partial matches in step names)
        candidates.addAll(strategyHeuristic(cobolName));
        
        // Sort by confidence score
        candidates.sort((a, b) -> Float.compare(b.confidence, a.confidence));
        
        if (candidates.isEmpty()) {
            return JclMatchResult.builder()
                    .cobolName(cobolName)
                    .found(false)
                    .confidence(0.0f)
                    .orphaned(true)
                    .build();
        }
        
        JclCandidate best = candidates.get(0);
        List<String> alternates = candidates.stream()
                .skip(1)
                .limit(3)
                .map(JclCandidate::getJclName)
                .collect(Collectors.toList());
        
        return JclMatchResult.builder()
                .cobolName(cobolName)
                .found(true)
                .jclName(best.jclName)
                .matchStrategy(best.strategy)
                .confidence(best.confidence)
                .jobName(best.jobName)
                .alternateJcls(alternates)
                .orphaned(false)
                .build();
    }
    
    /**
     * Strategy 1: Look for PGM=COBOLNAME in JCL content
     */
    private List<JclCandidate> strategyExactProgramName(String cobolName) {
        List<JclCandidate> result = new ArrayList<>();
        Pattern pgmPattern = Pattern.compile("PGM\\s*=\\s*" + cobolName + "\\b", Pattern.CASE_INSENSITIVE);
        
        for (Map.Entry<String, String> entry : jclContent.entrySet()) {
            if (pgmPattern.matcher(entry.getValue()).find()) {
                String jobName = extractJobName(entry.getValue());
                result.add(JclCandidate.builder()
                        .jclName(entry.getKey())
                        .strategy("program_name")
                        .confidence(0.95f)
                        .jobName(jobName)
                        .build());
            }
        }
        
        return result;
    }
    
    /**
     * Strategy 2: Match by naming convention
     */
    private List<JclCandidate> strategyNamingConvention(String cobolName) {
        List<JclCandidate> result = new ArrayList<>();
        
        for (String jclName : jclContent.keySet()) {
            String jclBaseName = jclName.replaceAll("\\.jcl$", "");
            
            // Check if JCL name matches or is related to COBOL name
            if (jclBaseName.equalsIgnoreCase(cobolName) ||
                jclBaseName.contains(cobolName) ||
                cobolName.contains(jclBaseName)) {
                
                String jobName = extractJobName(jclContent.get(jclName));
                result.add(JclCandidate.builder()
                        .jclName(jclName)
                        .strategy("naming_convention")
                        .confidence(0.7f)
                        .jobName(jobName)
                        .build());
            }
        }
        
        return result;
    }
    
    /**
     * Strategy 3: Match by JOB name if it relates to program name
     */
    private List<JclCandidate> strategyJobNameMatch(String cobolName) {
        List<JclCandidate> result = new ArrayList<>();
        Pattern jobPattern = Pattern.compile("//\\s*(\\w+)\\s+JOB", Pattern.CASE_INSENSITIVE);
        
        for (Map.Entry<String, String> entry : jclContent.entrySet()) {
            Matcher m = jobPattern.matcher(entry.getValue());
            if (m.find()) {
                String jobName = m.group(1);
                if (jobName.contains(cobolName.substring(0, Math.min(4, cobolName.length())))) {
                    result.add(JclCandidate.builder()
                            .jclName(entry.getKey())
                            .strategy("job_name")
                            .confidence(0.6f)
                            .jobName(jobName)
                            .build());
                }
            }
        }
        
        return result;
    }
    
    /**
     * Strategy 4: Heuristic matching (look for program name in step names)
     */
    private List<JclCandidate> strategyHeuristic(String cobolName) {
        List<JclCandidate> result = new ArrayList<>();
        
        for (Map.Entry<String, String> entry : jclContent.entrySet()) {
            String content = entry.getValue().toUpperCase();
            if (content.contains(cobolName.toUpperCase())) {
                String jobName = extractJobName(entry.getValue());
                result.add(JclCandidate.builder()
                        .jclName(entry.getKey())
                        .strategy("heuristic")
                        .confidence(0.5f)
                        .jobName(jobName)
                        .build());
            }
        }
        
        return result;
    }
    
    /**
     * Extract JOB name from JCL content
     */
    private String extractJobName(String jclContent) {
        Pattern jobPattern = Pattern.compile("//\\s*(\\w+)\\s+JOB");
        Matcher m = jobPattern.matcher(jclContent);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }
}
