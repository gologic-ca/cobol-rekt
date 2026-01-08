package org.smojol.toolkit.task;

import com.mojo.algorithms.task.CommandLineAnalysisTask;

/**
 * Extended enum to support additional analysis tasks without modifying the mojo-common submodule.
 * Maps custom tasks to base CommandLineAnalysisTask enum values where they exist.
 */
public enum ExtendedCommandLineAnalysisTask {
    FLOW_TO_NEO4J("Export parsed flow to Neo4j database", CommandLineAnalysisTask.FLOW_TO_NEO4J),
    FLOW_TO_GRAPHML("Export parsed flow to GraphML format", CommandLineAnalysisTask.FLOW_TO_GRAPHML),
    WRITE_RAW_AST("Write raw COBOL AST only", CommandLineAnalysisTask.WRITE_RAW_AST),
    WRITE_FLOW_AST("Write control flow AST", CommandLineAnalysisTask.WRITE_FLOW_AST),
    DRAW_FLOWCHART("Generate flowchart visualization", CommandLineAnalysisTask.DRAW_FLOWCHART),
    EXPORT_MERMAID("Export to Mermaid diagram format", CommandLineAnalysisTask.EXPORT_MERMAID),
    WRITE_CFG("Write control flow graph", CommandLineAnalysisTask.WRITE_CFG),
    ATTACH_COMMENTS("Attach source code comments", CommandLineAnalysisTask.ATTACH_COMMENTS),
    WRITE_DATA_STRUCTURES("Write data structure definitions", CommandLineAnalysisTask.WRITE_DATA_STRUCTURES),
    BUILD_PROGRAM_DEPENDENCIES("Build program dependency graph", CommandLineAnalysisTask.BUILD_PROGRAM_DEPENDENCIES),
    EXPORT_UNIFIED_TO_JSON("Export unified analysis to JSON", CommandLineAnalysisTask.EXPORT_UNIFIED_TO_JSON),
    COMPARE_CODE("Compare code versions", CommandLineAnalysisTask.COMPARE_CODE),
    WRITE_LLM_SUMMARY("Write LLM-generated summary", CommandLineAnalysisTask.WRITE_LLM_SUMMARY),
    SUMMARISE_THROUGH_LLM("Summarize through LLM analysis", CommandLineAnalysisTask.SUMMARISE_THROUGH_LLM),
    BUILD_TRANSPILER_FLOWGRAPH("Build transpiler flow graph", CommandLineAnalysisTask.BUILD_TRANSPILER_FLOWGRAPH),
    BUILD_BASE_ANALYSIS("Build base analysis", CommandLineAnalysisTask.BUILD_BASE_ANALYSIS),
    WRITE_AGGREGATED_JCL_AST("Write aggregated AST with JCL execution context", null),
    DO_NOTHING("Do nothing", CommandLineAnalysisTask.DO_NOTHING);
    
    private final String description;
    private final CommandLineAnalysisTask baseTask;
    
    ExtendedCommandLineAnalysisTask(String description, CommandLineAnalysisTask baseTask) {
        this.description = description;
        this.baseTask = baseTask;
    }
    
    /**
     * Get the description of this analysis task.
     *
     * @return task description
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Map this extended task to the base CommandLineAnalysisTask enum.
     * This allows using extended tasks with code that expects the base enum.
     * Returns null for tasks that don't have a base equivalent (e.g., WRITE_AGGREGATED_JCL_AST).
     *
     * @return the corresponding base CommandLineAnalysisTask, or null if not available
     */
    public CommandLineAnalysisTask toBaseTask() {
        return baseTask;
    }
    
    /**
     * Create an ExtendedCommandLineAnalysisTask from a base CommandLineAnalysisTask.
     *
     * @param baseTask the base task
     * @return the corresponding extended task
     * @throws IllegalArgumentException if no matching extended task exists
     */
    public static ExtendedCommandLineAnalysisTask fromBaseTask(CommandLineAnalysisTask baseTask) {
        for (ExtendedCommandLineAnalysisTask task : values()) {
            if (task.baseTask == baseTask) {
                return task;
            }
        }
        throw new IllegalArgumentException("No extended task mapping found for: " + baseTask);
    }
}
