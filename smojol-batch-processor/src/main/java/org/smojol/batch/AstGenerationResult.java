package org.smojol.batch;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.util.Date;

/**
 * Result of processing a single COBOL file
 */
@Data
@Builder
public class AstGenerationResult {
    @JsonProperty("cobol_name")
    private String cobolName;
    
    @JsonProperty("status")
    private String status;  // "success", "failed", "skipped"
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("json_file")
    private String jsonFile;
    
    @JsonProperty("file_size_bytes")
    private long fileSizeBytes;
    
    @JsonProperty("generation_time_ms")
    private long generationTimeMs;
    
    @JsonProperty("timestamp")
    private Date timestamp;
    
    @JsonProperty("error")
    private String error;
}
