package org.carball.aden.model.query;

public record QueryStoreQuery(
        String queryId,
        String sqlText,
        long executionCount,
        double avgDurationMs,
        double avgCpuTimeMs,
        double avgLogicalReads
) {}