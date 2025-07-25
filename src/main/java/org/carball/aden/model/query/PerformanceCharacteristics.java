package org.carball.aden.model.query;

import lombok.Data;

/**
 * Performance characteristics from Query Store analysis.
 */
@Data
public class PerformanceCharacteristics {
    private double avgQueryDurationMs;
    private double avgCpuTimeMs;
    private long avgLogicalReads;
    private long slowQueryCount;
    private boolean hasPerformanceIssues;
}