package org.smojol.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CLI entry point for batch AST generation
 * 
 * Usage:
 *   java -cp ... org.smojol.batch.BatchProcessorCli <cobol_dir> <cpy_dir> <cli_jar> [threads] [output_dir]
 */
public class BatchProcessorCli {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: java org.smojol.batch.BatchProcessorCli <cobol_dir> <cpy_dir> <cli_jar> [threads] [output_dir]");
            System.err.println();
            System.err.println("Example:");
            System.err.println("  java org.smojol.batch.BatchProcessorCli \\");
            System.err.println("    /path/to/cbl /path/to/cpy ./smojol-cli/target/smojol-cli.jar 4 ./out");
            System.exit(1);
        }
        
        Path cobolDir = Paths.get(args[0]);
        Path cpyDir = Paths.get(args[1]);
        Path cliJar = Paths.get(args[2]);
        int threads = args.length > 3 ? Integer.parseInt(args[3]) : Runtime.getRuntime().availableProcessors();
        Path outputDir = args.length > 4 ? Paths.get(args[4]) : Paths.get("./out");
        
        // Validate
        if (!Files.isDirectory(cobolDir)) {
            System.err.println("ERROR: COBOL directory not found: " + cobolDir);
            System.exit(1);
        }
        if (!Files.isDirectory(cpyDir)) {
            System.err.println("ERROR: Copybook directory not found: " + cpyDir);
            System.exit(1);
        }
        if (!Files.exists(cliJar)) {
            System.err.println("ERROR: CLI JAR not found: " + cliJar);
            System.exit(1);
        }
        
        // Create output directory
        Files.createDirectories(outputDir);
        
        System.out.println("╔═══════════════════════════════════════════════════════╗");
        System.out.println("║        SMOJOL Batch AST Generator                     ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  COBOL dir:  " + cobolDir);
        System.out.println("  CPY dir:    " + cpyDir);
        System.out.println("  CLI JAR:    " + cliJar);
        System.out.println("  Threads:    " + threads);
        System.out.println("  Output:     " + outputDir);
        System.out.println();
        
        try {
            BatchAstGenerator generator = new BatchAstGenerator(cobolDir, cpyDir, outputDir, cliJar, threads);
            BatchAstResult result = generator.generateAllAsts();
            
            // Export results
            Path resultsFile = outputDir.resolve("batch-ast-results.json");
            ObjectMapper mapper = new ObjectMapper();
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(resultsFile.toFile(), result);
            
            // Export index
            AstIndexBuilder indexBuilder = new AstIndexBuilder(outputDir);
            AstIndex index = indexBuilder.buildIndex();
            Path indexFile = outputDir.resolve("ast-index.json");
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(indexFile.toFile(), index);
            
            System.out.println("✓ Batch generation complete!");
            System.out.println();
            System.out.println("Results:");
            System.out.println(String.format("  - Total files:     %d", result.getTotalFiles()));
            System.out.println(String.format("  - Successful:      %d (%.1f%%)", result.getSuccessCount(), result.getSuccessRate()));
            System.out.println(String.format("  - Failed:          %d", result.getFailureCount()));
            System.out.println(String.format("  - Skipped:         %d", result.getSkipCount()));
            System.out.println(String.format("  - Total time:      %dms (%.2fs)", result.getTotalTimeMs(), result.getTotalTimeMs() / 1000.0));
            System.out.println();
            System.out.println("Output files:");
            System.out.println("  - Results:   " + resultsFile);
            System.out.println("  - Index:     " + indexFile);
            System.out.println();
            
            // Exit with error if there were failures
            if (result.getFailureCount() > 0) {
                System.exit(1);
            }
            
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
