package org.smojol.common.ast;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;

/**
 * Builder for extracting copybook metadata from CopybooksRepository
 * Converts repository data into enriched CopybookMetadata objects suitable for AST serialization
 */
public class CopybookMetadataBuilder {
    private static final Logger LOGGER = Logger.getLogger(CopybookMetadataBuilder.class.getName());

    /**
     * Extract metadata from the CopybooksRepository and return as a map
     * @param copybooksRepository the repository containing copybook definitions, usages, and statements
     * @return Map of copybook name to enriched metadata
     */
    public Map<String, CopybookMetadata> build(Object copybooksRepository) {
        if (copybooksRepository == null) {
            return Collections.emptyMap();
        }

        Map<String, CopybookMetadata> result = new LinkedHashMap<>();

        try {
            // Extract definitions Multimap using reflection to avoid generic type issues
            java.lang.reflect.Method getDefinitionsMethod =
                    copybooksRepository.getClass().getMethod("getDefinitions");
            Object definitionsObj = getDefinitionsMethod.invoke(copybooksRepository);

            if (!(definitionsObj instanceof com.google.common.collect.Multimap)) {
                return result;
            }

            @SuppressWarnings("unchecked")
            com.google.common.collect.Multimap<Object, Object> definitions =
                    (com.google.common.collect.Multimap<Object, Object>) definitionsObj;

            // Extract usages Multimap
            java.lang.reflect.Method getUsagesMethod = copybooksRepository.getClass().getMethod("getUsages");
            Object usagesObj = getUsagesMethod.invoke(copybooksRepository);
            @SuppressWarnings("unchecked")
            com.google.common.collect.Multimap<Object, Object> usages =
                    (usagesObj instanceof com.google.common.collect.Multimap)
                    ? (com.google.common.collect.Multimap<Object, Object>) usagesObj : null;

            // Process each copybook definition
            for (Object copybookIdObj : definitions.keySet()) {
                String copybookId = copybookIdObj.toString();

                CopybookMetadata.CopybookMetadataBuilder builder = CopybookMetadata.builder();

                // Extract the simple name from the CopybookId
                String simpleName = extractCopybookName(copybookId);

                // Get the URI from definitions
                java.util.Collection<Object> defUris = definitions.get(copybookIdObj);
                String uri = null;
                if (defUris != null && !defUris.isEmpty()) {
                    uri = defUris.iterator().next().toString();
                }
                builder.uri(uri);

                // Get file size and line count if URI is available
                if (uri != null) {
                    try {
                        String filePath = convertUriToPath(uri);
                        File file = new File(filePath);
                        if (file.exists()) {
                            builder.size(file.length());
                            builder.lines((int) Files.lines(Paths.get(filePath)).count());
                        }
                    } catch (Exception e) {
                        LOGGER.fine("Could not read file metadata for " + uri + ": " + e.getMessage());
                        builder.size(0);
                        builder.lines(0);
                    }
                }

                // Extract usages
                List<CopybookMetadata.UsageInfo> usageList = new ArrayList<>();
                if (usages != null) {
                    java.util.Collection<Object> usageValues = usages.get(copybookIdObj);
                    if (usageValues != null) {
                        for (Object usageVal : usageValues) {
                            if (usageVal instanceof org.eclipse.lsp4j.Location) {
                                org.eclipse.lsp4j.Location loc = (org.eclipse.lsp4j.Location) usageVal;
                                org.eclipse.lsp4j.Range range = loc.getRange();
                                CopybookMetadata.UsageInfo usage = CopybookMetadata.UsageInfo.builder()
                                    .uri(loc.getUri())
                                    .line(range.getStart().getLine() + 1) // Convert to 1-based
                                    .column(range.getStart().getCharacter())
                                    .build();
                                usageList.add(usage);
                            }
                        }
                    }
                }
                builder.usages(usageList);

                // Extract includes (nested copybooks)
                // Currently we don't have nested copybook info in Locality so we keep this empty
                List<String> includesList = new ArrayList<>();
                builder.includes(includesList);

                result.put(simpleName, builder.build());
            }

        } catch (Exception e) {
            LOGGER.warning("Error building copybook metadata: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Extract the simple copybook name from a CopybookId string
     * @param copybookId the full CopybookId (format: name/dialect/uri or name/dialect)
     * @return the simple copybook name
     */
    private String extractCopybookName(String copybookId) {
        // CopybookId format is typically: "COPYBOOK_NAME/DIALECT/URI" or similar
        // Extract just the first part (the copybook name)
        if (copybookId == null || copybookId.isEmpty()) {
            return copybookId;
        }

        int slashIndex = copybookId.indexOf('/');
        if (slashIndex > 0) {
            return copybookId.substring(0, slashIndex);
        }
        return copybookId;
    }

    /**
     * Convert file:// URI to filesystem path
     * @param uri the file URI
     * @return the file path
     */
    private String convertUriToPath(String uri) {
        if (uri == null) {
            return null;
        }

        // Handle file:// URIs
        if (uri.startsWith("file://")) {
            // On Windows, we might have file:///C:/path, on Unix file:///path
            String path = uri.substring(7);
            if (path.length() > 2 && path.charAt(2) == ':') {
                // Windows path like C:/path
                return path.replace("/", "\\");
            }
            return path;
        }

        return uri;
    }
}
