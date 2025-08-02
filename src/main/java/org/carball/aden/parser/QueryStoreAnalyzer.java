package org.carball.aden.parser;

import org.carball.aden.model.query.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Analyzes Query Store data and prepares qualified metrics for AI decisioning.
 * Does NOT make recommendations - that's the AI's job.
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
    
    public QueryStoreAnalyzer() {
    }
    
    /**
     * Analyzes queries and returns qualified metrics.
     */
    public QueryStoreAnalysis analyze(List<QueryStoreQuery> queries, String databaseName) {
        QueryStoreAnalysis analysis = new QueryStoreAnalysis();
        
        // Metadata
        analysis.setDatabase(databaseName);
        analysis.setAnalysisType("QUERY_STORE_PRODUCTION_METRICS");
        analysis.setTimestamp(new Date());
        analysis.setTotalQueriesAnalyzed(queries.size());
        
        // Analyze queries
        List<AnalyzedQuery> analyzedQueries = queries.stream()
            .map(this::analyzeQuery)
            .collect(Collectors.toList());
        analysis.setQueries(analyzedQueries);
        
        // Create qualified metrics for AI
        QualifiedMetrics qualifiedMetrics = createQualifiedMetrics(analyzedQueries);
        analysis.setQualifiedMetrics(qualifiedMetrics);
        
        return analysis;
    }
    
    /**
     * Analyzes individual query to extract AI-relevant qualifications.
     */
    private AnalyzedQuery analyzeQuery(QueryStoreQuery query) {
        AnalyzedQuery analyzed = new AnalyzedQuery();
        
        // Basic metrics
        analyzed.setQueryId(Long.parseLong(query.getQueryId()));
        analyzed.setExecutionCount(query.getExecutionCount());
        analyzed.setAvgDurationMs(query.getAvgDurationMs());
        analyzed.setAvgCpuTimeMs(query.getAvgCpuTimeMs());
        analyzed.setAvgLogicalReads(query.getAvgLogicalReads());
        
        // Extract operation type
        String operationType = extractOperationType(query.getSqlText());
        analyzed.setOperationType(operationType);
        
        // Extract tables accessed
        List<String> tablesAccessed = extractTableNames(query.getSqlText());
        analyzed.setTablesAccessed(tablesAccessed);
        analyzed.setTableCount(tablesAccessed.size());
        
        // Determine access pattern
        String accessPattern = determineAccessPattern(query.getSqlText());
        analyzed.setAccessPattern(accessPattern);
        
        // Check for joins
        boolean hasJoins = query.getSqlText().toUpperCase().contains("JOIN");
        analyzed.setHasJoins(hasJoins);
        
        // SQL preview
        String sqlPreview = query.getSqlText().length() > 200 
            ? query.getSqlText().substring(0, 200) + "..."
            : query.getSqlText();
        analyzed.setSqlPreview(sqlPreview.replaceAll("\\s+", " "));
        
        return analyzed;
    }
    
    /**
     * Creates qualified metrics for AI decisioning.
     */
    private QualifiedMetrics createQualifiedMetrics(List<AnalyzedQuery> analyzedQueries) {
        QualifiedMetrics metrics = new QualifiedMetrics();
        
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
        TableAccessPatterns tablePatterns = analyzeTablePatterns(analyzedQueries, totalExecutions);
        metrics.setTableAccessPatterns(tablePatterns);
        
        // Performance characteristics
        PerformanceCharacteristics performanceMetrics = analyzePerformance(analyzedQueries);
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
    private TableAccessPatterns analyzeTablePatterns(List<AnalyzedQuery> queries, long totalExecutions) {
        TableAccessPatterns patterns = new TableAccessPatterns();
        
        // Find tables that are always accessed together
        Map<Set<String>, Long> tableSetFrequency = new HashMap<>();
        
        for (AnalyzedQuery query : queries) {
            List<String> tables = query.getTablesAccessed();
            if (tables.size() > 1) {
                Set<String> tableSet = new HashSet<>(tables);
                long executions = query.getExecutionCount();
                tableSetFrequency.merge(tableSet, executions, Long::sum);
            }
        }
        
        // Find high-frequency table combinations
        List<TableCombination> frequentCombinations = tableSetFrequency.entrySet().stream()
            .filter(entry -> entry.getValue() > 50) // Lower threshold for test data
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
        
        patterns.setFrequentTableCombinations(frequentCombinations);
        patterns.setHasStrongCoAccessPatterns(!frequentCombinations.isEmpty());
        
        return patterns;
    }
    
    /**
     * Analyzes performance characteristics.
     */
    private PerformanceCharacteristics analyzePerformance(List<AnalyzedQuery> queries) {
        PerformanceCharacteristics perf = new PerformanceCharacteristics();
        
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
        
        perf.setAvgQueryDurationMs(Math.round(avgDuration * 100.0) / 100.0);
        perf.setAvgCpuTimeMs(Math.round(avgCpu * 100.0) / 100.0);
        perf.setAvgLogicalReads(Math.round(avgReads));
        
        // Identify slow queries (> 100ms)
        long slowQueryCount = queries.stream()
            .filter(q -> q.getAvgDurationMs() > 100)
            .count();
        perf.setSlowQueryCount(slowQueryCount);
        perf.setHasPerformanceIssues(slowQueryCount > 0);
        
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
            QueryStoreFileConnector connector = new QueryStoreFileConnector(exportFilePath);
            QueryStoreAnalyzer analyzer = new QueryStoreAnalyzer();
            
            System.out.println("=== Query Store Analysis (File-Based) ===");
            System.out.println("Export file: " + exportFilePath);
            
            // Load and verify export file
            connector.loadData();
            if (!connector.isQueryStoreEnabled()) {
                System.out.println("WARNING: Query Store was not enabled when data was exported");
            }
            
            // Extract queries from file
            List<QueryStoreQuery> queries = connector.getAllQueries();
            System.out.printf("Loaded %d queries from export file%n", queries.size());
            
            // Get metadata
            QueryStoreFileConnector.ExportMetadata metadata = connector.getExportMetadata();
            System.out.println("Database: " + metadata.getDatabaseName());
            System.out.println("Export timestamp: " + metadata.getExportTimestamp());
            
            // Analyze
            QueryStoreAnalysis analysis = analyzer.analyze(queries, metadata.getDatabaseName());
            
            System.out.printf("✓ Analysis completed with %d queries%n", analysis.getTotalQueriesAnalyzed());
            
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}