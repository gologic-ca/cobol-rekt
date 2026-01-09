package com.smojol.api.query.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Représentation d'un copybook COBOL
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Copybook {

    @JsonProperty("name")
    private String name;

    @JsonProperty("path")
    private String path;

    @JsonProperty("size")
    private long size;

    @JsonProperty("lines")
    private int lines;

    @JsonProperty("fields")
    private List<String> fields;

    @JsonProperty("includes")
    private List<String> includes;

    @JsonProperty("used_by_cbl")
    private List<String> usedByCobol;

    @JsonProperty("used_by_cpy")
    private List<String> usedByCopybook;

    @JsonProperty("data_structure")
    private Map<String, Object> dataStructure;

    @JsonProperty("parse_status")
    private ParseStatus parseStatus;

    @JsonProperty("parse_message")
    private String parseMessage;

    @JsonProperty("cpy_data")
    private Map<String, Object> cpyData;

    @JsonProperty("last_modified")
    private long lastModified;

    /**
     * Includes résolus récursivement (peuplé par CopybookIncludesResolver)
     * Non sérialisé en JSON par défaut (interne à l'API)
     */
    private List<Copybook> resolvedIncludes;

    /**
     * Retourne le chemin relatif vers le fichier AST
     */
    public String getAstFileName() {
        return name + "-aggregated.json";
    }

    /**
     * Vérifie si le fichier est valide
     */
    public boolean isValid() {
        return parseStatus == ParseStatus.SUCCESS && cpyData != null;
    }

    /**
     * Retourne le nombre de copybooks inclus
     */
    public int getIncludeCount() {
        return includes != null ? includes.size() : 0;
    }

    /**
     * Retourne le nombre de programmes utilisant ce copybook
     */
    public int getUsedByCobolCount() {
        return usedByCobol != null ? usedByCobol.size() : 0;
    }

    /**
     * Retourne le nombre de copybooks utilisant ce copybook
     */
    public int getUsedByCopybookCount() {
        return usedByCopybook != null ? usedByCopybook.size() : 0;
    }

    /**
     * Vérifie si ce copybook inclut un autre copybook
     */
    public boolean includes(String copybook) {
        return includes != null && includes.contains(copybook);
    }

    /**
     * Vérifie si ce copybook est utilisé par un programme
     */
    public boolean isUsedByCobol(String program) {
        return usedByCobol != null && usedByCobol.contains(program);
    }

    /**
     * Vérifie si ce copybook est utilisé par un autre copybook
     */
    public boolean isUsedByCopybook(String copybook) {
        return usedByCopybook != null && usedByCopybook.contains(copybook);
    }

    /**
     * Retourne la taille totale avec tous les includes (recursive)
     */
    public long getTotalSize() {
        long total = size;
        if (includes != null) {
            // Cette logique sera implémentée plus tard avec le service
            // Pour l'instant, retourne juste la taille du copybook
        }
        return total;
    }
}
