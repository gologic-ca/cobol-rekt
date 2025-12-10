package org.smojol.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.smojol.jcl.JclAnalysisTask;
import org.smojol.jcl.JclParserService;
import org.smojol.jcl.model.JclParseResult;
import org.smojol.jcl.model.JclParsingException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

/**
 * CLI command for parsing JCL files.
 */
@Command(name = "parse-jcl",
        mixinStandardHelpOptions = true,
        description = "Parse JCL files and generate JSON output",
        version = "1.0")
public class JclParseCommand implements Callable<Integer> {

    private static final Logger LOGGER = Logger.getLogger(JclParseCommand.class.getName());

    @Parameters(index = "0", description = "Path to JCL file or directory containing JCL files")
    private String inputPath;

    @Option(names = {"-o", "--output"},
            description = "Output directory for JSON files (default: ./out/jcl)")
    private String outputDir = "./out/jcl";

    @Option(names = {"-r", "--recursive"},
            description = "Recursively search for JCL files in subdirectories")
    private boolean recursive = false;

    @Option(names = {"-p", "--python"},
            description = "Path to Python executable (default: python3)")
    private String pythonExecutable = "python3";

    @Option(names = {"--batch"},
            description = "Batch mode: process all JCL files in directory")
    private boolean batchMode = false;

    @Option(names = {"--check-env"},
            description = "Check if Python environment is properly configured")
    private boolean checkEnvironment = false;

    @Option(names = {"--pretty"},
            description = "Pretty print JSON output to console")
    private boolean prettyPrint = false;

    @Override
    public Integer call() {
        try {
            LOGGER.info("Starting JCL parsing...");

            // Initialize parser service
            JclParserService parserService = new JclParserService(pythonExecutable);

            // Check environment if requested
            if (checkEnvironment) {
                boolean envOk = parserService.checkEnvironment();
                if (envOk) {
                    System.out.println("✓ Python environment is properly configured");
                    System.out.println("✓ legacylens-jcl-parser library is installed");
                    return 0;
                } else {
                    System.err.println("✗ Python environment is NOT properly configured");
                    System.err.println("Please ensure:");
                    System.err.println("  1. Python 3 is installed and accessible");
                    System.err.println("  2. Install the parser: pip install legacylens-jcl-parser");
                    return 1;
                }
            }

            // Create analysis task
            JclAnalysisTask analysisTask = new JclAnalysisTask(parserService);
            Path input = Paths.get(inputPath);
            Path output = Paths.get(outputDir);

            // Batch mode or single file mode
            if (batchMode) {
                LOGGER.info("Running in batch mode");
                List<Path> parsed = analysisTask.analyzeBatch(input, output, recursive);

                System.out.println("\n=== JCL Batch Analysis Results ===");
                System.out.println("Successfully parsed " + parsed.size() + " file(s)");
                System.out.println("Output directory: " + output.toAbsolutePath());

                return 0;

            } else {
                // Single file mode
                LOGGER.info("Parsing single JCL file: " + input);

                JclParseResult result = parserService.parseJclFile(input);

                if (result.isSuccess()) {
                    // Export to file
                    String fileName = input.getFileName().toString().replaceAll("\\.(jcl|JCL)$", ".json");
                    Path outputPath = output.resolve(fileName);
                    analysisTask.analyzeAndExport(input, outputPath);

                    System.out.println("\n=== JCL Parsing Success ===");
                    System.out.println("Input:  " + input.toAbsolutePath());
                    System.out.println("Output: " + outputPath.toAbsolutePath());

                    // Print to console if requested
                    if (prettyPrint) {
                        Gson gson = new GsonBuilder().setPrettyPrinting().create();
                        System.out.println("\n=== Parsed JCL Structure ===");
                        System.out.println(gson.toJson(result.getJcl()));
                    }

                    return 0;

                } else {
                    System.err.println("\n=== JCL Parsing Failed ===");
                    System.err.println(result.getErrorDetails());
                    return 1;
                }
            }

        } catch (JclParsingException e) {
            LOGGER.severe("JCL parsing failed: " + e.getMessage());
            System.err.println("\n✗ Error: " + e.getMessage());

            if (e.getMessage().contains("Python")) {
                System.err.println("\nTroubleshooting:");
                System.err.println("  1. Verify Python is installed: python3 --version");
                System.err.println("  2. Install parser: pip install legacylens-jcl-parser");
                System.err.println("  3. Use --check-env to verify installation");
            }

            return 1;

        } catch (Exception e) {
            LOGGER.severe("Unexpected error: " + e.getMessage());
            System.err.println("\n✗ Unexpected error: " + e.getMessage());
            return 1;
        }
    }
}
