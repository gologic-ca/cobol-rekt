package org.smojol.common.ast;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Metadata for a copybook extracted during AST generation
 * Contains enriched information about copybook definitions, usages, and includes
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CopybookMetadata {

    @Expose
    @SerializedName("uri")
    private String uri;

    @Expose
    @SerializedName("size")
    private long size;

    @Expose
    @SerializedName("lines")
    private int lines;

    @Expose
    @SerializedName("definitions")
    private List<String> definitions;

    @Expose
    @SerializedName("usages")
    private List<UsageInfo> usages;

    @Expose
    @SerializedName("includes")
    private List<String> includes;

    /**
     * Nested class to represent a copybook usage location
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UsageInfo {
        @Expose
        @SerializedName("uri")
        private String uri;

        @Expose
        @SerializedName("line")
        private int line;

        @Expose
        @SerializedName("column")
        private int column;
    }
}
