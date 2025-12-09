package org.smojol.common.ast;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import hu.webarticum.treeprinter.SimpleTreeNode;
import hu.webarticum.treeprinter.TreeNode;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.smojol.common.navigation.CobolEntityNavigator;
import org.smojol.common.navigation.TextSpan;

import java.text.MessageFormat;
import java.util.List;

/**
 *  Visualisation Tree Node that encapsulates the actual AST node
 */
public class CobolContextAugmentedTreeNode extends SimpleTreeNode {
    private final ParseTree astNode;

    @Expose
    @SerializedName("nodeType")
    private final String nodeType;
    @Expose
    @SerializedName("text")
    private final String originalText;
    @Expose
    @SerializedName("copybooks")
    private final List<String> copybooks;

    @Expose
    @SerializedName("children")
    private List<TreeNode> childrenRef;

//    @Expose
    @SerializedName("span")
    private TextSpan span;
    private final CobolEntityNavigator navigator;

    public CobolContextAugmentedTreeNode(ParseTree astNode, CobolEntityNavigator navigator) {
        this(astNode, navigator, null, null);
    }

    public CobolContextAugmentedTreeNode(ParseTree astNode, CobolEntityNavigator navigator, List<String> allCopybookNames, Object copybooksRepository) {
        super(astNode.getClass().getSimpleName());
        this.astNode = astNode;
        this.nodeType = astNode.getClass().getSimpleName();
        this.navigator = navigator;
        this.originalText = withType(astNode, false);
        this.span = createSpan(astNode);
        this.copybooks = filterCopybooksByPosition(allCopybookNames, copybooksRepository, this.span);
    }

    private List<String> filterCopybooksByPosition(List<String> allCopybookNames, Object copybooksRepository, TextSpan nodeSpan) {
        if (allCopybookNames == null || allCopybookNames.isEmpty() || copybooksRepository == null || nodeSpan == null) {
            return allCopybookNames != null ? allCopybookNames : java.util.Collections.emptyList();
        }

        try {
            // Get the usages Multimap from CopybooksRepository
            java.lang.reflect.Method getUsagesMethod = copybooksRepository.getClass().getMethod("getUsages");
            Object usages = getUsagesMethod.invoke(copybooksRepository);

            if (!(usages instanceof com.google.common.collect.Multimap)) {
                return java.util.Collections.emptyList();
            }

            com.google.common.collect.Multimap<?, ?> usagesMap = (com.google.common.collect.Multimap<?, ?>) usages;

            // Filter copybooks whose usage location overlaps with this node's span
            return allCopybookNames.stream()
                    .filter(copybookName -> {
                        // Check all usages of this copybook
                        for (Object entry : usagesMap.entries()) {
                            if (entry instanceof java.util.Map.Entry) {
                                java.util.Map.Entry<?, ?> mapEntry = (java.util.Map.Entry<?, ?>) entry;
                                String key = mapEntry.getKey().toString();
                                Object value = mapEntry.getValue();

                                // Check if this entry is for our copybook
                                if (key.contains(copybookName) && value instanceof org.eclipse.lsp4j.Location) {
                                    org.eclipse.lsp4j.Location location = (org.eclipse.lsp4j.Location) value;
                                    org.eclipse.lsp4j.Range range = location.getRange();

                                    // Check if the usage location overlaps with this node's span
                                    int usageStartLine = range.getStart().getLine() + 1; // LSP is 0-based, our span is 1-based
                                    int usageEndLine = range.getEnd().getLine() + 1;
                                    int usageStartChar = range.getStart().getCharacter();
                                    int usageEndChar = range.getEnd().getCharacter();

                                    // Check for overlap
                                    boolean overlaps = !(usageEndLine < nodeSpan.startLine()
                                                        || usageStartLine > nodeSpan.stopLine()
                                                        || (usageEndLine == nodeSpan.startLine() && usageEndChar < nodeSpan.startColumn())
                                                        || (usageStartLine == nodeSpan.stopLine() && usageStartChar > nodeSpan.stopColumn()));

                                    if (overlaps) {
                                        return true;
                                    }
                                }
                            }
                        }
                        return false;
                    })
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            // If reflection fails, return empty list
            return java.util.Collections.emptyList();
        }
    }

    private TextSpan createSpan(ParseTree astNode) {
        if (!(astNode instanceof ParserRuleContext)) {
            TerminalNode terminalNode = (TerminalNode) astNode;
            return new TextSpan(terminalNode.getSymbol().getLine(), terminalNode.getSymbol().getLine(), terminalNode.getSymbol().getCharPositionInLine(), -1, terminalNode.getSymbol().getStartIndex(), terminalNode.getSymbol().getStopIndex());
        }
        ParserRuleContext context = (ParserRuleContext) astNode;
        Token start = context.getStart();
        Token stop = context.getStop();
        return new TextSpan(start.getLine(), stop.getLine(), start.getCharPositionInLine(), stop.getCharPositionInLine(), start.getStartIndex(), stop.getStopIndex());
    }
    @Override
    public String content() {
        String formattedExtent = MessageFormat.format("({0}])", span.content());
        return astNode.getClass().getSimpleName() + " / " + withType(astNode, true) + " " + formattedExtent;
    }

    private String withType(ParseTree astNode, boolean truncate) {
        String originalText = NodeText.originalText(astNode, navigator::dialectText);
        return truncate ? truncated(originalText) : originalText;
    }

    private String truncated(String text) {
        return text.length() > 50 ? text.substring(0, 50) + " ... (truncated)" : text;
    }

    /**
     * Creates a new reference to the children that will be used for serialisation to JSON
     */
    public void freeze() {
        this.childrenRef = super.children();
    }
}
