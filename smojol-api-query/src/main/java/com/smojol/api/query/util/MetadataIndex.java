package com.smojol.api.query.util;

import com.smojol.api.query.model.CBLFile;
import com.smojol.api.query.model.Copybook;
import com.smojol.api.query.model.JCLFile;
import com.smojol.api.query.model.ProgramMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Index léger en mémoire (Tier 1).
 * 
 * Contient uniquement les métadonnées des programmes, copybooks et JCLs
 * sans stocker les ASTs complets. Permet de répondre aux requêtes de listing,
 * dépendances et analyse d'impact avec ~100 MB de RAM même pour 12 GB de fichiers.
 * 
 * Structure:
 * - programIndex: Map<programName, ProgramMetadata>
 * - copybookIndex: Map<copybookName, Set<programName>> (index inversé)
 * - datasetIndex: Map<datasetName, Set<programName>> (index inversé)
 * - jclIndex: Map<jclName, JCLFile> (les JCL sont petits, on les garde complets)
 * - copybookDetails: Map<copybookName, Copybook> (métadonnées des copybooks)
 */
public class MetadataIndex {
    private static final Logger logger = LoggerFactory.getLogger(MetadataIndex.class);

    private final Map<String, ProgramMetadata> programIndex = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> copybookUsageIndex = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> datasetUsageIndex = new ConcurrentHashMap<>();
    private final Map<String, JCLFile> jclIndex = new ConcurrentHashMap<>();
    private final Map<String, Copybook> copybookDetails = new ConcurrentHashMap<>();

    /**
     * Indexe un programme (extrait les métadonnées, ne garde pas l'AST).
     */
    public void indexProgram(CBLFile cbl) {
        if (cbl == null || cbl.getName() == null) return;
        
        ProgramMetadata meta = ProgramMetadata.fromCBLFile(cbl);
        programIndex.put(cbl.getName(), meta);

        // Index inversé copybook → programmes
        if (cbl.getCopybooks() != null) {
            for (String cpyName : cbl.getCopybooks()) {
                copybookUsageIndex.computeIfAbsent(cpyName, k -> ConcurrentHashMap.newKeySet())
                        .add(cbl.getName());
            }
        }

        // Index inversé dataset → programmes
        if (cbl.getDatasets() != null) {
            for (String dsName : cbl.getDatasets()) {
                datasetUsageIndex.computeIfAbsent(dsName, k -> ConcurrentHashMap.newKeySet())
                        .add(cbl.getName());
            }
        }
    }

    /**
     * Indexe un JCL (gardé complet car petit).
     */
    public void indexJcl(JCLFile jcl) {
        if (jcl == null || jcl.getName() == null) return;
        jclIndex.put(jcl.getName(), jcl);
    }

    /**
     * Indexe un copybook.
     */
    public void indexCopybook(Copybook copybook) {
        if (copybook == null || copybook.getName() == null) return;
        copybookDetails.put(copybook.getName(), copybook);
    }

    // ==================== Programme queries ====================

    public Optional<ProgramMetadata> getProgram(String name) {
        return Optional.ofNullable(programIndex.get(name));
    }

    public List<ProgramMetadata> getAllPrograms() {
        return new ArrayList<>(programIndex.values());
    }

    public List<ProgramMetadata> findProgramsUsingCopybook(String copybookName) {
        Set<String> programNames = copybookUsageIndex.get(copybookName);
        if (programNames == null || programNames.isEmpty()) return Collections.emptyList();
        return programNames.stream()
                .map(programIndex::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<ProgramMetadata> findProgramsUsingDataset(String datasetName) {
        Set<String> programNames = datasetUsageIndex.get(datasetName);
        if (programNames == null || programNames.isEmpty()) return Collections.emptyList();
        return programNames.stream()
                .map(programIndex::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ==================== JCL queries ====================

    public Optional<JCLFile> getJcl(String name) {
        return Optional.ofNullable(jclIndex.get(name));
    }

    public List<JCLFile> getAllJcls() {
        return new ArrayList<>(jclIndex.values());
    }

    // ==================== Copybook queries ====================

    public Optional<Copybook> getCopybook(String name) {
        return Optional.ofNullable(copybookDetails.get(name));
    }

    public List<Copybook> getAllCopybooks() {
        return new ArrayList<>(copybookDetails.values());
    }

    public Set<String> getCopybookUsers(String copybookName) {
        return copybookUsageIndex.getOrDefault(copybookName, Collections.emptySet());
    }

    // ==================== Dataset queries ====================

    public Set<String> getDatasetNames() {
        return Collections.unmodifiableSet(datasetUsageIndex.keySet());
    }

    public Set<String> getDatasetUsers(String datasetName) {
        return datasetUsageIndex.getOrDefault(datasetName, Collections.emptySet());
    }

    // ==================== Mutations (pour buildCallGraph, buildJclIndex) ====================

    public void updateProgramCallers(String programName, List<String> callers) {
        ProgramMetadata meta = programIndex.get(programName);
        if (meta != null) {
            meta.setCallers(callers);
        }
    }

    public void updateProgramJcls(String programName, List<String> jcls) {
        ProgramMetadata meta = programIndex.get(programName);
        if (meta != null) {
            meta.setJcls(jcls);
        }
    }

    public void updateProgramPlans(String programName, List<String> plans) {
        ProgramMetadata meta = programIndex.get(programName);
        if (meta != null) {
            meta.setPlans(plans);
        }
    }

    // ==================== Stats ====================

    public int getProgramCount() {
        return programIndex.size();
    }

    public int getJclCount() {
        return jclIndex.size();
    }

    public int getCopybookCount() {
        return copybookDetails.size();
    }

    public int getDatasetCount() {
        return datasetUsageIndex.size();
    }

    public void clear() {
        programIndex.clear();
        copybookUsageIndex.clear();
        datasetUsageIndex.clear();
        jclIndex.clear();
        copybookDetails.clear();
        logger.info("MetadataIndex cleared");
    }

    public String getStats() {
        return String.format("MetadataIndex[programs=%d, jcls=%d, copybooks=%d, datasets=%d]",
                getProgramCount(), getJclCount(), getCopybookCount(), getDatasetCount());
    }
}
