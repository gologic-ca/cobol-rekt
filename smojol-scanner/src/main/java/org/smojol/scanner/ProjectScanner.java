package org.smojol.scanner;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Scans a project directory to find all COBOL files and their associated JCL files
 */
@Slf4j
public class ProjectScanner {
    private final Path projectRoot;
    private final Path cobolDir;
    private final Path jclDir;
    private final Path copyDir;
    
    public ProjectScanner(Path projectRoot, Path cobolDir, Path jclDir, Path copyDir) {
        this.projectRoot = projectRoot;
        this.cobolDir = cobolDir;
        this.jclDir = jclDir;
        this.copyDir = copyDir;
    }
    
    /**
     * Find all COBOL files in the project
     */
    public List<Path> findCobolFiles() throws java.io.IOException {
        if (!Files.exists(cobolDir)) {
            log.warn("COBOL directory not found: {}", cobolDir);
            return Collections.emptyList();
        }
        
        List<Path> cobolFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(cobolDir, "*.cbl")) {
            stream.forEach(cobolFiles::add);
        } catch (java.io.IOException e) {
            log.error("Error scanning COBOL directory: {}", cobolDir, e);
            throw e;
        }
        
        cobolFiles.sort(Comparator.comparing(Path::getFileName));
        log.info("Found {} COBOL files", cobolFiles.size());
        return cobolFiles;
    }
    
    /**
     * Find all JCL files in the project
     */
    public List<Path> findJclFiles() throws java.io.IOException {
        if (!Files.exists(jclDir)) {
            log.warn("JCL directory not found: {}", jclDir);
            return Collections.emptyList();
        }
        
        List<Path> jclFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(jclDir, "*.jcl")) {
            stream.forEach(jclFiles::add);
        } catch (java.io.IOException e) {
            log.error("Error scanning JCL directory: {}", jclDir, e);
            throw e;
        }
        
        jclFiles.sort(Comparator.comparing(Path::getFileName));
        log.info("Found {} JCL files", jclFiles.size());
        return jclFiles;
    }
    
    /**
     * Find all copybook files in the project
     */
    public List<Path> findCopybooks() throws java.io.IOException {
        if (!Files.exists(copyDir)) {
            log.warn("Copybook directory not found: {}", copyDir);
            return Collections.emptyList();
        }
        
        List<Path> copies = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(copyDir, "*.cpy")) {
            stream.forEach(copies::add);
        } catch (java.io.IOException e) {
            log.error("Error scanning copybook directory: {}", copyDir, e);
            throw e;
        }
        
        copies.sort(Comparator.comparing(Path::getFileName));
        log.info("Found {} copybooks", copies.size());
        return copies;
    }
    
    /**
     * Create a report of the project structure
     */
    public ProjectStructure scanProject() throws java.io.IOException {
        List<Path> cobolFiles = findCobolFiles();
        List<Path> jclFiles = findJclFiles();
        List<Path> copybooks = findCopybooks();
        
        return ProjectStructure.builder()
                .projectRoot(projectRoot.toString())
                .totalCobolPrograms(cobolFiles.size())
                .totalJclJobs(jclFiles.size())
                .totalCopybooks(copybooks.size())
                .cobolFiles(cobolFiles.stream().map(Path::getFileName).map(Path::toString).toList())
                .jclFiles(jclFiles.stream().map(Path::getFileName).map(Path::toString).toList())
                .copybookFiles(copybooks.stream().map(Path::getFileName).map(Path::toString).toList())
                .scanTime(new java.util.Date())
                .build();
    }
}
