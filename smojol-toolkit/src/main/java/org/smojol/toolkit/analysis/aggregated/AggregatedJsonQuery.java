package org.smojol.toolkit.analysis.aggregated;

import com.google.gson.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Query builder for aggregated JSON (COBOL AST + JCL Context)
 * Enables programmatic access to DD to COBOL file mappings
 */
public class AggregatedJsonQuery {

    private final JsonObject root;
    private final JsonObject jclContext;

    public AggregatedJsonQuery(JsonObject aggregatedJson) {
        this.root = aggregatedJson;
        this.jclContext = aggregatedJson.getAsJsonObject("jclExecutionContext");
        if (jclContext == null) {
            throw new IllegalArgumentException("Missing jclExecutionContext in aggregated JSON");
        }
    }

    /**
     * Get all DD statements from all steps
     */
    public List<DdStatement> getAllDdStatements() {
        List<DdStatement> statements = new ArrayList<>();
        JsonArray steps = jclContext.getAsJsonArray("steps");
        if (steps != null) {
            for (JsonElement stepElem : steps) {
                JsonObject step = stepElem.getAsJsonObject();
                JsonArray ddStatements = step.getAsJsonArray("ddStatements");
                if (ddStatements != null) {
                    for (JsonElement ddElem : ddStatements) {
                        statements.add(new DdStatement(ddElem.getAsJsonObject()));
                    }
                }
            }
        }
        return statements;
    }

    /**
     * Get DD statements mapped to COBOL files
     */
    public List<DdStatement> getMappedDdStatements() {
        return getAllDdStatements().stream()
                .filter(DdStatement::hasMappedFile)
                .collect(Collectors.toList());
    }

    /**
     * Get unmapped DD statements (system DDs, etc.)
     */
    public List<DdStatement> getUnmappedDdStatements() {
        return getAllDdStatements().stream()
                .filter(dd -> !dd.hasMappedFile())
                .collect(Collectors.toList());
    }

    /**
     * Get input files (fileType == INPUT)
     */
    public List<DdStatement> getInputFiles() {
        return getMappedDdStatements().stream()
                .filter(dd -> "INPUT".equals(dd.getFileType()))
                .collect(Collectors.toList());
    }

    /**
     * Get output files (fileType == OUTPUT)
     */
    public List<DdStatement> getOutputFiles() {
        return getMappedDdStatements().stream()
                .filter(dd -> "OUTPUT".equals(dd.getFileType()))
                .collect(Collectors.toList());
    }

    /**
     * Get files with specific organization
     * @param org SEQUENTIAL, INDEXED, RELATIVE, LINE SEQUENTIAL
     */
    public List<DdStatement> getFilesByOrganization(String org) {
        return getMappedDdStatements().stream()
                .filter(dd -> org.equalsIgnoreCase(dd.getOrganization()))
                .collect(Collectors.toList());
    }

    /**
     * Get indexed files (with record key)
     */
    public List<DdStatement> getIndexedFiles() {
        return getFilesByOrganization("INDEXED");
    }

    /**
     * Get files with record key (record key != null)
     */
    public List<DdStatement> getFilesWithRecordKey() {
        return getMappedDdStatements().stream()
                .filter(dd -> dd.getRecordKey() != null)
                .collect(Collectors.toList());
    }

    /**
     * Get file by DD name
     */
    public Optional<DdStatement> getDdByName(String ddName) {
        return getAllDdStatements().stream()
                .filter(dd -> ddName.equalsIgnoreCase(dd.getDdName()))
                .findFirst();
    }

    /**
     * Get DD by COBOL select name
     */
    public Optional<DdStatement> getDdBySelectName(String selectName) {
        return getMappedDdStatements().stream()
                .filter(dd -> selectName.equalsIgnoreCase(dd.getSelectName()))
                .findFirst();
    }

    /**
     * Find all DDs accessing a dataset by name
     */
    public List<DdStatement> getFilesByDataset(String datasetName) {
        return getAllDdStatements().stream()
                .filter(dd -> {
                    String dsn = dd.getDatasetName();
                    return dsn != null && dsn.equalsIgnoreCase(datasetName);
                })
                .collect(Collectors.toList());
    }

    /**
     * Get all COBOL select names in job
     */
    public Set<String> getAllSelectNames() {
        return getMappedDdStatements().stream()
                .map(DdStatement::getSelectName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Get all DD names in job
     */
    public Set<String> getAllDdNames() {
        return getAllDdStatements().stream()
                .map(DdStatement::getDdName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Statistics about file usage
     */
    public FileStatistics getStatistics() {
        List<DdStatement> all = getAllDdStatements();
        List<DdStatement> mapped = getMappedDdStatements();
        List<DdStatement> input = getInputFiles();
        List<DdStatement> output = getOutputFiles();

        FileStatistics stats = new FileStatistics();
        stats.totalDds = all.size();
        stats.mappedDds = mapped.size();
        stats.unmappedDds = all.size() - mapped.size();
        stats.inputFiles = input.size();
        stats.outputFiles = output.size();
        stats.indexedFiles = getIndexedFiles().size();
        stats.filesWithKeys = getFilesWithRecordKey().size();
        return stats;
    }

    /**
     * Get job information
     */
    public JobInfo getJobInfo() {
        JsonObject job = jclContext.getAsJsonObject("job");
        if (job != null) {
            return new JobInfo(job);
        }
        return null;
    }

    /**
     * Validate mappings
     * Returns list of validation errors
     */
    public List<String> validateMappings() {
        List<String> errors = new ArrayList<>();
        List<DdStatement> all = getAllDdStatements();

        for (DdStatement dd : all) {
            // Check if file status variable is valid COBOL identifier
            String fileStatus = dd.getFileStatus();
            if (fileStatus != null && !isValidCobolIdentifier(fileStatus)) {
                errors.add(String.format("Invalid file status variable '%s' for DD %s (line %d)",
                        fileStatus, dd.getDdName(), dd.getLine()));
            }

            // Check record key for indexed files
            if ("INDEXED".equalsIgnoreCase(dd.getOrganization())) {
                if (dd.getRecordKey() == null || dd.getRecordKey().isEmpty()) {
                    errors.add(String.format("INDEXED file %s requires record key (line %d)",
                            dd.getSelectName(), dd.getLine()));
                }
            }
        }

        return errors;
    }

    /**
     * Pretty print mapping summary
     */
    public String printSummary() {
        StringBuilder sb = new StringBuilder();
        JobInfo job = getJobInfo();
        FileStatistics stats = getStatistics();

        sb.append("\n==== AGGREGATED JSON MAPPING SUMMARY ====\n\n");

        if (job != null) {
            sb.append(String.format("Job: %s\n", job.getName()));
            if (job.getDescription() != null) {
                sb.append(String.format("Description: %s\n", job.getDescription()));
            }
        }

        sb.append(String.format("\nDD Statistics:\n"));
        sb.append(String.format("  Total DDs: %d\n", stats.totalDds));
        sb.append(String.format("  Mapped: %d\n", stats.mappedDds));
        sb.append(String.format("  Unmapped (system): %d\n", stats.unmappedDds));
        sb.append(String.format("  Input files: %d\n", stats.inputFiles));
        sb.append(String.format("  Output files: %d\n", stats.outputFiles));
        sb.append(String.format("  Indexed files: %d\n", stats.indexedFiles));
        sb.append(String.format("  Files with record keys: %d\n\n", stats.filesWithKeys));

        sb.append("Mapped Files:\n");
        sb.append(String.format("%-15s %-25s %-8s %-12s\n", "DD", "SELECT", "TYPE", "ORG"));
        sb.append("─".repeat(65)).append("\n");

        for (DdStatement dd : getMappedDdStatements()) {
            sb.append(String.format("%-15s %-25s %-8s %-12s\n",
                    dd.getDdName(),
                    dd.getSelectName(),
                    dd.getFileType(),
                    dd.getOrganization()));
        }

        List<DdStatement> unmapped = getUnmappedDdStatements();
        if (!unmapped.isEmpty()) {
            sb.append("\nUnmapped DDs (system):\n");
            for (DdStatement dd : unmapped) {
                sb.append(String.format("  - %s (line %d)\n", dd.getDdName(), dd.getLine()));
            }
        }

        List<String> errors = validateMappings();
        if (!errors.isEmpty()) {
            sb.append("\nValidation Errors:\n");
            for (String error : errors) {
                sb.append(String.format("  ⚠️  %s\n", error));
            }
        }

        sb.append("\n==========================================\n");
        return sb.toString();
    }

    private boolean isValidCobolIdentifier(String name) {
        if (name == null || name.isEmpty()) return false;
        if (!Character.isLetter(name.charAt(0)) && name.charAt(0) != '_') return false;
        return name.matches("[a-zA-Z0-9_-]+");
    }

    // ==================== Inner Classes ====================

    public static class DdStatement {
        private final JsonObject json;
        private final JsonObject cobolMapping;

        DdStatement(JsonObject json) {
            this.json = json;
            this.cobolMapping = json.getAsJsonObject("cobolFileMapping");
        }

        public String getDdName() {
            return getStringValue("ddName");
        }

        public int getLine() {
            return getIntValue("line", -1);
        }

        public String getDatasetName() {
            JsonArray datasets = json.getAsJsonArray("datasets");
            if (datasets != null && datasets.size() > 0) {
                JsonObject ds = datasets.get(0).getAsJsonObject();
                return ds.get("dsn") != null ? ds.get("dsn").getAsString() : null;
            }
            return null;
        }

        public String getSelectName() {
            return cobolMapping != null ? cobolMapping.get("selectName").getAsString() : null;
        }

        public String getFileType() {
            return cobolMapping != null ? cobolMapping.get("fileType").getAsString() : null;
        }

        public String getOrganization() {
            return cobolMapping != null ? cobolMapping.get("organization").getAsString() : null;
        }

        public String getAccessMode() {
            return cobolMapping != null ? cobolMapping.get("accessMode").getAsString() : null;
        }

        public String getRecordKey() {
            return cobolMapping != null ? getStringFromObject(cobolMapping, "recordKey") : null;
        }

        public String getFileStatus() {
            return cobolMapping != null ? getStringFromObject(cobolMapping, "fileStatus") : null;
        }

        public boolean hasMappedFile() {
            return cobolMapping != null;
        }

        private String getStringValue(String key) {
            JsonElement elem = json.get(key);
            return elem != null && !elem.isJsonNull() ? elem.getAsString() : null;
        }

        private int getIntValue(String key, int defaultValue) {
            JsonElement elem = json.get(key);
            return elem != null && !elem.isJsonNull() ? elem.getAsInt() : defaultValue;
        }

        private String getStringFromObject(JsonObject obj, String key) {
            JsonElement elem = obj.get(key);
            return elem != null && !elem.isJsonNull() ? elem.getAsString() : null;
        }
    }

    public static class JobInfo {
        private final JsonObject json;

        JobInfo(JsonObject json) {
            this.json = json;
        }

        public String getName() {
            return getStringValue("name");
        }

        public String getDescription() {
            return getStringValue("description");
        }

        public String getClass_() {
            return getStringValue("CLASS");
        }

        public String getMsgClass() {
            return getStringValue("MSGCLASS");
        }

        private String getStringValue(String key) {
            JsonElement elem = json.get(key);
            return elem != null && !elem.isJsonNull() ? elem.getAsString() : null;
        }
    }

    public static class FileStatistics {
        public int totalDds;
        public int mappedDds;
        public int unmappedDds;
        public int inputFiles;
        public int outputFiles;
        public int indexedFiles;
        public int filesWithKeys;

        @Override
        public String toString() {
            return String.format("FileStatistics{total=%d, mapped=%d, unmapped=%d, " +
                    "input=%d, output=%d, indexed=%d, withKeys=%d}",
                    totalDds, mappedDds, unmappedDds, inputFiles, outputFiles, indexedFiles, filesWithKeys);
        }
    }
}
