package org.carball.aden.model.query;

/**
 * Represents a query captured from SQL Server Query Store with execution statistics.
 */
public record QueryStoreQuery(
        String queryId,
        String sqlText,
        long executionCount,
        double avgDurationMs,
        double avgCpuTimeMs,
        double avgLogicalReads
) {}