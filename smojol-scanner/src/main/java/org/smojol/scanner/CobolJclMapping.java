package org.smojol.scanner;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Builder;
import lombok.Data;

/**
 * Represents a mapping between a COBOL program and its associated JCL file(s)
 */
@Data
@Builder
public class CobolJclMapping {
    @JsonProperty("cobol_name")
    private String cobolName;
    
    @JsonProperty("cobol_path")
    private String cobolPath;
    
    @JsonProperty("jcl_name")
    private String jclName;
    
    @JsonProperty("jcl_path")
    private String jclPath;
    
    @JsonProperty("match_strategy")
    private String matchStrategy;  // "program_name", "pgm_parameter", "naming_convention", "heuristic"
    
    @JsonProperty("job_name")
    private String jobName;
    
    @JsonProperty("confidence")
    private float confidence;  // 0.0 to 1.0
    
    @JsonProperty("is_orphaned")
    private boolean orphaned;  // No JCL found
    
    @JsonProperty("alternate_jcls")
    private java.util.List<String> alternateJcls;  // Other JCLs that could match
}
