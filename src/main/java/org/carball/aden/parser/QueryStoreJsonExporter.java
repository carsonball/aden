package org.carball.aden.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.carball.aden.model.query.QueryStoreQuery;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports Query Store data to JSON format for log analysis.
 */
public class QueryStoreJsonExporter {
    
    private final ObjectMapper objectMapper;
    
    public QueryStoreJsonExporter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }
    
    /**
     * Exports Query Store queries to JSON file.
     */
    public void exportToJson(List<QueryStoreQuery> queries, String outputPath, String databaseName) throws IOException {
        Map<String, Object> exportData = new HashMap<>();
        
        // Metadata
        exportData.put("exportTimestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        exportData.put("database", databaseName);
        exportData.put("totalQueries", queries.size());
        exportData.put("exportType", "QUERY_STORE_USAGE_METRICS");
        
        // Query data
        exportData.put("queries", queries);
        
        // Summary statistics
        Map<String, Object> summary = createSummary(queries);
        exportData.put("summary", summary);
        
        // Write to file
        objectMapper.writeValue(new File(outputPath), exportData);
    }
    
    /**
     * Creates summary statistics from query data.
     */
    private Map<String, Object> createSummary(List<QueryStoreQuery> queries) {
        Map<String, Object> summary = new HashMap<>();
        
        if (queries.isEmpty()) {
            summary.put("totalExecutions", 0);
            summary.put("avgDurationMs", 0.0);
            summary.put("joinQueries", 0);
            summary.put("singleTableQueries", 0);
            return summary;
        }
        
        long totalExecutions = queries.stream()
            .mapToLong(QueryStoreQuery::getExecutionCount)
            .sum();
        
        double avgDuration = queries.stream()
            .mapToDouble(QueryStoreQuery::getAvgDurationMs)
            .average()
            .orElse(0.0);
        
        long joinQueries = queries.stream()
            .filter(q -> q.getJoinPattern() != null && !q.getJoinPattern().equals("SINGLE_TABLE"))
            .count();
        
        long singleTableQueries = queries.stream()
            .filter(q -> "SINGLE_TABLE".equals(q.getJoinPattern()))
            .count();
        
        // Analyze Customer+Profile patterns specifically
        long customerProfileQueries = queries.stream()
            .filter(q -> q.getTablesAccessed() != null)
            .filter(q -> q.getTablesAccessed().contains("Customers") && 
                        q.getTablesAccessed().contains("CustomerProfiles"))
            .count();
        
        long customerProfileExecutions = queries.stream()
            .filter(q -> q.getTablesAccessed() != null)
            .filter(q -> q.getTablesAccessed().contains("Customers") && 
                        q.getTablesAccessed().contains("CustomerProfiles"))
            .mapToLong(QueryStoreQuery::getExecutionCount)
            .sum();
        
        summary.put("totalExecutions", totalExecutions);
        summary.put("avgDurationMs", Math.round(avgDuration * 100.0) / 100.0);
        summary.put("joinQueries", joinQueries);
        summary.put("singleTableQueries", singleTableQueries);
        summary.put("customerProfileQueries", customerProfileQueries);
        summary.put("customerProfileExecutions", customerProfileExecutions);
        
        // Migration insights
        Map<String, Object> migrationInsights = new HashMap<>();
        if (customerProfileQueries > 0) {
            migrationInsights.put("dynamoDbCandidates", Map.of(
                "Customer+Profile", Map.of(
                    "priority", "HIGH",
                    "reason", "Always accessed together with high frequency",
                    "queryCount", customerProfileQueries,
                    "totalExecutions", customerProfileExecutions,
                    "recommendation", "Strong DynamoDB candidate - denormalize into single item"
                )
            ));
        }
        summary.put("migrationInsights", migrationInsights);
        
        return summary;
    }
    
    /**
     * Main method for standalone JSON export testing.
     */
    public static void main(String[] args) {
        try {
            String connectionString = QueryStoreConnector.createTestAppConnectionString();
            QueryStoreConnector connector = new QueryStoreConnector(connectionString);
            QueryStoreJsonExporter exporter = new QueryStoreJsonExporter();
            
            System.out.println("=== Query Store JSON Export ===");
            
            // Verify Query Store is available
            if (!connector.isQueryStoreEnabled()) {
                System.out.println("ERROR: Query Store is not enabled");
                return;
            }
            
            // Extract queries
            List<QueryStoreQuery> queries = connector.getAllQueries();
            System.out.printf("Extracted %d queries from Query Store%n", queries.size());
            
            // Export to JSON
            String outputPath = "query_store_export.json";
            exporter.exportToJson(queries, outputPath, "TestEcommerceApp");
            
            System.out.printf("✓ Exported to: %s%n", outputPath);
            System.out.println("Sample JSON log file created for analysis!");
            
            // Print first query as preview
            if (!queries.isEmpty()) {
                QueryStoreQuery firstQuery = queries.get(0);
                System.out.printf("\nTop query preview:%n");
                System.out.printf("  Executions: %,d%n", firstQuery.getExecutionCount());
                System.out.printf("  Tables: %s%n", firstQuery.getTablesAccessed());
                System.out.printf("  Join: %s%n", firstQuery.getJoinPattern());
            }
            
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}