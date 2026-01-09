package org.smojol.scanner;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.util.Date;
import java.util.List;

/**
 * Report of the project structure after scanning
 */
@Data
@Builder
public class ProjectStructure {
    @JsonProperty("project_root")
    private String projectRoot;
    
    @JsonProperty("total_cobol_programs")
    private int totalCobolPrograms;
    
    @JsonProperty("total_jcl_jobs")
    private int totalJclJobs;
    
    @JsonProperty("total_copybooks")
    private int totalCopybooks;
    
    @JsonProperty("cobol_files")
    private List<String> cobolFiles;
    
    @JsonProperty("jcl_files")
    private List<String> jclFiles;
    
    @JsonProperty("copybook_files")
    private List<String> copybookFiles;
    
    @JsonProperty("scan_time")
    private Date scanTime;
}
