package com.smojol.api.query.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration pour le système de query AST
 * Charge les paramètres depuis les propriétés système ou valeurs par défaut
 */
public class ASTConfig {
    private static final Logger logger = LoggerFactory.getLogger(ASTConfig.class);

    // AST Configuration
    private final Path astBasePath;
    private final int cacheMaxSize;
    private final int cacheTtlMinutes;
    private final boolean compressionEnabled;

    // Query Configuration
    private final boolean cycleDetectionEnabled;
    private final int maxRecursionDepth;
    private final int queryTimeoutSeconds;

    // Performance Configuration
    private final int loaderThreads;
    private final int batchSize;
    private final boolean memoryOptimizationEnabled;

    /**
     * Constructor avec valeurs par défaut
     */
    public ASTConfig() {
        this(getAstPathFromEnv(), 100, 60, false, true, 10, 30, 4, 10, true);
    }

    /**
     * Constructor avec tous les paramètres
     */
    public ASTConfig(Path astBasePath, int cacheMaxSize, int cacheTtlMinutes,
                     boolean compressionEnabled, boolean cycleDetectionEnabled,
                     int maxRecursionDepth, int queryTimeoutSeconds,
                     int loaderThreads, int batchSize, boolean memoryOptimizationEnabled) {
        this.astBasePath = astBasePath;
        this.cacheMaxSize = cacheMaxSize;
        this.cacheTtlMinutes = cacheTtlMinutes;
        this.compressionEnabled = compressionEnabled;
        this.cycleDetectionEnabled = cycleDetectionEnabled;
        this.maxRecursionDepth = maxRecursionDepth;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
        this.loaderThreads = loaderThreads;
        this.batchSize = batchSize;
        this.memoryOptimizationEnabled = memoryOptimizationEnabled;

        logger.info("ASTConfig initialized:");
        logger.info("  astBasePath: {}", astBasePath);
        logger.info("  cacheMaxSize: {}", cacheMaxSize);
        logger.info("  cacheTtlMinutes: {}", cacheTtlMinutes);
        logger.info("  compressionEnabled: {}", compressionEnabled);
        logger.info("  cycleDetectionEnabled: {}", cycleDetectionEnabled);
        logger.info("  maxRecursionDepth: {}", maxRecursionDepth);
        logger.info("  queryTimeoutSeconds: {}", queryTimeoutSeconds);
        logger.info("  loaderThreads: {}", loaderThreads);
        logger.info("  batchSize: {}", batchSize);
        logger.info("  memoryOptimizationEnabled: {}", memoryOptimizationEnabled);
    }

    // ==================== Getters ====================

    public Path getAstBasePath() {
        return astBasePath;
    }

    public int getCacheMaxSize() {
        return cacheMaxSize;
    }

    public int getCacheTtlMinutes() {
        return cacheTtlMinutes;
    }

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    public boolean isCycleDetectionEnabled() {
        return cycleDetectionEnabled;
    }

    public int getMaxRecursionDepth() {
        return maxRecursionDepth;
    }

    public int getQueryTimeoutSeconds() {
        return queryTimeoutSeconds;
    }

    public int getLoaderThreads() {
        return loaderThreads;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public boolean isMemoryOptimizationEnabled() {
        return memoryOptimizationEnabled;
    }

    // ==================== Builder ====================

    public static ASTConfigBuilder builder() {
        return new ASTConfigBuilder();
    }

    public static class ASTConfigBuilder {
        private Path astBasePath = getAstPathFromEnv();
        private int cacheMaxSize = 100;
        private int cacheTtlMinutes = 60;
        private boolean compressionEnabled = false;
        private boolean cycleDetectionEnabled = true;
        private int maxRecursionDepth = 10;
        private int queryTimeoutSeconds = 30;
        private int loaderThreads = 4;
        private int batchSize = 10;
        private boolean memoryOptimizationEnabled = true;

        public ASTConfigBuilder astBasePath(Path astBasePath) {
            this.astBasePath = astBasePath;
            return this;
        }

        public ASTConfigBuilder astBasePath(String astBasePath) {
            this.astBasePath = Paths.get(astBasePath);
            return this;
        }

        public ASTConfigBuilder cacheMaxSize(int cacheMaxSize) {
            this.cacheMaxSize = cacheMaxSize;
            return this;
        }

        public ASTConfigBuilder cacheTtlMinutes(int cacheTtlMinutes) {
            this.cacheTtlMinutes = cacheTtlMinutes;
            return this;
        }

        public ASTConfigBuilder compressionEnabled(boolean compressionEnabled) {
            this.compressionEnabled = compressionEnabled;
            return this;
        }

        public ASTConfigBuilder cycleDetectionEnabled(boolean cycleDetectionEnabled) {
            this.cycleDetectionEnabled = cycleDetectionEnabled;
            return this;
        }

        public ASTConfigBuilder maxRecursionDepth(int maxRecursionDepth) {
            this.maxRecursionDepth = maxRecursionDepth;
            return this;
        }

        public ASTConfigBuilder queryTimeoutSeconds(int queryTimeoutSeconds) {
            this.queryTimeoutSeconds = queryTimeoutSeconds;
            return this;
        }

        public ASTConfigBuilder loaderThreads(int loaderThreads) {
            this.loaderThreads = loaderThreads;
            return this;
        }

        public ASTConfigBuilder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public ASTConfigBuilder memoryOptimizationEnabled(boolean memoryOptimizationEnabled) {
            this.memoryOptimizationEnabled = memoryOptimizationEnabled;
            return this;
        }

        public ASTConfig build() {
            return new ASTConfig(astBasePath, cacheMaxSize, cacheTtlMinutes,
                    compressionEnabled, cycleDetectionEnabled, maxRecursionDepth,
                    queryTimeoutSeconds, loaderThreads, batchSize, memoryOptimizationEnabled);
        }
    }

    // ==================== Utilitaires ====================

    /**
     * Récupère le chemin AST depuis les variables d'environnement ou utilise par défaut ./out
     */
    private static Path getAstPathFromEnv() {
        String astPath = System.getenv("AST_BASE_PATH");
        if (astPath != null && !astPath.isEmpty()) {
            return Paths.get(astPath);
        }
        // Valeur par défaut
        return Paths.get("./out");
    }

    @Override
    public String toString() {
        return "ASTConfig{" +
                "astBasePath=" + astBasePath +
                ", cacheMaxSize=" + cacheMaxSize +
                ", cacheTtlMinutes=" + cacheTtlMinutes +
                ", compressionEnabled=" + compressionEnabled +
                ", cycleDetectionEnabled=" + cycleDetectionEnabled +
                ", maxRecursionDepth=" + maxRecursionDepth +
                ", queryTimeoutSeconds=" + queryTimeoutSeconds +
                ", loaderThreads=" + loaderThreads +
                ", batchSize=" + batchSize +
                ", memoryOptimizationEnabled=" + memoryOptimizationEnabled +
                '}';
    }
}
