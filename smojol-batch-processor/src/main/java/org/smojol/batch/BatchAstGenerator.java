package org.smojol.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates ASTs for multiple COBOL files in parallel
 */
@Slf4j
public class BatchAstGenerator {
    private final Path cobolDir;
    private final Path copyDir;
    private final Path outputDir;
    private final Path cliJar;
    private final int threadCount;
    private final ObjectMapper mapper = new ObjectMapper();
    
    public BatchAstGenerator(Path cobolDir, Path copyDir, Path outputDir, Path cliJar) {
        this(cobolDir, copyDir, outputDir, cliJar, Runtime.getRuntime().availableProcessors());
    }
    
    public BatchAstGenerator(Path cobolDir, Path copyDir, Path outputDir, Path cliJar, int threadCount) {
        this.cobolDir = cobolDir;
        this.copyDir = copyDir;
        this.outputDir = outputDir;
        this.cliJar = cliJar;
        this.threadCount = threadCount;
    }
    
    /**
     * Generate ASTs for all COBOL files
     */
    public BatchAstResult generateAllAsts() throws java.io.IOException {
        log.info("Starting batch AST generation with {} threads", threadCount);
        long startTime = System.currentTimeMillis();
        
        // Find all COBOL files
        List<Path> cobolFiles = findCobolFiles();
        if (cobolFiles.isEmpty()) {
            log.warn("No COBOL files found in: {}", cobolDir);
            return BatchAstResult.builder()
                    .totalFiles(0)
                    .successCount(0)
                    .failureCount(0)
                    .skipCount(0)
                    .totalTimeMs(0)
                    .results(new ArrayList<>())
                    .build();
        }
        
        log.info("Found {} COBOL files to process", cobolFiles.size());
        
        // Create output directory
        Files.createDirectories(outputDir);
        
        // Process in parallel
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<AstGenerationResult>> futures = new ArrayList<>();
        AtomicInteger processed = new AtomicInteger(0);
        
        try {
            for (Path cobolFile : cobolFiles) {
                Future<AstGenerationResult> future = executor.submit(() -> {
                    int current = processed.incrementAndGet();
                    log.info("[{}/{}] Processing: {}", current, cobolFiles.size(), cobolFile.getFileName());
                    return generateAst(cobolFile);
                });
                futures.add(future);
            }
            
            // Collect results
            List<AstGenerationResult> results = new ArrayList<>();
            int successCount = 0;
            int failureCount = 0;
            int skipCount = 0;
            
            for (Future<AstGenerationResult> future : futures) {
                try {
                    AstGenerationResult result = future.get(5, TimeUnit.MINUTES);
                    results.add(result);
                    
                    switch (result.getStatus()) {
                        case "success" -> successCount++;
                        case "failed" -> failureCount++;
                        case "skipped" -> skipCount++;
                    }
                } catch (TimeoutException e) {
                    failureCount++;
                    log.error("Timeout processing file", e);
                } catch (Exception e) {
                    failureCount++;
                    log.error("Error processing file", e);
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            
            BatchAstResult result = BatchAstResult.builder()
                    .totalFiles(cobolFiles.size())
                    .successCount(successCount)
                    .failureCount(failureCount)
                    .skipCount(skipCount)
                    .totalTimeMs(duration)
                    .results(results)
                    .build();
            
            log.info("Batch processing complete: {} success, {} failed, {} skipped in {}ms",
                    successCount, failureCount, skipCount, duration);
            
            return result;
            
        } finally {
            executor.shutdownNow();
        }
    }
    
    /**
     * Generate AST for a single COBOL file
     */
    private AstGenerationResult generateAst(Path cobolFile) {
        String cobolName = cobolFile.getFileName().toString()
                .replaceAll("\\.cbl$", "").toUpperCase();
        long startTime = System.currentTimeMillis();
        
        try {
            Path reportDir = outputDir.resolve(cobolName + ".cbl.report");
            
            // Build command
            ProcessBuilder pb = new ProcessBuilder(
                    "java", "-jar", cliJar.toString(), "run",
                    "-c", "WRITE_AGGREGATED_JCL_AST",
                    "-s", cobolDir.toString(),
                    "-cp", copyDir.toString(),
                    "-r", reportDir.toString(),
                    cobolFile.getFileName().toString()
            );
            pb.directory(cobolDir.toFile());
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            int exitCode = process.waitFor();
            
            long duration = System.currentTimeMillis() - startTime;
            
            if (exitCode == 0) {
                Path jsonFile = reportDir.resolve("ast/aggregated/" + cobolName + "-aggregated.json");
                if (Files.exists(jsonFile)) {
                    long fileSize = Files.size(jsonFile);
                    return AstGenerationResult.builder()
                            .cobolName(cobolName)
                            .status("success")
                            .message("AST generated successfully")
                            .jsonFile(jsonFile.toString())
                            .fileSizeBytes(fileSize)
                            .generationTimeMs(duration)
                            .timestamp(new Date())
                            .build();
                } else {
                    return AstGenerationResult.builder()
                            .cobolName(cobolName)
                            .status("failed")
                            .message("JSON file not generated")
                            .generationTimeMs(duration)
                            .timestamp(new Date())
                            .error("Output file not found at: " + jsonFile)
                            .build();
                }
            } else {
                return AstGenerationResult.builder()
                        .cobolName(cobolName)
                        .status("failed")
                        .message("Process exited with code: " + exitCode)
                        .generationTimeMs(duration)
                        .timestamp(new Date())
                        .error("Exit code: " + exitCode)
                        .build();
            }
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Error generating AST for {}: {}", cobolName, e.getMessage());
            
            return AstGenerationResult.builder()
                    .cobolName(cobolName)
                    .status("failed")
                    .message(e.getClass().getSimpleName() + ": " + e.getMessage())
                    .generationTimeMs(duration)
                    .timestamp(new Date())
                    .error(e.getMessage())
                    .build();
        }
    }
    
    /**
     * Find all COBOL files in the directory
     */
    private List<Path> findCobolFiles() throws java.io.IOException {
        if (!Files.exists(cobolDir)) {
            log.warn("COBOL directory not found: {}", cobolDir);
            return new ArrayList<>();
        }
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(cobolDir, "*.cbl")) {
            return StreamSupport.stream(stream.spliterator(), false)
                    .sorted(Comparator.comparing(Path::getFileName))
                    .collect(Collectors.toList());
        }
    }
}
