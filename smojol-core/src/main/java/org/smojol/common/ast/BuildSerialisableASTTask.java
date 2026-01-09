package org.smojol.common.ast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;
import org.antlr.v4.runtime.tree.ParseTree;
import org.smojol.common.flowchart.ConsoleColors;
import org.smojol.common.navigation.CobolEntityNavigator;

import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.logging.Logger;

public class BuildSerialisableASTTask {
    private static final Logger LOGGER = Logger.getLogger(BuildSerialisableASTTask.class.getName());

    /**
     *
     * @param tree
     * @param navigator
     * @param copybooksRepository
     * @return
     */
    public CobolContextAugmentedTreeNode run(ParseTree tree, CobolEntityNavigator navigator,
                                              Object copybooksRepository) {
        navigator.buildDialectNodeRepository();
        java.util.List<String> copybookNames = extractCopybookNames(copybooksRepository);

        // Build copybook metadata
        Map<String, CopybookMetadata> copybooksMetadata =
                new CopybookMetadataBuilder().build(copybooksRepository);

        CobolContextAugmentedTreeNode graphRoot = new CobolContextAugmentedTreeNode(tree, navigator,
                copybookNames, copybooksRepository, copybooksMetadata);
        buildContextGraph(tree, graphRoot, navigator, copybookNames, copybooksRepository);
        LOGGER.info(ConsoleColors.green(String.format("Memory usage: %s",
                Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())));
        return graphRoot;
    }

    private java.util.List<String> extractCopybookNames(Object copybooksRepository) {
        if (copybooksRepository == null) return java.util.Collections.emptyList();
        try {
            java.lang.reflect.Method getDefinitionsMethod = copybooksRepository.getClass().getMethod("getDefinitions");
            Object definitions = getDefinitionsMethod.invoke(copybooksRepository);
            if (definitions instanceof com.google.common.collect.Multimap) {
                com.google.common.collect.Multimap<?, ?> multimap = (com.google.common.collect.Multimap<?, ?>) definitions;
                return multimap.keySet().stream()
                        .map(Object::toString)
                        .collect(java.util.stream.Collectors.toList());
            }
        } catch (Exception e) {
            LOGGER.warning("Could not extract copybook names: " + e.getMessage());
        }
        return java.util.Collections.emptyList();
    }







    /**
     *
     * @param tree
     * @param navigator
     * @return
     */
    public String asSerialised(ParseTree tree, CobolEntityNavigator navigator) {
        CobolContextAugmentedTreeNode serialisableAST = run(tree, navigator, null);
        StringWriter stringWriter = new StringWriter();
        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().setPrettyPrinting().create();
        try (JsonWriter writer = new JsonWriter(stringWriter)) {
            writer.setIndent("  ");
            gson.toJson(serialisableAST, CobolContextAugmentedTreeNode.class, writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return stringWriter.toString();
    }

    private void buildContextGraph(ParseTree astParentNode, CobolContextAugmentedTreeNode graphParentNode, CobolEntityNavigator navigator, java.util.List<String> copybooks, Object copybooksRepository) {
        for (int i = 0; i <= astParentNode.getChildCount() - 1; ++i) {
            ParseTree astChildNode = astParentNode.getChild(i);
            CobolContextAugmentedTreeNode graphChildNode = new CobolContextAugmentedTreeNode(astChildNode, navigator, copybooks, copybooksRepository);
            graphParentNode.addChild(graphChildNode);
            buildContextGraph(astChildNode, graphChildNode, navigator, copybooks, copybooksRepository);
        }
        graphParentNode.freeze();
    }
}
