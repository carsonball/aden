package org.carball.aden.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.carball.aden.model.query.QueryStoreQuery;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Analyzes Query Store data and prepares qualified metrics for AI decisioning.
 * Does NOT make recommendations - that's the AI's job.
 */
public class QueryStoreAnalyzer {
    
    private final ObjectMapper objectMapper;
    
    // Regex patterns for SQL analysis
    private static final Pattern TABLE_PATTERN = Pattern.compile(
        "(?:FROM|JOIN|UPDATE|INTO)\\s+(?:\\[)?(?:dbo\\.)?(?:\\[)?([A-Za-z_][A-Za-z0-9_]*)(?:\\])?",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern OPERATION_PATTERN = Pattern.compile(
        "(?:^|\\))\\s*(SELECT|INSERT|UPDATE|DELETE|MERGE)\\s",
        Pattern.CASE_INSENSITIVE
    );
    
    public QueryStoreAnalyzer() {
        this.objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    }
    
    /**
     * Analyzes queries and exports qualified metrics as JSON.
     */
    public void analyzeAndExport(List<QueryStoreQuery> queries, String outputPath, String databaseName) throws IOException {
        Map<String, Object> analysisData = new HashMap<>();
        
        // Metadata
        analysisData.put("database", databaseName);
        analysisData.put("analysisType", "QUERY_STORE_PRODUCTION_METRICS");
        analysisData.put("timestamp", new Date());
        analysisData.put("totalQueriesAnalyzed", queries.size());
        
        // Analyze queries
        List<Map<String, Object>> analyzedQueries = queries.stream()
            .map(this::analyzeQuery)
            .collect(Collectors.toList());
        analysisData.put("queries", analyzedQueries);
        
        // Create qualified metrics for AI
        Map<String, Object> qualifiedMetrics = createQualifiedMetrics(analyzedQueries);
        analysisData.put("qualifiedMetrics", qualifiedMetrics);
        
        // Write to file
        objectMapper.writeValue(new File(outputPath), analysisData);
    }
    
    /**
     * Analyzes individual query to extract AI-relevant qualifications.
     */
    private Map<String, Object> analyzeQuery(QueryStoreQuery query) {
        Map<String, Object> analysis = new HashMap<>();
        
        // Basic metrics
        analysis.put("queryId", query.getQueryId());
        analysis.put("executionCount", query.getExecutionCount());
        analysis.put("avgDurationMs", query.getAvgDurationMs());
        analysis.put("avgCpuTimeMs", query.getAvgCpuTimeMs());
        analysis.put("avgLogicalReads", query.getAvgLogicalReads());
        
        // Extract operation type
        String operationType = extractOperationType(query.getSqlText());
        analysis.put("operationType", operationType);
        
        // Extract tables accessed
        List<String> tablesAccessed = extractTableNames(query.getSqlText());
        analysis.put("tablesAccessed", tablesAccessed);
        analysis.put("tableCount", tablesAccessed.size());
        
        // Determine access pattern
        String accessPattern = determineAccessPattern(query.getSqlText());
        analysis.put("accessPattern", accessPattern);
        
        // Check for joins
        boolean hasJoins = query.getSqlText().toUpperCase().contains("JOIN");
        analysis.put("hasJoins", hasJoins);
        
        // SQL preview
        String sqlPreview = query.getSqlText().length() > 200 
            ? query.getSqlText().substring(0, 200) + "..."
            : query.getSqlText();
        analysis.put("sqlPreview", sqlPreview.replaceAll("\\s+", " "));
        
        return analysis;
    }
    
    /**
     * Creates qualified metrics for AI decisioning.
     */
    private Map<String, Object> createQualifiedMetrics(List<Map<String, Object>> analyzedQueries) {
        Map<String, Object> metrics = new HashMap<>();
        
        // Total execution count
        long totalExecutions = analyzedQueries.stream()
            .mapToLong(q -> (Long) q.get("executionCount"))
            .sum();
        metrics.put("totalExecutions", totalExecutions);
        
        // Operation breakdown
        Map<String, Long> operationCounts = analyzedQueries.stream()
            .collect(Collectors.groupingBy(
                q -> (String) q.get("operationType"),
                Collectors.summingLong(q -> (Long) q.get("executionCount"))
            ));
        metrics.put("operationBreakdown", operationCounts);
        
        // Read/Write ratio
        long readOps = operationCounts.getOrDefault("SELECT", 0L);
        long writeOps = operationCounts.getOrDefault("INSERT", 0L) + 
                       operationCounts.getOrDefault("UPDATE", 0L) + 
                       operationCounts.getOrDefault("DELETE", 0L);
        double readWriteRatio = writeOps == 0 ? readOps : (double) readOps / writeOps;
        metrics.put("readWriteRatio", readWriteRatio);
        metrics.put("isReadHeavy", readWriteRatio > 10);
        
        // Table co-access patterns
        Map<String, Object> tablePatterns = analyzeTablePatterns(analyzedQueries, totalExecutions);
        metrics.put("tableAccessPatterns", tablePatterns);
        
        // Performance characteristics
        Map<String, Object> performanceMetrics = analyzePerformance(analyzedQueries);
        metrics.put("performanceCharacteristics", performanceMetrics);
        
        // Access pattern distribution
        Map<String, Long> accessPatterns = analyzedQueries.stream()
            .collect(Collectors.groupingBy(
                q -> (String) q.get("accessPattern"),
                Collectors.counting()
            ));
        metrics.put("accessPatternDistribution", accessPatterns);
        
        return metrics;
    }
    
    /**
     * Analyzes table access patterns for co-location insights.
     */
    private Map<String, Object> analyzeTablePatterns(List<Map<String, Object>> queries, long totalExecutions) {
        Map<String, Object> patterns = new HashMap<>();
        
        // Find tables that are always accessed together
        Map<Set<String>, Long> tableSetFrequency = new HashMap<>();
        
        for (Map<String, Object> query : queries) {
            @SuppressWarnings("unchecked")
            List<String> tables = (List<String>) query.get("tablesAccessed");
            if (tables.size() > 1) {
                Set<String> tableSet = new HashSet<>(tables);
                long executions = (Long) query.get("executionCount");
                tableSetFrequency.merge(tableSet, executions, Long::sum);
            }
        }
        
        // Find high-frequency table combinations
        List<Map<String, Object>> frequentCombinations = tableSetFrequency.entrySet().stream()
            .filter(entry -> entry.getValue() > 50) // Lower threshold for test data
            .map(entry -> {
                Map<String, Object> combo = new HashMap<>();
                combo.put("tables", new ArrayList<>(entry.getKey()));
                combo.put("totalExecutions", entry.getValue());
                combo.put("executionPercentage", (entry.getValue() * 100.0) / totalExecutions);
                return combo;
            })
            .sorted((a, b) -> Long.compare(
                (Long) b.get("totalExecutions"), 
                (Long) a.get("totalExecutions")
            ))
            .collect(Collectors.toList());
        
        patterns.put("frequentTableCombinations", frequentCombinations);
        patterns.put("hasStrongCoAccessPatterns", !frequentCombinations.isEmpty());
        
        return patterns;
    }
    
    /**
     * Analyzes performance characteristics.
     */
    private Map<String, Object> analyzePerformance(List<Map<String, Object>> queries) {
        Map<String, Object> perf = new HashMap<>();
        
        // Average metrics
        double avgDuration = queries.stream()
            .mapToDouble(q -> (Double) q.get("avgDurationMs"))
            .average()
            .orElse(0.0);
        
        double avgCpu = queries.stream()
            .mapToDouble(q -> (Double) q.get("avgCpuTimeMs"))
            .average()
            .orElse(0.0);
        
        double avgReads = queries.stream()
            .mapToDouble(q -> (Double) q.get("avgLogicalReads"))
            .average()
            .orElse(0.0);
        
        perf.put("avgQueryDurationMs", Math.round(avgDuration * 100.0) / 100.0);
        perf.put("avgCpuTimeMs", Math.round(avgCpu * 100.0) / 100.0);
        perf.put("avgLogicalReads", Math.round(avgReads));
        
        // Identify slow queries (> 100ms)
        long slowQueryCount = queries.stream()
            .filter(q -> (Double) q.get("avgDurationMs") > 100)
            .count();
        perf.put("slowQueryCount", slowQueryCount);
        perf.put("hasPerformanceIssues", slowQueryCount > 0);
        
        return perf;
    }
    
    /**
     * Extracts operation type from SQL.
     */
    private String extractOperationType(String sql) {
        Matcher matcher = OPERATION_PATTERN.matcher(sql);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return "UNKNOWN";
    }
    
    /**
     * Extracts table names from SQL.
     */
    private List<String> extractTableNames(String sql) {
        List<String> tables = new ArrayList<>();
        
        // First try the standard pattern
        Matcher matcher = TABLE_PATTERN.matcher(sql);
        while (matcher.find()) {
            String tableName = matcher.group(1);
            if (!tableName.startsWith("sys") && 
                !tableName.startsWith("#") && 
                !tableName.equals("INFORMATION_SCHEMA") &&
                !tableName.equals("dbo") &&
                !tables.contains(tableName)) {
                tables.add(tableName);
            }
        }
        
        // For Entity Framework queries, also look for [dbo].[TableName] pattern
        Pattern efPattern = Pattern.compile("\\[dbo\\]\\.\\[([A-Za-z_][A-Za-z0-9_]*)\\]", Pattern.CASE_INSENSITIVE);
        Matcher efMatcher = efPattern.matcher(sql);
        while (efMatcher.find()) {
            String tableName = efMatcher.group(1);
            if (!tables.contains(tableName)) {
                tables.add(tableName);
            }
        }
        
        return tables;
    }
    
    /**
     * Determines access pattern from SQL.
     */
    private String determineAccessPattern(String sql) {
        String upperSql = sql.toUpperCase();
        
        if (upperSql.contains("WHERE") && upperSql.contains("=") && !upperSql.contains("JOIN")) {
            return "KEY_LOOKUP";
        } else if (upperSql.contains("JOIN")) {
            return "JOIN_QUERY";
        } else if (upperSql.contains("GROUP BY") || upperSql.contains("ORDER BY")) {
            return "AGGREGATION";
        } else if (upperSql.contains("BETWEEN") || upperSql.contains(">") || upperSql.contains("<")) {
            return "RANGE_SCAN";
        } else {
            return "TABLE_SCAN";
        }
    }
    
    /**
     * Main method for testing.
     */
    public static void main(String[] args) {
        try {
            String connectionString = QueryStoreConnector.createTestAppConnectionString();
            QueryStoreConnector connector = new QueryStoreConnector(connectionString);
            QueryStoreAnalyzer analyzer = new QueryStoreAnalyzer();
            
            System.out.println("=== Query Store Analysis ===");
            
            // Verify Query Store is available
            if (!connector.isQueryStoreEnabled()) {
                System.out.println("ERROR: Query Store is not enabled");
                return;
            }
            
            // Extract queries
            List<QueryStoreQuery> queries = connector.getAllQueries();
            System.out.printf("Extracted %d queries from Query Store%n", queries.size());
            
            // Analyze and export
            String outputPath = "query_store_analysis.json";
            analyzer.analyzeAndExport(queries, outputPath, "TestEcommerceApp");
            
            System.out.printf("✓ Analysis exported to: %s%n", outputPath);
            
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}