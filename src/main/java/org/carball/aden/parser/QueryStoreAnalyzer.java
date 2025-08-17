package org.carball.aden.parser;

import org.carball.aden.model.query.*;
import org.carball.aden.config.ThresholdConfig;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Analyzes Query Store data and prepares query store metrics for AI decisioning.
 */
public class QueryStoreAnalyzer {
    
    // Regex patterns for SQL analysis
    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "(?:FROM|JOIN|UPDATE|INTO)\\s+\\[?(?:dbo\\.)?\\[?([A-Za-z_][A-Za-z0-9_]*)]?",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern OPERATION_PATTERN = Pattern.compile(
        "(?:^|\\))\\s*(SELECT|INSERT|UPDATE|DELETE|MERGE)\\s",
        Pattern.CASE_INSENSITIVE
    );
    
    private QueryStoreAnalyzer() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Analyzes queries and returns query store metrics.
     */
    public static QueryStoreAnalysis analyze(List<QueryStoreQuery> queries, String databaseName, ThresholdConfig thresholdConfig) {
        // Analyze queries
        List<AnalyzedQuery> analyzedQueries = queries.stream()
            .map(QueryStoreAnalyzer::analyzeQuery)
            .collect(Collectors.toList());
        
        // Create query store metrics for AI
        QueryStoreMetrics queryStoreMetrics = createQueryStoreMetrics(analyzedQueries, thresholdConfig);
        
        return new QueryStoreAnalysis(databaseName, "QUERY_STORE_PRODUCTION_METRICS", new Date(),
                queries.size(), analyzedQueries, queryStoreMetrics);
    }
    
    /**
     * Analyzes individual query to extract AI-relevant qualifications.
     */
    private static AnalyzedQuery analyzeQuery(QueryStoreQuery query) {
        // Extract operation type
        String operationType = extractOperationType(query.sqlText());
        
        // Extract tables accessed
        List<String> tablesAccessed = extractTableNames(query.sqlText());
        
        // Determine access pattern
        String accessPattern = determineAccessPattern(query.sqlText());
        
        // Check for joins
        boolean hasJoins = query.sqlText().toUpperCase().contains("JOIN");
        
        // SQL preview
        String sqlPreview = query.sqlText().length() > 150
            ? query.sqlText().substring(0, 150) + "..."
            : query.sqlText();
        
        return AnalyzedQuery.builder()
            .queryId(Long.parseLong(query.queryId()))
            .executionCount(query.executionCount())
            .avgDurationMs(query.avgDurationMs())
            .avgCpuTimeMs(query.avgCpuTimeMs())
            .avgLogicalReads(query.avgLogicalReads())
            .operationType(operationType)
            .tablesAccessed(tablesAccessed)
            .tableCount(tablesAccessed.size())
            .accessPattern(accessPattern)
            .hasJoins(hasJoins)
            .sqlPreview(sqlPreview.replaceAll("\\s+", " "))
            .build();
    }
    
    /**
     * Creates query store metrics for AI decisioning.
     */
    private static QueryStoreMetrics createQueryStoreMetrics(List<AnalyzedQuery> analyzedQueries, ThresholdConfig thresholdConfig) {
        QueryStoreMetrics metrics = new QueryStoreMetrics();
        
        // Total execution count
        long totalExecutions = analyzedQueries.stream()
            .mapToLong(AnalyzedQuery::getExecutionCount)
            .sum();
        metrics.setTotalExecutions(totalExecutions);
        
        // Operation breakdown
        Map<String, Long> operationCounts = analyzedQueries.stream()
            .collect(Collectors.groupingBy(
                AnalyzedQuery::getOperationType,
                Collectors.summingLong(AnalyzedQuery::getExecutionCount)
            ));
        metrics.setOperationBreakdown(operationCounts);
        
        // Read/Write ratio
        long readOps = operationCounts.getOrDefault("SELECT", 0L);
        long writeOps = operationCounts.getOrDefault("INSERT", 0L) + 
                       operationCounts.getOrDefault("UPDATE", 0L) + 
                       operationCounts.getOrDefault("DELETE", 0L);
        double readWriteRatio = writeOps == 0 ? readOps : (double) readOps / writeOps;
        metrics.setReadWriteRatio(readWriteRatio);
        metrics.setReadHeavy(readWriteRatio > 10);
        
        // Table co-access patterns
        TableAccessPatterns tablePatterns = analyzeTablePatterns(analyzedQueries, totalExecutions, thresholdConfig);
        metrics.setTableAccessPatterns(tablePatterns);
        
        // Performance characteristics
        PerformanceCharacteristics performanceMetrics = analyzePerformance(analyzedQueries, thresholdConfig);
        metrics.setPerformanceCharacteristics(performanceMetrics);
        
        // Access pattern distribution
        Map<String, Long> accessPatterns = analyzedQueries.stream()
            .collect(Collectors.groupingBy(
                AnalyzedQuery::getAccessPattern,
                Collectors.counting()
            ));
        metrics.setAccessPatternDistribution(accessPatterns);
        
        return metrics;
    }
    
    /**
     * Analyzes table access patterns for co-location insights.
     */
    private static TableAccessPatterns analyzeTablePatterns(List<AnalyzedQuery> queries, long totalExecutions, ThresholdConfig thresholdConfig) {
        // Find tables that are always accessed together
        Map<Set<String>, Long> tableCombinationFrequency = new HashMap<>();
        
        for (AnalyzedQuery query : queries) {
            List<String> tables = query.getTablesAccessed();
            if (tables.size() > 1) {
                Set<String> tableSet = new HashSet<>(tables);
                long executions = query.getExecutionCount();
                tableCombinationFrequency.merge(tableSet, executions, Long::sum);
            }
        }
        
        // Find high-frequency table combinations
        List<TableCombination> frequentCombinations = tableCombinationFrequency.entrySet().stream()
            .filter(entry -> entry.getValue() > thresholdConfig.getProductionCoAccessThreshold())
            .map(entry -> {
                TableCombination combo = new TableCombination();
                combo.setTables(new ArrayList<>(entry.getKey()));
                combo.setTotalExecutions(entry.getValue());
                combo.setExecutionPercentage((entry.getValue() * 100.0) / totalExecutions);
                return combo;
            })
            .sorted((a, b) -> Long.compare(
                b.getTotalExecutions(), 
                a.getTotalExecutions()
            ))
            .collect(Collectors.toList());

        return new TableAccessPatterns(frequentCombinations, !frequentCombinations.isEmpty());
    }
    
    /**
     * Analyzes performance characteristics.
     */
    private static PerformanceCharacteristics analyzePerformance(List<AnalyzedQuery> queries, ThresholdConfig thresholdConfig) {
        // Average metrics
        double avgDuration = queries.stream()
            .mapToDouble(AnalyzedQuery::getAvgDurationMs)
            .average()
            .orElse(0.0);
        
        double avgCpu = queries.stream()
            .mapToDouble(AnalyzedQuery::getAvgCpuTimeMs)
            .average()
            .orElse(0.0);
        
        double avgReads = queries.stream()
            .mapToDouble(AnalyzedQuery::getAvgLogicalReads)
            .average()
            .orElse(0.0);
        
        // Calculate rounded values
        double roundedAvgDuration = Math.round(avgDuration * 100.0) / 100.0;
        double roundedAvgCpu = Math.round(avgCpu * 100.0) / 100.0;
        long roundedAvgReads = Math.round(avgReads);
        
        // Identify slow queries using configurable threshold
        long slowQueryCount = queries.stream()
            .filter(q -> q.getAvgDurationMs() > thresholdConfig.getSlowQueryDurationThresholdMs())
            .count();
        boolean hasPerformanceIssues = slowQueryCount > 0;
        
        return new PerformanceCharacteristics(
            roundedAvgDuration,
            roundedAvgCpu,
            roundedAvgReads,
            slowQueryCount,
            hasPerformanceIssues
        );
    }
    
    /**
     * Extracts operation type from SQL.
     */
    private static String extractOperationType(String sql) {
        Matcher matcher = OPERATION_PATTERN.matcher(sql);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return "UNKNOWN";
    }
    
    /**
     * Extracts table names from SQL.
     */
    private static List<String> extractTableNames(String sql) {
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
        Pattern efPattern = Pattern.compile("\\[dbo]\\.\\[([A-Za-z_][A-Za-z0-9_]*)]", Pattern.CASE_INSENSITIVE);
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
    private static String determineAccessPattern(String sql) {
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
     * Main method for testing with exported Query Store file.
     * Usage: java QueryStoreAnalyzer <path-to-export-file>
     */    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java QueryStoreAnalyzer <path-to-export-file>");
            System.out.println("Example: java QueryStoreAnalyzer query-store-export.json");
            System.exit(1);
        }
        
        try {
            String exportFilePath = args[0];
            System.out.println("=== Query Store Analysis (File-Based) ===");
            System.out.println("Export file: " + exportFilePath);
            
            // Load and verify export file
            QueryStoreFileConnector connector = new QueryStoreFileConnector(exportFilePath);
            if (!connector.isQueryStoreEnabled()) {
                System.out.println("WARNING: Query Store was not enabled when data was exported");
            }
            
            // Extract queries from file
            List<QueryStoreQuery> queries = connector.getAllQueries();
            System.out.printf("Loaded %d queries from export file%n", queries.size());
            
            // Get metadata
            QueryStoreFileConnector.ExportMetadata metadata = connector.getExportMetadata();
            System.out.println("Database: " + metadata.databaseName());
            System.out.println("Export timestamp: " + metadata.exportTimestamp());
            
            // Analyze with default thresholds
            ThresholdConfig defaultThresholds = ThresholdConfig.createDiscoveryDefaults();
            QueryStoreAnalysis analysis = QueryStoreAnalyzer.analyze(queries, metadata.databaseName(), defaultThresholds);
            
            System.out.printf("✓ Analysis completed with %d queries%n", analysis.totalQueriesAnalyzed());
            
        } catch (Exception e) {
            System.err.println("ERROR: " + e);
        }
    }
}