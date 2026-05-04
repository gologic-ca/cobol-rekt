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
        this.astCache = new LRUCache<>(config.getCacheMaxSize());
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
     * Précharge les métadonnées et construit les index (architecture 2-tier).
     * 
     * Phase 1: Charge chaque AST, extrait les métadonnées vers le MetadataIndex
     * Phase 2: Résout les includes copybook
     * Phase 3: Construit l'index JCL→Programme
     * Phase 4: Construit le graphe des callers
     * Phase 5: Si memoryOptimization=true, libère les ASTs complets du heap
     * 
     * Résultat: ~100 MB de RAM pour l'index, même avec 12 GB de fichiers AST.
     */
    public void preloadAllAndResolveIncludes() {
        logger.info("Preloading metadata index (2-tier architecture)...");
        Path basePath = config.getAstBasePath();
        long startTime = System.currentTimeMillis();
        
        try {
            Path reportPath = basePath.resolve("report");
            Path scanPath = Files.exists(reportPath) ? reportPath : basePath;
            
            logger.info("Scanning for AST files in: {}", scanPath.toAbsolutePath());
            
            // Phase 1: Scanner et charger les ASTs pour extraire les métadonnées
            List<String> programNames = new ArrayList<>();
            Files.walk(scanPath, 10)
                    .filter(p -> p.getFileName().toString().endsWith("-aggregated.json"))
                    .forEach(p -> {
                        String fileName = p.getFileName().toString();
                        String name = fileName.replace("-aggregated.json", "");
                        programNames.add(name);
                    });
            
            logger.info("Found {} AST files to index", programNames.size());
            
            for (String name : programNames) {
                Optional<CBLFile> cbl = loader.loadCbl(name);
                cbl.ifPresent(c -> {
                    metadataIndex.indexProgram(c);
                    allCbls.put(name, c);
                });
            }

            // Phase 2: Résoudre les includes pour tous les copybooks
            CopybookIncludesResolver resolver = new CopybookIncludesResolver();
            for (Copybook copybook : allCopybooks.values()) {
                try {
                    resolver.populateResolvedIncludes(copybook, this);
                    metadataIndex.indexCopybook(copybook);
                } catch (Exception e) {
                    logger.warn("Error resolving includes for copybook {}: {}",
                        copybook.getName(), e.getMessage());
                }
            }

            logger.info("Metadata indexed: {} programs, {} copybooks.",
                metadataIndex.getProgramCount(), metadataIndex.getCopybookCount());
            
            // Phase 3: Construire l'index JCL→Programme
            buildJclProgramIndex(new ArrayList<>(allCbls.values()));
            
            // Phase 4: Construire le graphe des callers
            buildCallGraph();
            
            // Phase 5: Synchroniser callers/jcls/plans vers metadataIndex
            for (CBLFile cbl : allCbls.values()) {
                ProgramMetadata meta = metadataIndex.getProgram(cbl.getName()).orElse(null);
                if (meta != null) {
                    meta.setCallers(cbl.getCallers());
                    meta.setJcls(cbl.getJcls());
                    meta.setPlans(cbl.getPlans());
                }
            }
            
            // Phase 6: Libérer les ASTs complets si memory optimization activé
            if (config.isMemoryOptimizationEnabled()) {
                int freedCount = allCbls.size();
                allCbls.clear();
                logger.info("Memory optimization: released {} full ASTs from heap. Index: {}",
                    freedCount, metadataIndex.getStats());
            }
            
            long elapsed = System.currentTimeMillis() - startTime;
            logger.info("Preload complete in {}ms. {}", elapsed, metadataIndex.getStats());
            
        } catch (IOException e) {
            logger.error("Error during preload: {}", e.getMessage(), e);
        }
    }

    /**
     * Construit le graphe d'appels en calculant les callers pour tous les programmes.
     * Cette méthode doit être appelée après le chargement de tous les programmes.
     * 
     * Algorithme:
     * 1. Parcourir tous les programmes et leurs callees
     * 2. Construire une map inverse: callee -> list of callers
     * 3. Mettre à jour chaque programme avec ses callers
     */
    public void buildCallGraph() {
        logger.info("Building call graph...");
        
        // 1. Construire la map inverse (callee -> callers)
        Map<String, List<String>> callersMap = new HashMap<>();
        
        for (CBLFile program : allCbls.values()) {
            String programName = program.getName();
            List<String> callees = program.getCallees();
            
            if (callees != null && !callees.isEmpty()) {
                for (String callee : callees) {
                    callersMap.computeIfAbsent(callee, k -> new ArrayList<>()).add(programName);
                }
            }
        }
        
        // 2. Mettre à jour tous les programmes avec leurs callers
        int updatedCount = 0;
        for (CBLFile program : allCbls.values()) {
            String programName = program.getName();
            List<String> callers = callersMap.get(programName);
            
            if (callers != null && !callers.isEmpty()) {
                // Trier pour cohérence
                Collections.sort(callers);
                program.setCallers(callers);
                updatedCount++;
                
                logger.debug("Program {} has {} callers: {}", programName, callers.size(), callers);
            }
        }
        
        logger.info("Call graph built. Updated {} programs with callers", updatedCount);
        
        // Log statistiques
        int programsWithCallees = (int) allCbls.values().stream()
                .filter(p -> p.getCallees() != null && !p.getCallees().isEmpty())
                .count();
        int programsWithCallers = (int) allCbls.values().stream()
                .filter(p -> p.getCallers() != null && !p.getCallers().isEmpty())
                .count();
        
        logger.info("Call graph stats: {} programs have callees, {} programs have callers",
                programsWithCallees, programsWithCallers);
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
        buildJclProgramIndex(allPrograms);
        
        return allPrograms;
    }
    
    /**
     * Crée un index inversé pour lier chaque programme aux JCL qui l'appellent
     */
    private void buildJclProgramIndex(List<CBLFile> programs) {
        logger.debug("Building JCL→Program index...");
        
        // 1. Charger tous les JCL si pas encore fait
        List<JCLFile> allJcls = getAllJcl();
        
        // 2. Pour chaque programme, trouver les JCL qui le référencent
        //    et les PLANs qui l'appellent (via bindplan entry_point matching program name)
        for (CBLFile program : programs) {
            List<String> callingJcls = new ArrayList<>();
            List<String> callingPlans = new ArrayList<>();
            
            for (JCLFile jcl : allJcls) {
                if (jcl.getPrograms() != null && jcl.getPrograms().contains(program.getName())) {
                    callingJcls.add(jcl.getName());
                }
                
                // Check bindplan steps: if entry_point matches this program, collect the plan name
                if (jcl.getSteps() != null) {
                    for (JCLFile.JCLStep step : jcl.getSteps()) {
                        if ((program.getName().equalsIgnoreCase(step.getProgram()) || program.getName().equalsIgnoreCase(step.getName()))) {
                            // This step executes our program - check if it has a plan parameter
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
            
            // 3. Mettre à jour le programme avec ses JCL appelants et plans
            program.setJcls(callingJcls);
            program.setPlans(callingPlans);
            logger.debug("Program {} is called by {} JCL(s): {}, {} plan(s): {}", 
                program.getName(), callingJcls.size(), callingJcls, callingPlans.size(), callingPlans);
        }
        
        logger.info("JCL→Program index built: {} programs indexed", programs.size());
    }

    @Override
    public List<JCLFile> getAllJcl() {
        logger.debug("getAllJcl called");
        
        Path basePath = config.getAstBasePath();
        Path jclAnalysisPath = basePath.resolve("jcl-analysis.json");
        
        // 1. Charger depuis jcl-analysis.json en priorité
        if (Files.exists(jclAnalysisPath)) {
            logger.info("Loading JCLs from jcl-analysis.json");
            JclAnalysisParser parser = new JclAnalysisParser();
            List<JCLFile> jclFiles = parser.parseJclAnalysis(jclAnalysisPath);
            logger.info("Loaded {} JCLs from jcl-analysis.json", jclFiles.size());
            return jclFiles;
        }
        
        // 2. Fallback: scanner les fichiers AST
        logger.info("jcl-analysis.json not found, falling back to AST scanning");
        
        return scanAstForJclFiles();
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
        
        // Map pour accumuler les informations de chaque copybook
        Map<String, Copybook> copybookMap = new HashMap<>();
        
        // 1. Scanner tous les programmes pour extraire les métadonnées des copybooks depuis astData
        List<CBLFile> allPrograms = getAllCbl();
        
        for (CBLFile program : allPrograms) {
            // Extraire les métadonnées des copybooks depuis astData.copybooksMetadata
            if (program.getAstData() != null) {
                Object copybooksMetadataObj = program.getAstData().get("copybooksMetadata");
                if (copybooksMetadataObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> copybooksMetadata = (Map<String, Object>) copybooksMetadataObj;
                    
                    for (Map.Entry<String, Object> entry : copybooksMetadata.entrySet()) {
                        String copybookName = entry.getKey();
                        Object metadataObj = entry.getValue();
                        
                        if (metadataObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> metadata = (Map<String, Object>) metadataObj;
                            
                            if (!copybookMap.containsKey(copybookName)) {
                                // Extraire uri, size, lines depuis les métadonnées
                                String uri = metadata.get("uri") != null ? metadata.get("uri").toString() : "";
                                String path = normalizeUri(uri);
                                int size = metadata.get("size") != null ? ((Number) metadata.get("size")).intValue() : 0;
                                int lines = metadata.get("lines") != null ? ((Number) metadata.get("lines")).intValue() : 0;
                                
                                Copybook copybook = Copybook.builder()
                                    .name(copybookName)
                                    .path(path)
                                    .size(size)
                                    .lines(lines)
                                    .parseStatus(ParseStatus.SUCCESS)
                                    .lastModified(System.currentTimeMillis())
                                    .usedByCobol(new ArrayList<>())
                                    .usedByCopybook(new ArrayList<>())
                                    .includes(new ArrayList<>())
                                    .build();
                                copybookMap.put(copybookName, copybook);
                                logger.debug("Extracted copybook {} with path: {}", copybookName, path);
                            }
                            
                            // Ajouter ce programme à la liste des usages
                            copybookMap.get(copybookName).getUsedByCobol().add(program.getName());
                        }
                    }
                }
            }
            
            // Fallback : utiliser la liste simple des noms de copybooks si astData n'est pas disponible
            if (program.getCopybooks() != null) {
                for (String copybookName : program.getCopybooks()) {
                    if (!copybookMap.containsKey(copybookName)) {
                        Copybook copybook = Copybook.builder()
                            .name(copybookName)
                            .path("")
                            .size(0)
                            .lines(0)
                            .parseStatus(ParseStatus.SUCCESS)
                            .lastModified(System.currentTimeMillis())
                            .usedByCobol(new ArrayList<>())
                            .usedByCopybook(new ArrayList<>())
                            .includes(new ArrayList<>())
                            .build();
                        copybookMap.put(copybookName, copybook);
                        logger.debug("Created minimal copybook {} (no metadata)", copybookName);
                    }
                    if (!copybookMap.get(copybookName).getUsedByCobol().contains(program.getName())) {
                        copybookMap.get(copybookName).getUsedByCobol().add(program.getName());
                    }
                }
            }
        }
        
        List<Copybook> result = new ArrayList<>(copybookMap.values());
        logger.info("Found {} unique copybooks across {} programs", result.size(), allPrograms.size());
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
        
        // Maps pour accumuler les usages de chaque dataset
        Map<String, List<String>> datasetUsagesByJcl = new HashMap<>();
        Map<String, List<String>> datasetUsagesByCobol = new HashMap<>();
        
        // 1. Collecter datasets depuis JCL
        List<JCLFile> allJcls = getAllJcl();
        for (JCLFile jcl : allJcls) {
            if (jcl.getDatasets() != null) {
                for (String datasetName : jcl.getDatasets()) {
                    datasetUsagesByJcl.computeIfAbsent(datasetName, k -> new ArrayList<>())
                        .add(jcl.getName());
                }
            }
        }
        
        // 2. Collecter datasets depuis CBL
        List<CBLFile> allPrograms = getAllCbl();
        for (CBLFile program : allPrograms) {
            if (program.getDatasets() != null) {
                for (String datasetName : program.getDatasets()) {
                    datasetUsagesByCobol.computeIfAbsent(datasetName, k -> new ArrayList<>())
                        .add(program.getName());
                }
            }
        }
        
        // 3. Créer les objets Dataset avec toutes les données
        Set<String> allDatasetNames = new HashSet<>();
        allDatasetNames.addAll(datasetUsagesByJcl.keySet());
        allDatasetNames.addAll(datasetUsagesByCobol.keySet());
        
        List<Dataset> result = new ArrayList<>();
        for (String datasetName : allDatasetNames) {
            Dataset dataset = Dataset.builder()
                .name(datasetName)
                .path("")
                .parseStatus(ParseStatus.SUCCESS)
                .lastModified(System.currentTimeMillis())
                .usedByJcl(datasetUsagesByJcl.getOrDefault(datasetName, new ArrayList<>()))
                .usedByCobol(datasetUsagesByCobol.getOrDefault(datasetName, new ArrayList<>()))
                .build();
            result.add(dataset);
        }
        
        logger.info("Found {} unique datasets across {} JCLs and {} programs", 
            result.size(), allJcls.size(), allPrograms.size());
        return result;
    }
}

