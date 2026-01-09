package org.smojol.scanner;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CLI entry point for project scanning and analysis
 * 
 * Usage:
 *   java -cp ... org.smojol.scanner.ScannerCli <cobol_dir> <jcl_dir> <cpy_dir> <output_dir>
 */
public class ScannerCli {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: java org.smojol.scanner.ScannerCli <cobol_dir> <jcl_dir> <cpy_dir> <output_dir>");
            System.err.println();
            System.err.println("Example:");
            System.err.println("  java org.smojol.scanner.ScannerCli \\");
            System.err.println("    /path/to/cbl /path/to/jcl /path/to/cpy ./out");
            System.exit(1);
        }
        
        Path cobolDir = Paths.get(args[0]);
        Path jclDir = Paths.get(args[1]);
        Path cpyDir = Paths.get(args[2]);
        Path outputDir = Paths.get(args[3]);
        
        // Validate directories
        if (!Files.isDirectory(cobolDir)) {
            System.err.println("ERROR: COBOL directory not found: " + cobolDir);
            System.exit(1);
        }
        if (!Files.isDirectory(jclDir)) {
            System.err.println("ERROR: JCL directory not found: " + jclDir);
            System.exit(1);
        }
        if (!Files.isDirectory(cpyDir)) {
            System.err.println("ERROR: Copybook directory not found: " + cpyDir);
            System.exit(1);
        }
        
        // Create output directory
        Files.createDirectories(outputDir);
        
        System.out.println("╔═══════════════════════════════════════════════════════╗");
        System.out.println("║        SMOJOL Project Scanner & Analyzer              ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝");
        System.out.println();
        
        try {
            ProjectAnalyzer analyzer = new ProjectAnalyzer(cobolDir, jclDir, cpyDir);
            ProjectAnalysisResult result = analyzer.analyzeProject();
            
            // Export results
            Path outputFile = outputDir.resolve("project-analysis.json");
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(outputFile.toFile(), result);
            
            System.out.println("✓ Analysis complete!");
            System.out.println();
            System.out.println("Results:");
            System.out.println("  - Total COBOL programs:   " + result.getProjectStructure().getTotalCobolPrograms());
            System.out.println("  - Total JCL jobs:         " + result.getProjectStructure().getTotalJclJobs());
            System.out.println("  - Successful mappings:    " + result.getSuccessfulMappings());
            System.out.println("  - Orphaned programs:      " + result.getOrphanedPrograms());
            System.out.println("  - Analysis time:          " + result.getAnalysisTimeMs() + "ms");
            System.out.println();
            System.out.println("Output file: " + outputFile);
            System.out.println();
            
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
