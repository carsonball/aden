package org.carball.aden.model.query;

import java.util.List;

/**
 * Represents a query captured from SQL Server Query Store with execution statistics.
 */
public class QueryStoreQuery {
    private String queryId;
    private String sqlText;
    private long executionCount;
    private double avgDurationMs;
    private double avgCpuTimeMs;
    private double avgLogicalReads;
    private List<String> tablesAccessed;
    private String joinPattern;
    
    public QueryStoreQuery() {}
    
    public QueryStoreQuery(String queryId, String sqlText, long executionCount, 
                          double avgDurationMs, double avgCpuTimeMs, double avgLogicalReads) {
        this.queryId = queryId;
        this.sqlText = sqlText;
        this.executionCount = executionCount;
        this.avgDurationMs = avgDurationMs;
        this.avgCpuTimeMs = avgCpuTimeMs;
        this.avgLogicalReads = avgLogicalReads;
    }
    
    public String getQueryId() { return queryId; }
    public void setQueryId(String queryId) { this.queryId = queryId; }
    
    public String getSqlText() { return sqlText; }
    public void setSqlText(String sqlText) { this.sqlText = sqlText; }
    
    public long getExecutionCount() { return executionCount; }
    public void setExecutionCount(long executionCount) { this.executionCount = executionCount; }
    
    public double getAvgDurationMs() { return avgDurationMs; }
    public void setAvgDurationMs(double avgDurationMs) { this.avgDurationMs = avgDurationMs; }
    
    public double getAvgCpuTimeMs() { return avgCpuTimeMs; }
    public void setAvgCpuTimeMs(double avgCpuTimeMs) { this.avgCpuTimeMs = avgCpuTimeMs; }
    
    public double getAvgLogicalReads() { return avgLogicalReads; }
    public void setAvgLogicalReads(double avgLogicalReads) { this.avgLogicalReads = avgLogicalReads; }
    
    public List<String> getTablesAccessed() { return tablesAccessed; }
    public void setTablesAccessed(List<String> tablesAccessed) { this.tablesAccessed = tablesAccessed; }
    
    public String getJoinPattern() { return joinPattern; }
    public void setJoinPattern(String joinPattern) { this.joinPattern = joinPattern; }
    
    @Override
    public String toString() {
        return String.format("QueryStoreQuery{id='%s', executions=%d, avgDuration=%.2fms, tables=%s}", 
                           queryId, executionCount, avgDurationMs, tablesAccessed);
    }
}