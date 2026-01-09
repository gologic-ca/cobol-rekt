package com.smojol.api.query.service;

import com.smojol.api.query.config.ASTConfig;
import com.smojol.api.query.util.ASTLoader;
import com.smojol.api.query.model.CBLFile;
import com.smojol.api.query.model.Copybook;
import com.smojol.api.query.model.Dataset;
import com.smojol.api.query.model.JCLFile;
import com.smojol.api.query.util.CopybookIncludesResolver;
import com.smojol.api.query.util.CycleDetector;
import com.smojol.api.query.util.SimpleCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;


/**
 * Implémentation simple du service de query AST
 * Utilise:
 * - ASTLoader pour charger les fichiers
 * - SimpleCache pour cacher en mémoire
 * - CycleDetector pour détecter les cycles copybook
 */
public class SimpleASTQueryService implements ASTQueryService {
    private static final Logger logger = LoggerFactory.getLogger(SimpleASTQueryService.class);

    private final ASTConfig config;
    private final ASTLoader loader;
    private final SimpleCache<String, CBLFile> cblCache;
    private final SimpleCache<String, JCLFile> jclCache;
    private final SimpleCache<String, Copybook> copybookCache;

    // Caches de programmes et datasets trouvés
    private Map<String, CBLFile> allCbls = new HashMap<>();
    private Map<String, JCLFile> allJcls = new HashMap<>();
    private Map<String, Copybook> allCopybooks = new HashMap<>();

    public SimpleASTQueryService(ASTConfig config) {
        this.config = config;
        this.loader = new ASTLoader(config.getAstBasePath().toString());
        this.cblCache = new SimpleCache<>(config.getCacheMaxSize());
        this.jclCache = new SimpleCache<>(config.getCacheMaxSize());
        this.copybookCache = new SimpleCache<>(config.getCacheMaxSize());

        logger.info("SimpleASTQueryService initialized with config: {}", config);
    }

    public SimpleASTQueryService(String astBasePath) {
        this(ASTConfig.builder().astBasePath(astBasePath).build());
    }

    @Override
    public Optional<CBLFile> getCbl(String programName) {
        logger.debug("getCbl: {}", programName);

        // 1. Chercher dans le cache
        Optional<CBLFile> cached = cblCache.get("cbl:" + programName);
        if (cached.isPresent()) {
            logger.debug("Found in CBL cache: {}", programName);
            return cached;
        }

        // 2. Charger depuis le disque
        Optional<CBLFile> cbl = loader.loadCbl(programName);

        // 3. Ajouter au cache
        cbl.ifPresent(c -> {
            cblCache.put("cbl:" + programName, c);
            allCbls.put(programName, c);
        });

        return cbl;
    }

    @Override
    public Optional<JCLFile> getJcl(String jclName) {
        logger.debug("getJcl: {}", jclName);

        // 1. Chercher dans le cache
        Optional<JCLFile> cached = jclCache.get("jcl:" + jclName);
        if (cached.isPresent()) {
            logger.debug("Found in JCL cache: {}", jclName);
            return cached;
        }

        // 2. Charger depuis le disque
        Optional<JCLFile> jcl = loader.loadJcl(jclName);

        // 3. Ajouter au cache
        jcl.ifPresent(j -> {
            jclCache.put("jcl:" + jclName, j);
            allJcls.put(jclName, j);
        });

        return jcl;
    }

    @Override
    public Optional<Copybook> getCopybook(String copybookName) {
        logger.debug("getCopybook: {}", copybookName);

        // 1. Chercher dans le cache
        Optional<Copybook> cached = copybookCache.get("cpy:" + copybookName);
        if (cached.isPresent()) {
            logger.debug("Found in Copybook cache: {}", copybookName);
            return cached;
        }

        // 2. Charger depuis le disque
        Optional<Copybook> copybook = loader.loadCopybook(copybookName);

        // 3. Ajouter au cache
        copybook.ifPresent(c -> {
            copybookCache.put("cpy:" + copybookName, c);
            allCopybooks.put(copybookName, c);
        });

        return copybook;
    }

    @Override
    public Optional<Dataset> getDataset(String datasetName) {
        logger.debug("getDataset: {}", datasetName);
        return Optional.of(loader.loadDataset(datasetName).get());
    }

    @Override
    public List<JCLFile> findJclUsingCbl(String programName) {
        logger.debug("findJclUsingCbl: {}", programName);

        List<JCLFile> result = new ArrayList<>();

        // Parcourir tous les fichiers JCL disponibles
        Path basePath = config.getAstBasePath();
        try {
            Files.list(basePath)
                    .filter(p -> p.getFileName().toString().endsWith("-aggregated.json"))
                    .forEach(p -> {
                        String fileName = p.getFileName().toString();
                        String jclName = fileName.replace("-aggregated.json", "");

                        Optional<JCLFile> jcl = getJcl(jclName);
                        if (jcl.isPresent() && jcl.get().usesProgram(programName)) {
                            result.add(jcl.get());
                        }
                    });
        } catch (IOException e) {
            logger.error("Error scanning for JCLs using program {}: {}", programName, e.getMessage());
        }

        logger.debug("Found {} JCLs using program: {}", result.size(), programName);
        return result;
    }

    @Override
    public List<JCLFile> findJclUsingDataset(String datasetName) {
        logger.debug("findJclUsingDataset: {}", datasetName);

        List<JCLFile> result = new ArrayList<>();

        // Parcourir tous les fichiers JCL disponibles
        Path basePath = config.getAstBasePath();
        try {
            Files.list(basePath)
                    .filter(p -> p.getFileName().toString().endsWith("-aggregated.json"))
                    .forEach(p -> {
                        String fileName = p.getFileName().toString();
                        String jclName = fileName.replace("-aggregated.json", "");

                        Optional<JCLFile> jcl = getJcl(jclName);
                        if (jcl.isPresent() && jcl.get().usesDataset(datasetName)) {
                            result.add(jcl.get());
                        }
                    });
        } catch (IOException e) {
            logger.error("Error scanning for JCLs using dataset {}: {}", datasetName, e.getMessage());
        }

        logger.debug("Found {} JCLs using dataset: {}", result.size(), datasetName);
        return result;
    }

    @Override
    public List<CBLFile> findCblUsingCopybook(String copybookName) {
        logger.debug("findCblUsingCopybook: {}", copybookName);

        List<CBLFile> result = new ArrayList<>();

        // Parcourir tous les programmes COBOL
        Path basePath = config.getAstBasePath();
        try {
            Files.list(basePath)
                    .filter(p -> p.getFileName().toString().endsWith("-aggregated.json"))
                    .forEach(p -> {
                        String fileName = p.getFileName().toString();
                        String cblName = fileName.replace("-aggregated.json", "");

                        Optional<CBLFile> cbl = getCbl(cblName);
                        if (cbl.isPresent() && cbl.get().usesCopybook(copybookName)) {
                            result.add(cbl.get());
                        }
                    });
        } catch (IOException e) {
            logger.error("Error scanning for CBLs using copybook {}: {}", copybookName, e.getMessage());
        }

        logger.debug("Found {} CBLs using copybook: {}", result.size(), copybookName);
        return result;
    }

    @Override
    public List<CBLFile> findCblUsingDataset(String datasetName) {
        logger.debug("findCblUsingDataset: {}", datasetName);

        List<CBLFile> result = new ArrayList<>();

        // Parcourir tous les programmes COBOL
        Path basePath = config.getAstBasePath();
        try {
            Files.list(basePath)
                    .filter(p -> p.getFileName().toString().endsWith("-aggregated.json"))
                    .forEach(p -> {
                        String fileName = p.getFileName().toString();
                        String cblName = fileName.replace("-aggregated.json", "");

                        Optional<CBLFile> cbl = getCbl(cblName);
                        if (cbl.isPresent() && cbl.get().usesDataset(datasetName)) {
                            result.add(cbl.get());
                        }
                    });
        } catch (IOException e) {
            logger.error("Error scanning for CBLs using dataset {}: {}", datasetName, e.getMessage());
        }

        logger.debug("Found {} CBLs using dataset: {}", result.size(), datasetName);
        return result;
    }

    @Override
    public List<CBLFile> findCblCallees(String programName) {
        logger.debug("findCblCallees: {}", programName);

        Optional<CBLFile> program = getCbl(programName);
        if (program.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> calleeNames = program.get().getCallees();
        if (calleeNames == null || calleeNames.isEmpty()) {
            return new ArrayList<>();
        }

        // Charger tous les programs appelés
        List<CBLFile> callees = new ArrayList<>();
        for (String calleeName : calleeNames) {
            Optional<CBLFile> callee = getCbl(calleeName);
            if (callee.isPresent()) {
                callees.add(callee.get());
            }
        }

        logger.debug("Found {} callees for program: {}", callees.size(), programName);
        return callees;
    }

    @Override
    public List<CBLFile> findCblCallers(String programName) {
        logger.debug("findCblCallers: {}", programName);

        Optional<CBLFile> program = getCbl(programName);
        if (program.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> callerNames = program.get().getCallers();
        if (callerNames == null || callerNames.isEmpty()) {
            return new ArrayList<>();
        }

        // Charger tous les programs appelants
        List<CBLFile> callers = new ArrayList<>();
        for (String callerName : callerNames) {
            Optional<CBLFile> caller = getCbl(callerName);
            if (caller.isPresent()) {
                callers.add(caller.get());
            }
        }

        logger.debug("Found {} callers for program: {}", callers.size(), programName);
        return callers;
    }

    @Override
    public List<Copybook> findCopybooksUsedByCbl(String programName) {
        logger.debug("findCopybooksUsedByCbl: {}", programName);

        Optional<CBLFile> program = getCbl(programName);
        if (program.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> copybooks = program.get().getCopybooks();
        if (copybooks == null || copybooks.isEmpty()) {
            return new ArrayList<>();
        }

        // Charger tous les copybooks utilisés
        List<Copybook> result = new ArrayList<>();
        for (String cpyName : copybooks) {
            Optional<Copybook> cpy = getCopybook(cpyName);
            if (cpy.isPresent()) {
                result.add(cpy.get());
            }
        }

        logger.debug("Found {} copybooks used by program: {}", result.size(), programName);
        return result;
    }

    @Override
    public List<Copybook> findCopybooksUsedByCopybook(String copybookName) {
        logger.debug("findCopybooksUsedByCopybook: {}", copybookName);

        Optional<Copybook> copybook = getCopybook(copybookName);
        if (copybook.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> includes = copybook.get().getIncludes();
        if (includes == null || includes.isEmpty()) {
            return new ArrayList<>();
        }

        // Charger les copybooks avec cycle detection
        Map<String, Copybook> allCopybooksMap = new HashMap<>(allCopybooks);

        // Ajouter le copybook courant s'il n'existe pas
        if (!allCopybooksMap.containsKey(copybookName)) {
            allCopybooksMap.put(copybookName, copybook.get());
        }

        // Charger les includes manquants
        for (String include : includes) {
            if (!allCopybooksMap.containsKey(include)) {
                Optional<Copybook> cpy = getCopybook(include);
                cpy.ifPresent(c -> allCopybooksMap.put(include, c));
            }
        }

        // Utiliser le CycleDetector pour récupérer les includes sans cycles
        List<Copybook> result = CycleDetector.getIncludesWithoutCycles(
                copybookName, allCopybooksMap);

        // Vérifier et logger les cycles
        if (config.isCycleDetectionEnabled()) {
            Optional<List<String>> cycle = CycleDetector.findCyclePath(copybookName, allCopybooksMap);
            if (cycle.isPresent()) {
                logger.warn("Cycle detected in copybook dependencies: {}",
                        CycleDetector.formatCyclePath(cycle.get()));
            }
        }

        logger.debug("Found {} copybooks used by copybook: {}", result.size(), copybookName);
        return result;
    }

    // ==================== Utilitaires ====================

    /**
     * Retourne les copybooks utilisés par un programme avec includes résolus
     * (Peuplés par preloadAll() lors du startup)
     */
    public List<Copybook> findCopybooksWithResolvedIncludes(String programName) {
        logger.debug("findCopybooksWithResolvedIncludes: {}", programName);

        Optional<CBLFile> program = getCbl(programName);
        if (program.isEmpty()) {
            return new ArrayList<>();
        }

        // Retourner copybooksList peuplée par ASTParser
        CBLFile cbl = program.get();
        List<Copybook> copybooksList = cbl.getCopybooksList();

        if (copybooksList == null || copybooksList.isEmpty()) {
            logger.debug("No copybooks found in copybooksList for: {}", programName);
            return new ArrayList<>();
        }

        logger.debug("Found {} copybooks with resolved includes for: {}", 
            copybooksList.size(), programName);
        return copybooksList;
    }

    /**
     * Retourne TOUS les includes résolus d'un copybook (récursif)
     * Les includes résolus sont peuplés par preloadAll()
     */
    public List<Copybook> findAllIncludesRecursive(String copybookName) {
        logger.debug("findAllIncludesRecursive: {}", copybookName);

        Optional<Copybook> cpy = getCopybook(copybookName);
        if (cpy.isEmpty()) {
            return new ArrayList<>();
        }

        Copybook copybook = cpy.get();
        List<Copybook> resolved = copybook.getResolvedIncludes();

        if (resolved == null || resolved.isEmpty()) {
            logger.debug("No resolved includes found for copybook: {}", copybookName);
            return new ArrayList<>();
        }

        logger.debug("Found {} resolved includes for: {}", resolved.size(), copybookName);
        return resolved;
    }

    /**
     * Précharge tous les ASTs disponibles et résout les includes
     * À appeler une seule fois au démarrage de l'application
     */
    public void preloadAllAndResolveIncludes() {
        logger.info("Preloading all ASTs and resolving includes...");
        Path basePath = config.getAstBasePath();
        try {
            Files.list(basePath)
                    .filter(p -> p.getFileName().toString().endsWith("-aggregated.json"))
                    .forEach(p -> {
                        String fileName = p.getFileName().toString();
                        String name = fileName.replace("-aggregated.json", "");
                        getCbl(name);  // Load and cache
                    });

            // Résoudre les includes pour tous les copybooks
            CopybookIncludesResolver resolver = new CopybookIncludesResolver();
            for (Copybook copybook : allCopybooks.values()) {
                try {
                    resolver.populateResolvedIncludes(copybook, this);
                } catch (Exception e) {
                    logger.warn("Error resolving includes for copybook {}: {}",
                        copybook.getName(), e.getMessage());
                }
            }

            logger.info("Preload complete. Cached {} CBLs, {} Copybooks. Includes resolved.",
                allCbls.size(), allCopybooks.size());
        } catch (IOException e) {
            logger.error("Error during preload: {}", e.getMessage());
        }
    }

    /**
     * Retourne les statistiques du cache
     */
    public String getCacheStats() {
        return String.format("CBL Cache: %s, JCL Cache: %s, Copybook Cache: %s",
                cblCache.getStats(), jclCache.getStats(), copybookCache.getStats());
    }

    /**
     * Vide tous les caches
     */
    public void clearCaches() {
        cblCache.clear();
        jclCache.clear();
        copybookCache.clear();
        allCbls.clear();
        allJcls.clear();
        allCopybooks.clear();
        logger.info("All caches cleared");
    }

    /**
     * Retourne le nombre de CBLs en cache
     */
    public int getCblCacheSize() {
        return cblCache.size();
    }

    /**
     * Retourne le nombre de JCLs en cache
     */
    public int getJclCacheSize() {
        return jclCache.size();
    }

    /**
     * Retourne le nombre de Copybooks en cache
     */
    public int getCopybookCacheSize() {
        return copybookCache.size();
    }
}
