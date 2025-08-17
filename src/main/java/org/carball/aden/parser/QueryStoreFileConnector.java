package org.carball.aden.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.carball.aden.model.query.QueryStoreQuery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads Query Store metrics from exported JSON files instead of connecting directly to the database.
 */
public class QueryStoreFileConnector {

    private final JsonNode exportData;
    
    public QueryStoreFileConnector(String filePath) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IOException("Query Store export file not found: " + filePath);
        }

        String content = Files.readString(path);
        exportData = objectMapper.readTree(content);

        validateExportFormat();
    }
    
    /**
     * Extracts all queries from the exported Query Store data.
     */
    public List<QueryStoreQuery> getAllQueries()
{
        List<QueryStoreQuery> results = new ArrayList<>();
        JsonNode queries = exportData.get("queries");
        
        if (queries != null && queries.isArray()) {
            for (JsonNode queryNode : queries) {
                QueryStoreQuery query = parseQueryFromJson(queryNode);
                results.add(query);
            }
        }
        
        return results;
    }
    
    /**
     * Tests if Query Store was enabled when the data was exported.
     */
    public boolean isQueryStoreEnabled()
{
        JsonNode metadata = exportData.get("export_metadata");
        if (metadata == null) {
            return false;
        }
        
        JsonNode queryStoreEnabled = metadata.get("query_store_enabled");
        return queryStoreEnabled != null && queryStoreEnabled.asBoolean();
    }
    
    /**
     * Gets total number of queries from the exported data.
     */
    public int getQueryStoreQueryCount()
{
        JsonNode metadata = exportData.get("export_metadata");
        if (metadata != null) {
            JsonNode totalQueries = metadata.get("total_queries");
            if (totalQueries != null) {
                return totalQueries.asInt();
            }
        }
        
        // Fallback: count queries in the data
        JsonNode queries = exportData.get("queries");
        return queries != null && queries.isArray() ? queries.size() : 0;
    }
    
    /**
     * Gets export metadata including database name, export timestamp, and SQL Server version.
     */
    public ExportMetadata getExportMetadata() throws IOException {
        JsonNode metadata = exportData.get("export_metadata");
        if (metadata == null) {
            throw new IOException("Export metadata not found in file");
        }
        
        return new ExportMetadata(
            metadata.get("database_name").asText(),
            metadata.get("export_timestamp").asText(),
            metadata.get("sql_server_version").asText(),
            metadata.get("query_store_enabled").asBoolean(),
            metadata.get("total_queries").asInt()
        );
    }
    
    private void validateExportFormat() {
        if (exportData == null) {
            throw new IllegalStateException("Invalid JSON format in export file");
        }
        
        JsonNode metadata = exportData.get("export_metadata");
        if (metadata == null) {
            throw new IllegalStateException("Missing export_metadata section in export file");
        }
        
        JsonNode queries = exportData.get("queries");
        if (queries == null || !queries.isArray()) {
            throw new IllegalStateException("Missing or invalid queries section in export file");
        }
        
        // Validate required metadata fields
        String[] requiredFields = {"database_name", "export_timestamp", "query_store_enabled"};
        for (String field : requiredFields) {
            if (!metadata.has(field)) {
                throw new IllegalStateException("Missing required metadata field: " + field);
            }
        }
    }
    
    private QueryStoreQuery parseQueryFromJson(JsonNode queryNode) {
        String queryId = queryNode.get("query_id").asText();
        String sqlText = queryNode.get("sql_text").asText();
        long executionCount = queryNode.get("execution_count").asLong();
        double avgDurationMs = queryNode.get("avg_duration_ms").asDouble();
        double avgCpuMs = queryNode.get("avg_cpu_ms").asDouble();
        double avgLogicalReads = queryNode.get("avg_logical_reads").asDouble();
        
        return new QueryStoreQuery(queryId, sqlText, executionCount, avgDurationMs, avgCpuMs, avgLogicalReads);
    }

        /**
         * Metadata about the Query Store export.
         */
        public record ExportMetadata(String databaseName, String exportTimestamp, String sqlServerVersion,
                                     boolean queryStoreEnabled, int totalQueries) {

            @Override
            public String toString() {
                return String.format("ExportMetadata{database='%s', timestamp='%s', version='%s', enabled=%s, queries=%d}",
                        databaseName, exportTimestamp, sqlServerVersion, queryStoreEnabled, totalQueries);
            }
        }
    
    /**
     * Main method for standalone testing of QueryStoreFileConnector.
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java QueryStoreFileConnector <path-to-export-file>");
            System.exit(1);
        }
        
        try {
            String filePath = args[0];
            System.out.println("Loading Query Store export from: " + filePath);
            QueryStoreFileConnector connector = new QueryStoreFileConnector(filePath);
            System.out.println("✓ Export file loaded and validated");
            
            System.out.println("=== Query Store File Connector Test ===");
            
            // Get export metadata
            ExportMetadata metadata = connector.getExportMetadata();
            System.out.println("✓ Export metadata: " + metadata);
            
            // Check if Query Store was enabled when data was exported
            if (!connector.isQueryStoreEnabled()) {
                System.out.println("WARNING: Query Store was not enabled when data was exported");
            } else {
                System.out.println("✓ Query Store was enabled during export");
            }
            
            // Get query count
            int queryCount = connector.getQueryStoreQueryCount();
            System.out.println("✓ Total queries in export: " + queryCount);
            
            if (queryCount == 0) {
                System.out.println("WARNING: No queries found in export file");
                return;
            }
            
            // Get all queries from export file
            System.out.println("\n=== All Queries from Export ===");
            List<QueryStoreQuery> allQueries = connector.getAllQueries();
            
            System.out.printf("Total queries loaded: %d\n", allQueries.size());
            
            // Show first 5 for testing
            for (int i = 0; i < Math.min(5, allQueries.size()); i++) {
                QueryStoreQuery query = allQueries.get(i);
                System.out.printf("\n#%d - Query ID: %s%n", i + 1, query.queryId());
                System.out.printf("  Executions: %,d%n", query.executionCount());
                System.out.printf("  Avg Duration: %.2f ms%n", query.avgDurationMs());
                
                String sqlPreview = query.sqlText().length() > 150
                    ? query.sqlText().substring(0, 150) + "..."
                    : query.sqlText();
                System.out.printf("  SQL: %s%n", sqlPreview.replaceAll("\\s+", " "));
            }
            
            System.out.println("\n✓ Query Store export data successfully processed");
            
        } catch (Exception e) {
            System.err.println("ERROR: " + e);
        }
    }
}