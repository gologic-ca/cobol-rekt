package com.smojol.api.query.util;

import com.smojol.api.query.model.Copybook;
import com.smojol.api.query.model.ParseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Transforme les métadatas de copybooks (format JSON Map) en objets Copybook exploitables
 * Utilisé par ASTParser pour peupler copybooksList
 */
public class CopybookMetadataTransformer {
    private static final Logger logger = LoggerFactory.getLogger(
        CopybookMetadataTransformer.class);

    /**
     * Transforme une Map de copybooksMetadata en List de Copybook
     *
     * @param copybooksMetadataMap Map extraite de root.get("copybooksMetadata")
     * @return List de Copybook objects prêts à être utilisés
     */
    public List<Copybook> transform(Map<String, Object> copybooksMetadataMap) {
        List<Copybook> result = new ArrayList<>();

        if (copybooksMetadataMap == null || copybooksMetadataMap.isEmpty()) {
            logger.debug("No copybook metadata to transform");
            return result;
        }

        for (Map.Entry<String, Object> entry : copybooksMetadataMap.entrySet()) {
            String copybookName = entry.getKey();
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = (Map<String, Object>) entry.getValue();

                Copybook copybook = transformSingleCopybook(copybookName, metadata);
                result.add(copybook);

                logger.trace("Transformed copybook: {}", copybookName);
            } catch (ClassCastException e) {
                logger.warn("Failed to transform copybook {}: invalid metadata format", copybookName, e);
            }
        }

        logger.debug("Transformed {} copybooks", result.size());
        return result;
    }

    /**
     * Transforme une seule entrée de métadata en Copybook object
     *
     * @param name nom du copybook
     * @param metadata Map contenant uri, size, lines, includes, etc.
     * @return Copybook object peuplé
     */
    private Copybook transformSingleCopybook(String name, Map<String, Object> metadata) {
        return Copybook.builder()
            .name(name)
            .path(getString(metadata, "uri", ""))
            .size(getLong(metadata, "size", 0L))
            .lines(getInt(metadata, "lines", 0))
            .includes(getList(metadata, "includes"))
            .parseStatus(ParseStatus.SUCCESS)
            .lastModified(System.currentTimeMillis())
            .build();
    }

    // ==================== Utilitaires de casting sécurisé ====================

    /**
     * Récupère une String depuis la Map de manière sécurisée
     */
    private String getString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value instanceof String) {
            return (String) value;
        }
        return defaultValue;
    }

    /**
     * Récupère un Long depuis la Map de manière sécurisée
     */
    private long getLong(Map<String, Object> map, String key, long defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }

    /**
     * Récupère un Integer depuis la Map de manière sécurisée
     */
    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    /**
     * Récupère une List<String> depuis la Map de manière sécurisée
     */
    @SuppressWarnings("unchecked")
    private List<String> getList(Map<String, Object> map, String key) {
        if (map == null) {
            return new ArrayList<>();
        }
        Object value = map.get(key);
        if (value instanceof List) {
            try {
                return (List<String>) value;
            } catch (ClassCastException e) {
                logger.debug("Cannot cast value for key {} to List<String>", key);
                return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }
}
