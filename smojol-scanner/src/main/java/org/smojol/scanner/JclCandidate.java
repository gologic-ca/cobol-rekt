package org.smojol.scanner;

import lombok.Builder;
import lombok.Data;

/**
 * Internal candidate for JCL matching
 */
@Data
@Builder
class JclCandidate {
    String jclName;
    String strategy;
    float confidence;
    String jobName;
}
