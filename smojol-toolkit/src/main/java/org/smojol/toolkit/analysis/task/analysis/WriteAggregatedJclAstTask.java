package org.smojol.toolkit.analysis.task.analysis;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojo.algorithms.task.AnalysisTask;
import com.mojo.algorithms.task.AnalysisTaskResult;
import org.antlr.v4.runtime.tree.ParseTree;
import org.smojol.common.ast.BuildSerialisableASTTask;
import org.smojol.common.ast.CobolContextAugmentedTreeNode;
import org.smojol.common.flowchart.ConsoleColors;
import org.smojol.common.navigation.CobolEntityNavigator;
import org.smojol.common.resource.ResourceOperations;
import org.smojol.jcl.JclParserService;
import org.smojol.jcl.model.JclParseResult;
import org.smojol.jcl.model.JclParsingException;
import org.smojol.toolkit.analysis.pipeline.config.RawASTOutputConfig;
import org.smojol.toolkit.analysis.pipeline.config.SourceConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Task to write an aggregated JSON containing both COBOL AST and JCL execution context.
 * This task searches for corresponding JCL files and merges them with the COBOL AST.
 */
public class WriteAggregatedJclAstTask implements AnalysisTask {
    private static final Logger LOGGER = Logger.getLogger(WriteAggregatedJclAstTask.class.getName());
    
    private final ParseTree tree;
    private final CobolEntityNavigator navigator;
    private final RawASTOutputConfig rawAstOutputConfig;
    private final ResourceOperations resourceOperations;
    private final Object copybooksRepository;
    private final String jclDirectory;
    private final SourceConfig sourceConfig;
    private final Gson gson;

    public WriteAggregatedJclAstTask(
            CobolEntityNavigator navigator,
            RawASTOutputConfig rawAstOutputConfig,
            ResourceOperations resourceOperations,
            Object copybooksRepository,
            String jclDirectory,
            SourceConfig sourceConfig) {
        this.tree = navigator.getRoot();
        this.navigator = navigator;
        this.rawAstOutputConfig = rawAstOutputConfig;
        this.resourceOperations = resourceOperations;
        this.copybooksRepository = copybooksRepository;
        this.jclDirectory = jclDirectory;
        this.sourceConfig = sourceConfig;
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    @Override
    public AnalysisTaskResult run() {
        try {
            LOGGER.info(ConsoleColors.green("Creating aggregated JCL+AST output..."));
            
            // Get program name from sourceConfig
            String programName = sourceConfig.programName();
            if (programName.endsWith(".cbl")) {
                programName = programName.substring(0, programName.lastIndexOf(".cbl"));
            }
            
            // Build the serialisable AST
            CobolContextAugmentedTreeNode serialisableAST = new BuildSerialisableASTTask().run(tree, navigator, copybooksRepository);
            
            // First, write the regular AST using the existing visualiser
            resourceOperations.createDirectories(rawAstOutputConfig.astOutputDir());
            rawAstOutputConfig.visualiser().writeCobolAST(serialisableAST, rawAstOutputConfig.cobolParseTreeOutputPath(), false);
            
            // Read the generated JSON file
            Path astJsonPath = Paths.get(rawAstOutputConfig.cobolParseTreeOutputPath());
            String astJsonContent = Files.readString(astJsonPath);
            JsonObject aggregatedJson = gson.fromJson(astJsonContent, JsonObject.class);
            
            // Try to find and parse corresponding JCL file
            JsonObject jclContext = findAndParseJclFile(programName);
            
            // Add JCL execution context if found
            if (jclContext != null && !jclContext.entrySet().isEmpty()) {
                aggregatedJson.add("jclExecutionContext", jclContext);
                LOGGER.info(ConsoleColors.green("✓ JCL context successfully merged with AST"));
            } else {
                LOGGER.warning("⚠ No JCL file found for program " + programName);
            }
            
            // Write aggregated JSON
            Path outputDir = rawAstOutputConfig.astOutputDir().resolve("aggregated");
            resourceOperations.createDirectories(outputDir);
            
            Path outputPath = outputDir.resolve(programName + "-aggregated.json");
            
            String jsonOutput = gson.toJson(aggregatedJson);
            Files.writeString(outputPath, jsonOutput);
            
            LOGGER.info(ConsoleColors.green(String.format("✓ Aggregated AST+JCL written to: %s", outputPath)));
            LOGGER.info(String.format("  File size: %,d bytes", Files.size(outputPath)));
            
            return AnalysisTaskResult.OK("WRITE_AGGREGATED_JCL_AST", aggregatedJson);
            
        } catch (IOException e) {
            LOGGER.severe("Error writing aggregated JCL+AST: " + e.getMessage());
            return AnalysisTaskResult.ERROR(e, "WRITE_AGGREGATED_JCL_AST");
        }
    }

    /**
     * Find and parse the JCL file corresponding to the COBOL program.
     * Searches for .jcl files in the specified JCL directory.
     *
     * @param programName the COBOL program name
     * @return JsonObject containing JCL context with file mappings, or empty object if not found
     */
    private JsonObject findAndParseJclFile(String programName) {
        JsonObject emptyContext = new JsonObject();
        
        if (jclDirectory == null || jclDirectory.isEmpty()) {
            LOGGER.fine("No JCL directory specified, skipping JCL parsing");
            return emptyContext;
        }
        
        Path jclDir = Paths.get(jclDirectory);
        if (!Files.exists(jclDir) || !Files.isDirectory(jclDir)) {
            LOGGER.warning("JCL directory does not exist: " + jclDirectory);
            return emptyContext;
        }
        
        try {
            // Search for JCL file matching the program name
            Path jclFile = findJclFile(jclDir, programName);
            
            if (jclFile == null) {
                LOGGER.fine("No JCL file found for program: " + programName);
                return emptyContext;
            }
            
            LOGGER.info("Found JCL file: " + jclFile);
            
            // Parse JCL file
            JclParserService jclParser = new JclParserService();
            JclParseResult parseResult = jclParser.parseJclFile(jclFile);

            if (parseResult == null || parseResult.getJcl() == null) {
                LOGGER.warning("Failed to parse JCL file: " + jclFile);
                return emptyContext;
            }
            
            // Build JCL context with file mappings
            return buildJclContextWithMappings(parseResult, navigator);
            
        } catch (JclParsingException e) {
            LOGGER.warning("Error parsing JCL file: " + e.getMessage());
            return emptyContext;
        } catch (Exception e) {
            LOGGER.warning("Unexpected error processing JCL: " + e.getMessage());
            return emptyContext;
        }
    }

    /**
     * Find JCL file in directory matching the program name.
     * Tries exact match first, then fuzzy matching.
     */
    private Path findJclFile(Path jclDir, String programName) throws IOException {
        // Try exact match first (e.g., CBIMPORT.jcl for program CBIMPORT)
        Path exactMatch = jclDir.resolve(programName + ".jcl");
        if (Files.exists(exactMatch)) {
            return exactMatch;
        }
        
        // Try case-insensitive search
        try (Stream<Path> files = Files.list(jclDir)) {
            return files
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jcl"))
                    .filter(p -> {
                        String fileName = p.getFileName().toString();
                        String baseName = fileName.substring(0, fileName.length() - 4);
                        return baseName.equalsIgnoreCase(programName);
                    })
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * Build JCL execution context with DD-to-COBOL file mappings.
     */
    private JsonObject buildJclContextWithMappings(JclParseResult jclResult, CobolEntityNavigator navigator) {
        JsonObject context = new JsonObject();
        
        try {
            // Convert JCL result to JSON
            JsonElement jclJson = gson.toJsonTree(jclResult);
            
            if (!jclJson.isJsonObject()) {
                LOGGER.warning("JCL result is not a JSON object");
                return context;
            }
            
            JsonObject jclObject = jclJson.getAsJsonObject();
            
            // Extract job and steps
            context.addProperty("nodeType", "JCLExecutionContext");
            context.addProperty("status", "success");
            
            // The jclResult structure wraps the actual JCL data in a "jcl" property
            if (jclObject.has("jcl")) {
                JsonObject jclData = jclObject.getAsJsonObject("jcl");
                
                // Copy sourceFile if present
                if (jclData.has("sourceFile")) {
                    context.add("sourceFile", jclData.get("sourceFile"));
                }
                
                // Add job information
                if (jclData.has("job")) {
                    JsonObject job = jclData.getAsJsonObject("job");
                    enhanceJobWithContext(job);
                    context.add("job", job);
                }
                
                // Add steps information
                if (jclData.has("steps")) {
                    JsonElement stepsElement = jclData.get("steps");
                    if (stepsElement.isJsonArray()) {
                        // Enhance steps with COBOL file mappings
                        enhanceStepsWithCobolMappings(stepsElement, navigator);
                        context.add("steps", stepsElement);
                    }
                }
            } else if (jclObject.has("job") || jclObject.has("steps")) {
                // Fallback: if jcl property is missing, try to use job/steps directly
                // This handles cases where the structure might be different
                if (jclObject.has("job")) {
                    JsonObject job = jclObject.getAsJsonObject("job");
                    enhanceJobWithContext(job);
                    context.add("job", job);
                }
                
                if (jclObject.has("steps")) {
                    JsonElement stepsElement = jclObject.get("steps");
                    if (stepsElement.isJsonArray()) {
                        enhanceStepsWithCobolMappings(stepsElement, navigator);
                        context.add("steps", stepsElement);
                    }
                }
            }
            
        } catch (Exception e) {
            LOGGER.warning("Error building JCL context: " + e.getMessage());
        }
        
        return context;
    }

    /**
     * Enhance job node with proper nodeType
     */
    private void enhanceJobWithContext(JsonObject job) {
        if (!job.has("nodeType")) {
            job.addProperty("nodeType", "JCLJobContext");
        }
    }

    /**
     * Enhance JCL steps with COBOL file mappings from SELECT statements.
     */
    private void enhanceStepsWithCobolMappings(JsonElement stepsElement, CobolEntityNavigator navigator) {
        // This would require parsing the COBOL source to extract SELECT statements
        // For now, we'll add the basic structure and let the mapping be done externally
        
        // TODO: Extract SELECT statements from COBOL AST and map to DD statements
        // This requires navigating the AST to find FILE-CONTROL section
        
        LOGGER.fine("JCL steps structure preserved (detailed mapping requires AST analysis)");
    }
}
