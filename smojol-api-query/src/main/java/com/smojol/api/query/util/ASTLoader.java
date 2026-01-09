package com.smojol.api.query.util;

import com.smojol.api.query.model.CBLFile;
import com.smojol.api.query.model.Copybook;
import com.smojol.api.query.model.Dataset;
import com.smojol.api.query.model.JCLFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Gestionnaire de chargement des fichiers AST depuis le disque
 */
public class ASTLoader {
    private static final Logger logger = LoggerFactory.getLogger(ASTLoader.class);
    private final String astBasePath;
    private final ASTParser parser;

    public ASTLoader(String astBasePath) {
        this.astBasePath = astBasePath;
        this.parser = new ASTParser();
    }

    /**
     * Charge un programme COBOL par son nom
     */
    public Optional<CBLFile> loadCbl(String programName) {
        try {
            Optional<String> json = loadAstFile(programName);
            if (json.isEmpty()) {
                logger.debug("AST file not found for program: {}", programName);
                return Optional.empty();
            }
            return Optional.of(parser.parseCbl(json.get(), programName));
        } catch (Exception e) {
            logger.error("Error loading CBL file for {}: {}", programName, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Charge un fichier JCL par son nom
     */
    public Optional<JCLFile> loadJcl(String jclName) {
        try {
            Optional<String> json = loadAstFile(jclName);
            if (json.isEmpty()) {
                logger.debug("AST file not found for JCL: {}", jclName);
                return Optional.empty();
            }
            return Optional.of(parser.parseJcl(json.get(), jclName));
        } catch (Exception e) {
            logger.error("Error loading JCL file for {}: {}", jclName, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Charge un copybook par son nom
     */
    public Optional<Copybook> loadCopybook(String copybookName) {
        try {
            Optional<String> json = loadAstFile(copybookName);
            if (json.isEmpty()) {
                logger.debug("AST file not found for copybook: {}", copybookName);
                return Optional.empty();
            }
            return Optional.of(parser.parseCopybook(json.get(), copybookName));
        } catch (Exception e) {
            logger.error("Error loading copybook {}: {}", copybookName, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Charge les informations d'un dataset
     */
    public Optional<Dataset> loadDataset(String datasetName) {
        try {
            // Chercher un fichier AST pour le dataset (même format que les copybooks)
            Optional<String> json = loadAstFile("DS_" + datasetName);
            if (json.isEmpty()) {
                logger.debug("AST file not found for dataset: {}", datasetName);
                return Optional.empty();
            }
            return Optional.of(parser.parseDataset(json.get(), datasetName));
        } catch (Exception e) {
            logger.error("Error loading dataset {}: {}", datasetName, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Charge un fichier AST brut depuis le disque
     */
    public Optional<String> loadAstFile(String fileName) {
        try {
            String filePath = getFilePath(fileName);
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return Optional.empty();
            }
            String content = Files.readString(path, StandardCharsets.UTF_8);
            logger.trace("Loaded AST file: {}", filePath);
            return Optional.of(content);
        } catch (IOException e) {
            logger.error("Error reading AST file for {}: {}", fileName, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Retourne le chemin d'un fichier AST
     */
    public String getFilePath(String fileName) {
        String cleanName = fileName.toUpperCase().replace(".CPY", "").replace(".CBL", "").replace(".JCL", "");
        // Format report: {astBasePath}/{NAME}.cbl.report/ast/aggregated/{NAME}-aggregated.json
        String reportPath = astBasePath + "/" + cleanName + ".cbl.report/ast/aggregated/" + cleanName + "-aggregated.json";
        Path reportFile = Paths.get(reportPath);
        if (Files.exists(reportFile)) {
            logger.debug("Found AST file at: {}", reportPath);
            return reportPath;
        }
        // Fallback au format simple: {path}/{NAME}-aggregated.json
        String simplePath = astBasePath + "/" + cleanName + "-aggregated.json";
        Path simpleFile = Paths.get(simplePath);
        if (Files.exists(simpleFile)) {
            logger.debug("Found AST file at: {}", simplePath);
            return simplePath;
        }
        // Si aucun fichier trouvé, retourner le chemin report par défaut
        logger.debug("No AST file found for {}, using default path: {}", cleanName, reportPath);
        return reportPath;
    }

    /**
     * Vérifie si un fichier AST existe
     */
    public boolean exists(String fileName) {
        String filePath = getFilePath(fileName);
        return Files.exists(Paths.get(filePath));
    }

    /**
     * Retourne le chemin de base des fichiers AST
     */
    public String getBasePath() {
        return astBasePath;
    }
}
