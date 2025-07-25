package org.carball.aden.model.query;

import lombok.Data;
import java.util.List;

/**
 * Analysis result for a single query from Query Store.
 */
@Data
public class AnalyzedQuery {
    private long queryId;
    private long executionCount;
    private double avgDurationMs;
    private double avgCpuTimeMs;
    private double avgLogicalReads;
    private String operationType;
    private List<String> tablesAccessed;
    private int tableCount;
    private String accessPattern;
    private boolean hasJoins;
    private String sqlPreview;
}