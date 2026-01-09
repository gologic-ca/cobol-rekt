package org.smojol.batch;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates an index of all generated ASTs for quick lookup
 */
@Slf4j
public class AstIndexBuilder {
    private final Path reportDir;
    private final ObjectMapper mapper = new ObjectMapper();
    
    public AstIndexBuilder(Path reportDir) {
        this.reportDir = reportDir;
    }
    
    /**
     * Build index of all ASTs in report directory
     */
    public AstIndex buildIndex() throws java.io.IOException {
        log.info("Building AST index from: {}", reportDir);
        long startTime = System.currentTimeMillis();
        
        List<AstIndexEntry> entries = new ArrayList<>();
        
        // Find all aggregated JSON files
        List<Path> jsonFiles;
        try {
            jsonFiles = Files.walk(reportDir)
                    .filter(p -> p.getFileName().toString().endsWith("-aggregated.json"))
                    .collect(Collectors.toList());
        } catch (java.io.IOException e) {
            log.error("Error walking report directory: {}", reportDir, e);
            jsonFiles = new ArrayList<>();
        }
        
        log.info("Found {} AST files", jsonFiles.size());
        
        for (Path jsonFile : jsonFiles) {
            try {
                String cobolName = jsonFile.getFileName().toString()
                        .replaceAll("-aggregated\\.json$", "");
                long fileSize = Files.size(jsonFile);
                
                // Read first few bytes to extract metadata
                JsonNode root = mapper.readTree(jsonFile.toFile());
                String nodeType = root.has("nodeType") ? root.get("nodeType").asText() : "unknown";
                
                List<String> copybooks = new ArrayList<>();
                if (root.has("copybooks") && root.get("copybooks").isArray()) {
                    root.get("copybooks").forEach(cb -> copybooks.add(cb.asText()));
                }
                
                entries.add(AstIndexEntry.builder()
                        .cobolName(cobolName)
                        .jsonPath(jsonFile.toString())
                        .fileSizeBytes(fileSize)
                        .nodeType(nodeType)
                        .copybooks(copybooks)
                        .build());
                
            } catch (Exception e) {
                log.warn("Error indexing {}: {}", jsonFile, e.getMessage());
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        AstIndex index = AstIndex.builder()
                .totalEntries(entries.size())
                .entries(entries)
                .indexTimeMs(duration)
                .build();
        
        log.info("Index built in {}ms with {} entries", duration, entries.size());
        return index;
    }
    
    /**
     * Export index to JSON
     */
    public void exportIndex(AstIndex index, Path outputFile) throws java.io.IOException {
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(outputFile.toFile(), index);
        log.info("Index exported to: {}", outputFile);
    }
}

@lombok.Data
@lombok.Builder
class AstIndexEntry {
    @JsonProperty("cobol_name")
    private String cobolName;
    
    @JsonProperty("json_path")
    private String jsonPath;
    
    @JsonProperty("file_size_bytes")
    private long fileSizeBytes;
    
    @JsonProperty("node_type")
    private String nodeType;
    
    @JsonProperty("copybooks")
    private List<String> copybooks;
}

@lombok.Data
@lombok.Builder
class AstIndex {
    @JsonProperty("total_entries")
    private int totalEntries;
    
    @JsonProperty("entries")
    private List<AstIndexEntry> entries;
    
    @JsonProperty("index_time_ms")
    private long indexTimeMs;
}
