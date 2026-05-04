package com.smojol.api.query.service;

import com.smojol.api.query.config.ASTConfig;
import com.smojol.api.query.util.ASTLoader;
import com.smojol.api.query.util.JclAnalysisParser;
import com.smojol.api.query.model.CBLFile;
import com.smojol.api.query.util.LRUCache;
import com.smojol.api.query.util.MetadataIndex;
import com.smojol.api.query.model.ProgramMetadata;
import com.smojol.api.query.model.Copybook;
import com.smojol.api.query.model.Dataset;
import com.smojol.api.query.model.JCLFile;
import com.smojol.api.query.model.ParseStatus;
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

    // Tier 1: Index léger en mémoire — métadonnées uniquement (~100 MB pour 12 GB de fichiers)
    private final MetadataIndex metadataIndex = new MetadataIndex();

    // Tier 2: LRU cache pour les ASTs complets chargés à la demande
    private final LRUCache<String, CBLFile> astCache;
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
        this.astCache = new LRUCache<>(Math.min(config.getCacheMaxSize(), 20));
        this.cblCache = new SimpleCache<>(config.getCacheMaxSize());
        this.jclCache = new SimpleCache<>(config.getCacheMaxSize());
        this.copybookCache = new SimpleCache<>(config.getCacheMaxSize());

        logger.info("SimpleASTQueryService initialized with config: {}", config);
    }

    public SimpleASTQueryService(String astBasePath) {
        this(ASTConfig.builder().astBasePath(astBasePath).build());
    }

    public ASTConfig getConfig() {
        return config;
    }

    @Override
    public Optional<CBLFile> getCbl(String programName) {
        logger.debug("getCbl: {}", programName);

        // 1. Chercher dans le LRU cache (full AST)
        Optional<CBLFile> cached = astCache.get(programName);
        if (cached.isPresent()) {
            return cached;
        }

        // 2. Charger depuis le disque
        Optional<CBLFile> cbl = loader.loadCbl(programName);

        // 3. Ajouter au LRU cache
        cbl.ifPresent(c -> {
            astCache.put(programName, c);
            if (metadataIndex.getProgram(programName).isEmpty()) {
                metadataIndex.indexProgram(c);
            }
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

        // Utiliser l index inverse O(1) — aucun chargement AST
        List<ProgramMetadata> metas = metadataIndex.findProgramsUsingCopybook(copybookName);
        if (!metas.isEmpty()) {
            List<CBLFile> result = new ArrayList<>();
            for (ProgramMetadata meta : metas) {
                result.add(meta.toCBLFile());
            }
            logger.debug("Found {} programs using copybook {} (from index)", result.size(), copybookName);
            return result;
        }

        logger.debug("No programs found using copybook: {}", copybookName);
        return new ArrayList<>();
    }

    @Override
    public List<CBLFile> findCblUsingDataset(String datasetName) {
        logger.debug("findCblUsingDataset: {}", datasetName);

        // Utiliser l index inverse O(1) — aucun chargement AST
        List<ProgramMetadata> metas = metadataIndex.findProgramsUsingDataset(datasetName);
        if (!metas.isEmpty()) {
            List<CBLFile> result = new ArrayList<>();
            for (ProgramMetadata meta : metas) {
                result.add(meta.toCBLFile());
            }
            logger.debug("Found {} programs using dataset {} (from index)", result.size(), datasetName);
            return result;
        }

        logger.debug("No programs found using dataset: {}", datasetName);
        return new ArrayList<>();
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
        Map<String, Copybook> allCopybooksMap = new HashMap<>();

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
     * Precharge les metadonnees et construit les index (architecture 2-tier, streaming).
     *
     * Charge un seul AST a la fois, extrait ses metadonnees vers le MetadataIndex,
     * puis le libere IMMEDIATEMENT. Pic memoire = 1 AST max (~150 MB) au lieu de tous.
     */
    public void preloadAllAndResolveIncludes() {
        logger.info("Preloading metadata index (2-tier architecture)...");
        Path basePath = config.getAstBasePath();
        long startTime = System.currentTimeMillis();

        try {
            Path reportPath = basePath.resolve("report");
            Path scanPath = Files.exists(reportPath) ? reportPath : basePath;

            logger.info("Scanning for AST files in: {}", scanPath.toAbsolutePath());

            // Phase 1: Decouvrir les noms de programmes (pas de chargement)
            List<String> programNames = new ArrayList<>();
            Files.walk(scanPath, 10)
                    .filter(p -> p.getFileName().toString().endsWith("-aggregated.json"))
                    .forEach(p -> {
                        String fileName = p.getFileName().toString();
                        programNames.add(fileName.replace("-aggregated.json", ""));
                    });

            logger.info("Found {} AST files to index", programNames.size());

            // Phase 2: Streaming — charger un AST a la fois, extraire metadonnees, liberer
            int indexed = 0;
            for (String name : programNames) {
                Optional<CBLFile> cbl = loader.loadCbl(name);
                if (cbl.isPresent()) {
                    metadataIndex.indexProgram(cbl.get());
                    indexed++;
                    if (indexed % 50 == 0) {
                        logger.info("Indexed {}/{} programs...", indexed, programNames.size());
                    }
                }
                // AST est libere ici (hors scope, eligible GC)
            }

            logger.info("Metadata indexed: {} programs.", metadataIndex.getProgramCount());

            // Phase 3: Charger et indexer les JCL (petits fichiers)
            Path jclAnalysisPath = basePath.resolve("jcl-analysis.json");
            if (Files.exists(jclAnalysisPath)) {
                logger.info("Loading JCLs from jcl-analysis.json");
                JclAnalysisParser parser = new JclAnalysisParser();
                List<JCLFile> jclFiles = parser.parseJclAnalysis(jclAnalysisPath);
                for (JCLFile jcl : jclFiles) {
                    metadataIndex.indexJcl(jcl);
                }
                logger.info("Loaded {} JCLs from jcl-analysis.json", jclFiles.size());
            }

            // Phase 4: Construire l index JCL->Programme depuis le MetadataIndex
            buildJclProgramIndexFromMetadata();

            // Phase 5: Construire le graphe des callers depuis le MetadataIndex
            buildCallGraphFromMetadata();

            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Preload complete in {}ms. {}", elapsed, metadataIndex.getStats());

        } catch (IOException e) {
            logger.error("Error during preload: {}", e.getMessage(), e);
        }
    }

    /**
     * Construit l index JCL->Programme depuis le MetadataIndex (pas de chargement AST).
     */
    private void buildJclProgramIndexFromMetadata() {
        List<JCLFile> allJcls = metadataIndex.getAllJcls();
        List<ProgramMetadata> allPrograms = metadataIndex.getAllPrograms();

        for (ProgramMetadata program : allPrograms) {
            List<String> callingJcls = new ArrayList<>();
            List<String> callingPlans = new ArrayList<>();

            for (JCLFile jcl : allJcls) {
                if (jcl.getPrograms() != null && jcl.getPrograms().contains(program.getName())) {
                    callingJcls.add(jcl.getName());
                }
                if (jcl.getSteps() != null) {
                    for (JCLFile.JCLStep step : jcl.getSteps()) {
                        if (program.getName().equalsIgnoreCase(step.getProgram()) || program.getName().equalsIgnoreCase(step.getName())) {
                            Map<String, String> params = step.getParameters();
                            if (params != null && params.containsKey("PLAN")) {
                                String planName = params.get("PLAN");
                                if (planName != null && !planName.isEmpty() && !callingPlans.contains(planName)) {
                                    callingPlans.add(planName);
                                }
                            }
                        }
                    }
                }
            }

            program.setJcls(callingJcls);
            program.setPlans(callingPlans);
        }

        logger.info("JCL->Program index built: {} programs indexed", allPrograms.size());
    }

    /**
     * Construit le graphe des callers depuis le MetadataIndex (pas de chargement AST).
     */
    private void buildCallGraphFromMetadata() {
        logger.info("Building call graph...");

        Map<String, List<String>> callersMap = new HashMap<>();
        for (ProgramMetadata program : metadataIndex.getAllPrograms()) {
            if (program.getCallees() != null) {
                for (String callee : program.getCallees()) {
                    callersMap.computeIfAbsent(callee, k -> new ArrayList<>()).add(program.getName());
                }
            }
        }

        int updatedCount = 0;
        for (ProgramMetadata program : metadataIndex.getAllPrograms()) {
            List<String> callers = callersMap.get(program.getName());
            if (callers != null && !callers.isEmpty()) {
                Collections.sort(callers);
                program.setCallers(callers);
                updatedCount++;
            }
        }

        logger.info("Call graph built. Updated {} programs with callers", updatedCount);
        int programsWithCallees = (int) metadataIndex.getAllPrograms().stream()
                .filter(p -> p.getCallees() != null && !p.getCallees().isEmpty())
                .count();
        int programsWithCallers = (int) metadataIndex.getAllPrograms().stream()
                .filter(p -> p.getCallers() != null && !p.getCallers().isEmpty())
                .count();
        logger.info("Call graph stats: {} programs have callees, {} programs have callers",
                programsWithCallees, programsWithCallers);
    }

    /**
     * Retourne les statistiques du cache
     */
    public String getCacheStats() {
        return String.format("AST LRU Cache: %s | MetadataIndex: %s | JCL Cache: %s | Copybook Cache: %s",
                astCache.getStats(), metadataIndex.getStats(), jclCache.getStats(), copybookCache.getStats());
    }

    /**
     * Vide tous les caches
     */
    public void clearCaches() {
        astCache.clear();
        cblCache.clear();
        jclCache.clear();
        copybookCache.clear();
        metadataIndex.clear();
        allCbls.clear();
        allJcls.clear();
        allCopybooks.clear();
        logger.info("All caches cleared (astCache + metadataIndex + legacy caches)");
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

    @Override
    public List<CBLFile> getAllCbl() {
        logger.debug("getAllCbl called");
        
        // Si l'index est peuplé, retourner depuis l'index (pas de chargement AST)
        if (metadataIndex.getProgramCount() > 0) {
            List<CBLFile> result = new ArrayList<>();
            for (ProgramMetadata meta : metadataIndex.getAllPrograms()) {
                result.add(meta.toCBLFile());
            }
            logger.debug("Returning {} programs from metadata index", result.size());
            return result;
        }
        
        // Fallback: scanner et charger (première utilisation sans preload)
        logger.info("Metadata index empty, scanning for CBL files...");
        List<CBLFile> allPrograms = new ArrayList<>();
        Path basePath = config.getAstBasePath();
        
        try {
            Path reportPath = basePath.resolve("report");
            Path scanPath = Files.exists(reportPath) ? reportPath : basePath;
            
            Files.walk(scanPath, 10)
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith("-aggregated.json"))
                .filter(p -> p.toString().contains("ast" + java.io.File.separator + "aggregated"))
                .forEach(p -> {
                    String fileName = p.getFileName().toString();
                    String programName = fileName.replace("-aggregated.json", "");
                    Optional<CBLFile> cbl = getCbl(programName);
                    cbl.ifPresent(allPrograms::add);
                });
        } catch (IOException e) {
            logger.error("Error listing all CBL files: {}", e.getMessage(), e);
        }
        
        logger.info("Found {} CBL programs", allPrograms.size());
        buildJclProgramIndexFromMetadata();
        
        return allPrograms;
    }

    @Override
    public List<JCLFile> getAllJcl() {
        logger.debug("getAllJcl called");

        // Utiliser le MetadataIndex si peuple
        List<JCLFile> fromIndex = metadataIndex.getAllJcls();
        if (!fromIndex.isEmpty()) {
            logger.debug("Returning {} JCLs from metadata index", fromIndex.size());
            return fromIndex;
        }

        // Fallback: charger depuis jcl-analysis.json
        Path basePath = config.getAstBasePath();
        Path jclAnalysisPath = basePath.resolve("jcl-analysis.json");
        if (Files.exists(jclAnalysisPath)) {
            logger.info("Loading JCLs from jcl-analysis.json");
            JclAnalysisParser parser = new JclAnalysisParser();
            List<JCLFile> jclFiles = parser.parseJclAnalysis(jclAnalysisPath);
            // Index them for next time
            for (JCLFile jcl : jclFiles) {
                metadataIndex.indexJcl(jcl);
            }
            logger.info("Loaded {} JCLs from jcl-analysis.json", jclFiles.size());
            return jclFiles;
        }

        logger.info("jcl-analysis.json not found, no JCLs available");
        return new ArrayList<>();
    }

    /**
     * Scanner les fichiers AST pour extraire les JCL
     */
    private List<JCLFile> scanAstForJclFiles() {
        List<JCLFile> jclFiles = new ArrayList<>();
        Path basePath = config.getAstBasePath();
        
        try {
            Path reportPath = basePath.resolve("report");
            
            if (Files.exists(reportPath) && Files.isDirectory(reportPath)) {
                // Scanner récursivement dans report/*/ast/aggregated/
                Files.walk(reportPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith("-aggregated.json"))
                    .filter(p -> p.toString().contains("ast" + java.io.File.separator + "aggregated"))
                    .forEach(p -> {
                        String jclName = extractNameFromAggregatedFile(p);
                        getJcl(jclName).ifPresent(jclFiles::add);
                    });
            } else {
                // Fallback: scanner au niveau racine
                Files.list(basePath)
                    .filter(p -> p.getFileName().toString().endsWith("-aggregated.json"))
                    .filter(p -> !p.getFileName().toString().endsWith(".cbl-aggregated.json"))
                    .forEach(p -> {
                        String jclName = extractNameFromAggregatedFile(p);
                        getJcl(jclName).ifPresent(jclFiles::add);
                    });
            }
        } catch (IOException e) {
            logger.error("Error scanning AST for JCL files: {}", e.getMessage());
        }
        
        logger.info("Found {} JCL files from AST scanning", jclFiles.size());
        return jclFiles;
    }

    /**
     * Extrait le nom depuis un fichier aggregated (ex: CBEXPORT-aggregated.json → CBEXPORT)
     */
    private String extractNameFromAggregatedFile(Path filePath) {
        return filePath.getFileName().toString().replace("-aggregated.json", "");
    }

    @Override
    public List<Copybook> getAllCopybooks() {
        logger.debug("getAllCopybooks called");

        // Utiliser le MetadataIndex pour construire la liste des copybooks
        // sans charger aucun AST complet
        Map<String, Copybook> copybookMap = new HashMap<>();

        // 1. Depuis metadataIndex.copybookDetails (si rempli par preload)
        for (Copybook cpy : metadataIndex.getAllCopybooks()) {
            copybookMap.put(cpy.getName(), cpy);
        }

        // 2. Depuis les metadonnees des programmes (index inverse)
        for (ProgramMetadata meta : metadataIndex.getAllPrograms()) {
            if (meta.getCopybooks() != null) {
                for (String cpyName : meta.getCopybooks()) {
                    if (!copybookMap.containsKey(cpyName)) {
                        Copybook copybook = Copybook.builder()
                            .name(cpyName)
                            .path("")
                            .size(0)
                            .lines(0)
                            .parseStatus(ParseStatus.SUCCESS)
                            .lastModified(System.currentTimeMillis())
                            .usedByCobol(new ArrayList<>())
                            .usedByCopybook(new ArrayList<>())
                            .includes(new ArrayList<>())
                            .build();
                        copybookMap.put(cpyName, copybook);
                    }
                    if (!copybookMap.get(cpyName).getUsedByCobol().contains(meta.getName())) {
                        copybookMap.get(cpyName).getUsedByCobol().add(meta.getName());
                    }
                }
            }
        }

        List<Copybook> result = new ArrayList<>(copybookMap.values());
        logger.debug("Returning {} copybooks from metadata index", result.size());
        return result;
    }
    
    /**
     * Normalise un URI de fichier (enlève le préfixe file:/ ou file://)
     */
    private String normalizeUri(String uri) {
        if (uri == null || uri.isEmpty()) {
            return "";
        }
        
        String normalized = uri;
        if (normalized.startsWith("file:///")) {
            normalized = normalized.substring(8);
        } else if (normalized.startsWith("file:/")) {
            normalized = normalized.substring(6);
        }
        
        normalized = normalized.replace('\\', '/');
        return normalized;
    }

    @Override
    public List<Dataset> getAllDatasets() {
        logger.debug("getAllDatasets called");

        // Utiliser l index inverse du MetadataIndex
        Set<String> allDatasetNames = metadataIndex.getDatasetNames();
        if (allDatasetNames != null && !allDatasetNames.isEmpty()) {
            List<Dataset> result = new ArrayList<>();
            for (String dsName : allDatasetNames) {
                Set<String> cobolUsers = metadataIndex.getDatasetUsers(dsName);
                // Trouver les JCL utilisant ce dataset
                List<String> jclUsers = new ArrayList<>();
                for (JCLFile jcl : metadataIndex.getAllJcls()) {
                    if (jcl.getDatasets() != null && jcl.getDatasets().contains(dsName)) {
                        jclUsers.add(jcl.getName());
                    }
                }
                Dataset dataset = Dataset.builder()
                    .name(dsName)
                    .path("")
                    .parseStatus(ParseStatus.SUCCESS)
                    .lastModified(System.currentTimeMillis())
                    .usedByJcl(jclUsers)
                    .usedByCobol(new ArrayList<>(cobolUsers))
                    .build();
                result.add(dataset);
            }
            logger.debug("Returning {} datasets from metadata index", result.size());
            return result;
        }

        // Fallback: collecter depuis JCL et programmes
        Map<String, List<String>> datasetUsagesByJcl = new HashMap<>();
        Map<String, List<String>> datasetUsagesByCobol = new HashMap<>();
        List<JCLFile> jcls = getAllJcl();
        for (JCLFile jcl : jcls) {
            if (jcl.getDatasets() != null) {
                for (String datasetName : jcl.getDatasets()) {
                    datasetUsagesByJcl.computeIfAbsent(datasetName, k -> new ArrayList<>()).add(jcl.getName());
                }
            }
        }
        List<CBLFile> allPrograms = getAllCbl();
        for (CBLFile program : allPrograms) {
            if (program.getDatasets() != null) {
                for (String datasetName : program.getDatasets()) {
                    datasetUsagesByCobol.computeIfAbsent(datasetName, k -> new ArrayList<>()).add(program.getName());
                }
            }
        }
        Set<String> allNames = new HashSet<>();
        allNames.addAll(datasetUsagesByJcl.keySet());
        allNames.addAll(datasetUsagesByCobol.keySet());
        List<Dataset> result = new ArrayList<>();
        for (String dsName : allNames) {
            result.add(Dataset.builder().name(dsName).path("").parseStatus(ParseStatus.SUCCESS)
                .lastModified(System.currentTimeMillis())
                .usedByJcl(datasetUsagesByJcl.getOrDefault(dsName, new ArrayList<>()))
                .usedByCobol(datasetUsagesByCobol.getOrDefault(dsName, new ArrayList<>()))
                .build());
        }
        return result;
    }
}

