package org.smojol.scanner;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Result of JCL matching for a COBOL program
 */
@Data
@Builder
public class JclMatchResult {
    @JsonProperty("cobol_name")
    private String cobolName;
    
    @JsonProperty("found")
    private boolean found;
    
    @JsonProperty("jcl_name")
    private String jclName;
    
    @JsonProperty("match_strategy")
    private String matchStrategy;  // "program_name", "naming_convention", "job_name", "heuristic"
    
    @JsonProperty("confidence")
    private float confidence;  // 0.0 to 1.0
    
    @JsonProperty("job_name")
    private String jobName;
    
    @JsonProperty("alternate_jcls")
    private List<String> alternateJcls;
    
    @JsonProperty("orphaned")
    private boolean orphaned;
}
