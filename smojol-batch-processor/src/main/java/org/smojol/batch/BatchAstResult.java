package org.smojol.batch;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Result of batch AST generation
 */
@Data
@Builder
public class BatchAstResult {
    @JsonProperty("total_files")
    private int totalFiles;
    
    @JsonProperty("success_count")
    private int successCount;
    
    @JsonProperty("failure_count")
    private int failureCount;
    
    @JsonProperty("skip_count")
    private int skipCount;
    
    @JsonProperty("success_rate_percent")
    public double getSuccessRate() {
        return totalFiles > 0 ? (successCount * 100.0 / totalFiles) : 0.0;
    }
    
    @JsonProperty("total_time_ms")
    private long totalTimeMs;
    
    @JsonProperty("results")
    private List<AstGenerationResult> results;
}
