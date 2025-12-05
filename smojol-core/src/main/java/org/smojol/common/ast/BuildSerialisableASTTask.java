package org.smojol.common.ast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;
import org.antlr.v4.runtime.tree.ParseTree;
import org.smojol.common.flowchart.ConsoleColors;
import org.smojol.common.navigation.CobolEntityNavigator;

import java.io.IOException;
import java.io.StringWriter;
import java.util.logging.Logger;

public class BuildSerialisableASTTask {
    private static final Logger LOGGER = Logger.getLogger(BuildSerialisableASTTask.class.getName());
    private Object copybooksRepository;
    private CobolEntityNavigator navigator;

    /**
     *
     * @param tree
     * @param navigator
     * @param copybooksRepository
     * @return
     */
    public CobolContextAugmentedTreeNode run(ParseTree tree, CobolEntityNavigator navigator, Object copybooksRepository) {
        this.navigator = navigator;
        this.copybooksRepository = copybooksRepository;
        navigator.buildDialectNodeRepository();
        java.util.List<String> copybookNames = extractCopybookNames(copybooksRepository);
        String rootCopybookUri = determineCopybookUri(tree);
        CobolContextAugmentedTreeNode graphRoot = new CobolContextAugmentedTreeNode(tree, navigator, copybookNames, rootCopybookUri);
        buildContextGraph(tree, graphRoot, navigator);
        LOGGER.info(ConsoleColors.green(String.format("Memory usage: %s", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())));
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

    private String determineCopybookUri(ParseTree astNode) {
        if (copybooksRepository == null || navigator == null) return null;
        try {
            org.antlr.v4.runtime.Token startToken = getStartToken(astNode);
            if (startToken == null) return null;

            java.lang.reflect.Method getDefinitionsMethod = copybooksRepository.getClass().getMethod("getDefinitions");
            Object definitions = getDefinitionsMethod.invoke(copybooksRepository);
            if (definitions instanceof com.google.common.collect.Multimap) {
                com.google.common.collect.Multimap<String, ?> multimap = (com.google.common.collect.Multimap<String, ?>) definitions;
                for (String copybookName : multimap.keySet()) {
                    java.util.Collection<?> ranges = multimap.get(copybookName);
                    for (Object rangeObj : ranges) {
                        if (isTokenInRange(startToken, rangeObj)) {
                            return copybookName;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Silently ignore - node is from main program
        }
        return null;
    }

    private org.antlr.v4.runtime.Token getStartToken(ParseTree astNode) {
        if (astNode instanceof org.antlr.v4.runtime.ParserRuleContext) {
            return ((org.antlr.v4.runtime.ParserRuleContext) astNode).getStart();
        } else if (astNode instanceof org.antlr.v4.runtime.tree.TerminalNode) {
            return ((org.antlr.v4.runtime.tree.TerminalNode) astNode).getSymbol();
        }
        return null;
    }

    private boolean isTokenInRange(org.antlr.v4.runtime.Token token, Object rangeObj) {
        try {
            java.lang.reflect.Method getStartMethod = rangeObj.getClass().getMethod("getStart");
            java.lang.reflect.Method getStopMethod = rangeObj.getClass().getMethod("getStop");
            int rangeStart = (int) getStartMethod.invoke(rangeObj);
            int rangeStop = (int) getStopMethod.invoke(rangeObj);
            int tokenStart = token.getStartIndex();
            return tokenStart >= rangeStart && tokenStart <= rangeStop;
        } catch (Exception e) {
            return false;
        }
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

    private void buildContextGraph(ParseTree astParentNode, CobolContextAugmentedTreeNode graphParentNode, CobolEntityNavigator navigator) {
        for (int i = 0; i <= astParentNode.getChildCount() - 1; ++i) {
            ParseTree astChildNode = astParentNode.getChild(i);
            String childCopybookUri = determineCopybookUri(astChildNode);
            CobolContextAugmentedTreeNode graphChildNode = new CobolContextAugmentedTreeNode(astChildNode, navigator, null, childCopybookUri);
            graphParentNode.addChild(graphChildNode);
            buildContextGraph(astChildNode, graphChildNode, navigator);
        }
        graphParentNode.freeze();
    }
}
