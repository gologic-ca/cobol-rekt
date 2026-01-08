package org.smojol.toolkit.analysis.aggregated;

import com.google.gson.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Example: Using AggregatedJsonQuery to analyze DD to COBOL mappings
 * 
 * This example demonstrates practical queries on aggregated JSON files
 */
public class AggregatedJsonQueryExample {

    public static void main(String[] args) throws Exception {
        // Load aggregated JSON
        String jsonFile = "out/aggregated/CBIMPORT-aggregated.json";
        JsonObject aggregated = loadJson(jsonFile);

        // Create query object
        AggregatedJsonQuery query = new AggregatedJsonQuery(aggregated);

        // ===== Example 1: Get all mappings =====
        System.out.println("\n=== Example 1: All DD Statements ===");
        List<AggregatedJsonQuery.DdStatement> allDds = query.getAllDdStatements();
        for (AggregatedJsonQuery.DdStatement dd : allDds) {
            String mapped = dd.hasMappedFile() ? "✓" : "✗";
            String selectName = dd.getSelectName() != null ? dd.getSelectName() : "[UNMAPPED]";
            System.out.printf("%s %-12s -> %-25s (line %d)\n",
                    mapped, dd.getDdName(), selectName, dd.getLine());
        }

        // ===== Example 2: Only mapped files =====
        System.out.println("\n=== Example 2: Mapped Files Only ===");
        List<AggregatedJsonQuery.DdStatement> mapped = query.getMappedDdStatements();
        System.out.println("Total mapped: " + mapped.size());
        for (AggregatedJsonQuery.DdStatement dd : mapped) {
            System.out.printf("  %s -> %s (%s)\n",
                    dd.getDdName(), dd.getSelectName(), dd.getFileType());
        }

        // ===== Example 3: Input files only =====
        System.out.println("\n=== Example 3: Input Files ===");
        List<AggregatedJsonQuery.DdStatement> inputs = query.getInputFiles();
        System.out.println("Input files: " + inputs.size());
        for (AggregatedJsonQuery.DdStatement dd : inputs) {
            System.out.printf("  DD: %s\n", dd.getDdName());
            System.out.printf("    SELECT: %s\n", dd.getSelectName());
            System.out.printf("    Org: %s, Access: %s\n", dd.getOrganization(), dd.getAccessMode());
            System.out.printf("    Dataset: %s\n", dd.getDatasetName());
        }

        // ===== Example 4: Output files =====
        System.out.println("\n=== Example 4: Output Files ===");
        List<AggregatedJsonQuery.DdStatement> outputs = query.getOutputFiles();
        System.out.println("Output files: " + outputs.size());
        outputs.forEach(dd -> {
            String status = dd.getFileStatus() != null ? dd.getFileStatus() : "—";
            System.out.printf("  %s -> %s (Status: %s)\n", dd.getDdName(), dd.getSelectName(), status);
        });

        // ===== Example 5: Indexed files with record keys =====
        System.out.println("\n=== Example 5: Indexed Files ===");
        List<AggregatedJsonQuery.DdStatement> indexed = query.getIndexedFiles();
        System.out.println("Indexed files: " + indexed.size());
        for (AggregatedJsonQuery.DdStatement dd : indexed) {
            System.out.printf("  %s -> %s\n", dd.getDdName(), dd.getSelectName());
            System.out.printf("    Record Key: %s\n", dd.getRecordKey());
            System.out.printf("    Access Mode: %s\n", dd.getAccessMode());
        }

        // ===== Example 6: Query by name =====
        System.out.println("\n=== Example 6: Query Specific DD ===");
        query.getDdByName("EXPFILE").ifPresent(dd -> {
            System.out.printf("Found DD 'EXPFILE':\n");
            System.out.printf("  SELECT: %s\n", dd.getSelectName());
            System.out.printf("  Organization: %s\n", dd.getOrganization());
            System.out.printf("  Record Key: %s\n", dd.getRecordKey());
            System.out.printf("  File Status: %s\n", dd.getFileStatus());
        });

        // ===== Example 7: Find by COBOL select name =====
        System.out.println("\n=== Example 7: Find DD by COBOL Select ===");
        query.getDdBySelectName("CUSTOMER-OUTPUT").ifPresent(dd -> {
            System.out.printf("SELECT 'CUSTOMER-OUTPUT' is mapped to:\n");
            System.out.printf("  DD: %s\n", dd.getDdName());
            System.out.printf("  Dataset: %s\n", dd.getDatasetName());
            System.out.printf("  File Status: %s\n", dd.getFileStatus());
        });

        // ===== Example 8: Find by dataset name =====
        System.out.println("\n=== Example 8: Find by Dataset ===");
        List<AggregatedJsonQuery.DdStatement> ddsForDataset = 
            query.getFilesByDataset("CARDDEMO.EXPORT.DATA");
        System.out.printf("DDs using 'CARDDEMO.EXPORT.DATA': %d\n", ddsForDataset.size());
        for (AggregatedJsonQuery.DdStatement dd : ddsForDataset) {
            System.out.printf("  DD: %s -> SELECT: %s\n", dd.getDdName(), dd.getSelectName());
        }

        // ===== Example 9: Get all select names =====
        System.out.println("\n=== Example 9: All SELECT Names ===");
        System.out.println("COBOL SELECT statements:");
        query.getAllSelectNames().stream()
                .sorted()
                .forEach(name -> System.out.printf("  - %s\n", name));

        // ===== Example 10: Statistics =====
        System.out.println("\n=== Example 10: File Statistics ===");
        AggregatedJsonQuery.FileStatistics stats = query.getStatistics();
        System.out.printf("Total DDs: %d\n", stats.totalDds);
        System.out.printf("Mapped to COBOL: %d\n", stats.mappedDds);
        System.out.printf("System/Unmapped: %d\n", stats.unmappedDds);
        System.out.printf("Input files: %d\n", stats.inputFiles);
        System.out.printf("Output files: %d\n", stats.outputFiles);
        System.out.printf("Indexed files: %d\n", stats.indexedFiles);
        System.out.printf("Files with record keys: %d\n", stats.filesWithKeys);

        // ===== Example 11: Job information =====
        System.out.println("\n=== Example 11: Job Information ===");
        AggregatedJsonQuery.JobInfo job = query.getJobInfo();
        if (job != null) {
            System.out.printf("Job: %s\n", job.getName());
            System.out.printf("Description: %s\n", job.getDescription());
            System.out.printf("Class: %s\n", job.getClass_());
            System.out.printf("MsgClass: %s\n", job.getMsgClass());
        }

        // ===== Example 12: Validation =====
        System.out.println("\n=== Example 12: Validation Report ===");
        List<String> errors = query.validateMappings();
        if (errors.isEmpty()) {
            System.out.println("✓ All mappings are valid!");
        } else {
            System.out.println("Validation errors found:");
            errors.forEach(e -> System.out.printf("  ⚠️  %s\n", e));
        }

        // ===== Example 13: Unmapped DDs =====
        System.out.println("\n=== Example 13: Unmapped DDs ===");
        List<AggregatedJsonQuery.DdStatement> unmapped = query.getUnmappedDdStatements();
        System.out.printf("Unmapped: %d\n", unmapped.size());
        unmapped.forEach(dd -> System.out.printf("  - %s (system, line %d)\n", dd.getDdName(), dd.getLine()));

        // ===== Example 14: Pretty print summary =====
        System.out.println(query.printSummary());
    }

    static JsonObject loadJson(String filePath) throws Exception {
        String content = new String(Files.readAllBytes(Paths.get(filePath)));
        JsonParser parser = new JsonParser();
        return parser.parse(content).getAsJsonObject();
    }
}
