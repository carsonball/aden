package org.carball.aden.model.query;

import lombok.Data;
import java.util.Map;

/**
 * Qualified metrics for AI decisioning.
 */
@Data
public class QualifiedMetrics {
    private long totalExecutions;
    private Map<String, Long> operationBreakdown;
    private double readWriteRatio;
    private boolean isReadHeavy;
    private TableAccessPatterns tableAccessPatterns;
    private PerformanceCharacteristics performanceCharacteristics;
    private Map<String, Long> accessPatternDistribution;
}