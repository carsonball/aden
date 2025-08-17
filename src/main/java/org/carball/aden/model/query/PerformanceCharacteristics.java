package org.carball.aden.model.query;

/**
 * Performance characteristics from Query Store analysis.
 */
public record PerformanceCharacteristics(
    double avgQueryDurationMs,
    double avgCpuTimeMs,
    long avgLogicalReads,
    long slowQueryCount,
    boolean hasPerformanceIssues
) {}